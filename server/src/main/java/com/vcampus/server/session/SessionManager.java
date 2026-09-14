package com.vcampus.server.session;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 服务端登录令牌（token）会话表。
 *
 * <p>与 {@code SessionContext} 的分工：{@code SessionContext} 把身份绑定在<b>一条连接</b>上，
 * 本类则让身份可以<b>脱离连接</b>存在——登录成功后签发一个随机令牌，客户端回传令牌即可在任意新连接上
 * 自证身份。两者配合的关系是：连接上已有会话就优先用会话，没有会话才用令牌，用令牌认证成功后会顺手
 * 把该连接绑定成会话，之后的请求便不再需要令牌。</p>
 *
 * <p>令牌随机、不落库、服务端重启即全部失效；采用滑动过期——每次被使用都会刷新活跃时间，
 * 因此只要用户还在操作就不会掉线，长时间静默后令牌才会被清理。</p>
 *
 * @author GGbongy
 */
public class SessionManager {

    /** 令牌有效期（滑动）：30 分钟无任何请求即失效 */
    private static final long TOKEN_TTL_MS = 30L * 60L * 1000L;

    /** 过期令牌清理周期（分钟） */
    private static final long SWEEP_INTERVAL_MINUTES = 5L;

    /** 令牌随机字节数，Base64 后为 43 个字符 */
    private static final int TOKEN_BYTES = 32;

    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    /**
     * 在线账号表：一卡通号 → 当前绑定它的活连接数，计数归零即视为已登出。
     *
     * <p>与 {@link #sessions 令牌表}的分工：令牌可以<b>脱离连接</b>活到过期为止，
     * 所以「令牌还在」并不等于「人还在线」——关闭窗口、强杀进程都不会走登出按钮，
     * 令牌会滞留到自然过期。本表只回答「此刻还有没有活着的连接在用这个账号」，
     * 登录判重以它为准：连接一断（含空闲超时回收）名额立刻归还，账号马上就能重新登录。</p>
     */
    private final Map<String, Integer> onlineConnections = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * 过期清理线程。声明为守护线程，使其不阻碍服务端进程退出。
     */
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SessionManager-Sweeper");
        thread.setDaemon(true);
        return thread;
    });

    public SessionManager() {
        sweeper.scheduleWithFixedDelay(this::sweepExpired,
                SWEEP_INTERVAL_MINUTES, SWEEP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 登录成功后签发令牌。
     *
     * @param uid 用户一卡通号
     * @return 新令牌
     */
    public String createSession(String uid) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = encoder.encodeToString(bytes);
        sessions.put(token, new SessionRecord(uid));
        return token;
    }

    /**
     * 校验令牌并解析其归属用户，同时刷新活跃时间（滑动过期）。
     *
     * @param token 客户端回传的令牌
     * @return 令牌有效时返回一卡通号；无效或已过期返回 null
     */
    public String resolveUid(String token) {
        SessionRecord record = recordOf(token);
        if (record == null) {
            return null;
        }
        record.touch();
        return record.uid;
    }

    /**
     * 刷新令牌活跃时间，但仅当该令牌本就属于指定用户时。
     *
     * <p>用于连接上已有会话、请求又携带了令牌的场景：此时不去用令牌改判身份，
     * 只是顺带续期，避免「A 用户会话里携带 B 用户令牌」把 B 的令牌续命。</p>
     *
     * @param token 请求携带的令牌
     * @param uid   当前连接会话的用户，可为 null
     */
    public void touch(String token, String uid) {
        if (uid == null) {
            return;
        }
        SessionRecord record = recordOf(token);
        if (record != null && uid.equals(record.uid)) {
            record.touch();
        }
    }

    /**
     * 注销令牌（登出时调用）。
     *
     * @param token 待注销的令牌
     */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /**
     * 当前有效令牌数量，仅供观测与调试。
     */
    public int activeCount() {
        return sessions.size();
    }

    /**
     * 登录专用：一步完成「判定 + 占用」，账号已被在线连接持有时返回 false。
     *
     * <p>判定与占用必须原子：拆成「先查在线表、再绑身份」两步的话，同一账号在两个连接上
     * 同时点登录可能双双通过。占用成功后由调用方（{@code SessionContext}）负责在登出或
     * 断线时调用 {@link #leaveOnline(String)} 归还，两者严格配对。</p>
     *
     * @param uid 待登录的一卡通号
     * @return true 表示占用成功可以登录；false 表示该账号已在别处登录
     */
    public synchronized boolean occupyForLogin(String uid) {
        if (uid == null || onlineConnections.containsKey(uid)) {
            return false;
        }
        onlineConnections.put(uid, 1);
        return true;
    }

    /**
     * 把一条连接计入该账号的在线连接数（令牌认证成功时调用）。
     *
     * <p>这里刻意不做判重：凭令牌进来的通常是本人客户端的断线重连，此时旧连接可能还没被
     * 服务端回收，若一并拒绝反而会把本人挡在门外。单点登录的判重只作用于 LOGIN 入口。</p>
     *
     * @param uid 一卡通号
     */
    public void joinOnline(String uid) {
        if (uid != null) {
            onlineConnections.merge(uid, 1, Integer::sum);
        }
    }

    /**
     * 归还该账号的一个在线名额（登出或连接断开时调用），计数归零即移除。
     *
     * @param uid 一卡通号
     */
    public void leaveOnline(String uid) {
        if (uid != null) {
            onlineConnections.computeIfPresent(uid, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    private SessionRecord recordOf(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        SessionRecord record = sessions.get(token);
        if (record == null) {
            return null;
        }
        if (record.isExpired()) {
            sessions.remove(token, record);
            return null;
        }
        return record;
    }

    private void sweepExpired() {
        sessions.values().removeIf(SessionRecord::isExpired);
    }

    /**
     * 单个令牌的会话记录。
     */
    private static final class SessionRecord {

        private final String uid;
        private volatile long lastActiveAt;

        private SessionRecord(String uid) {
            this.uid = uid;
            this.lastActiveAt = System.currentTimeMillis();
        }

        private void touch() {
            this.lastActiveAt = System.currentTimeMillis();
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - lastActiveAt > TOKEN_TTL_MS;
        }
    }
}
