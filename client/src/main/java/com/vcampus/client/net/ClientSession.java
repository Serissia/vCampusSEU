package com.vcampus.client.net;

import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.vo.UserVO;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 客户端会话与全局唯一网络连接。
 *
 * <p>改造背景：服务端现在把身份绑定在 TCP 连接上（登录成功后该连接即代表某个用户），
 * 因此客户端不能再像以前那样「每个控制器各开一条连接、每条请求都自己填 uid」——
 * 那样除了登录那条连接以外，所有连接在服务端看来都是未登录的。全客户端统一使用本类持有的
 * <b>唯一一条长连接</b>：登录、业务请求、登出全部走它。</p>
 *
 * <p>代价与取舍：{@link SocketClient#send} 全程 synchronized，因此单连接意味着<b>请求串行化</b>——
 * 电子资源上传/下载、PDF 单页渲染这类慢请求执行期间，其它请求会排队等待。
 * 登录令牌已经具备，需要时可为慢请求单独开一条带令牌的连接（它会凭令牌自证身份）来消除这个瓶颈。</p>
 *
 * <p>使用方式：控制器照旧保留 {@code private final SocketClient socketClient = ClientSession.client();}
 * 字段，调用点无需改动。</p>
 *
 * @author GGbongy
 */
public final class ClientSession {

    private static final ClientSession INSTANCE = new ClientSession();

    /** 登出时的收尾线程池：只用于「通知服务端 + 关闭旧连接」，避免阻塞 UI 线程 */
    private static final ExecutorService CLEANUP_POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ClientSession-Cleanup");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 心跳间隔（秒）。服务端连接空闲超时为 180 秒，这里取 60 秒留三次余量。
     */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 60L;

    /** 进程退出时补发登出的最长等待时间（毫秒），服务端不可达时不至于拖住退出 */
    private static final long SHUTDOWN_LOGOUT_WAIT_MS = 1500L;

    /** 心跳线程池，仅在登录期间存在 */
    private ScheduledExecutorService heartbeat;

    /** 当前共享连接；为 null 表示尚未建立（首次请求时按配置懒创建） */
    private SocketClient client;

    /** 已登录用户 */
    private UserVO currentUser;

    /** 服务端签发的登录令牌，随每个请求发出，使连接失效重连后仍能自证身份 */
    private String token;

    private ClientSession() {
        registerShutdownLogout();
    }

    public static ClientSession getInstance() {
        return INSTANCE;
    }

    /**
     * 取得全局共享连接，供控制器字段初始化使用。
     *
     * @return 共享的 {@link SocketClient} 实例
     */
    public static SocketClient client() {
        return INSTANCE.getClient();
    }

    /**
     * 取得全局共享连接；为 null 时按当前 AppConfig 中的地址端口创建（连接本身仍是懒建立）。
     *
     * <p>重建的连接会立刻装上当前令牌，因此换连接后第一个请求即可恢复登录态。</p>
     */
    public SocketClient getClient() {
        synchronized (this) {
            if (client == null) {
                client = new SocketClient();
                client.setSessionToken(token);
            }
            return client;
        }
    }

    /**
     * 登录成功后登记本次会话。
     *
     * @param user  登录用户
     * @param token 服务端签发的登录令牌，可为 null（旧服务端或异常响应）
     */
    public void begin(UserVO user, String token) {
        SocketClient current;
        synchronized (this) {
            this.currentUser = user;
            this.token = token;
            current = client;
        }
        if (current != null) {
            current.setSessionToken(token);
        }
        startHeartbeat();
    }

    /**
     * 启动心跳：周期性在本连接上发一次 HEARTBEAT。
     *
     * <p>两个作用：一是让服务端的连接空闲超时看到流量，从而把「应用开着但用户没操作」
     * 与「客户端已经死了」区分开；二是服务端借此为令牌滑动续期，避免挂着不动时令牌悄悄过期。</p>
     */
    private void startHeartbeat() {
        stopHeartbeat();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ClientSession-Heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        synchronized (this) {
            heartbeat = scheduler;
        }
        // fixedDelay 而非 fixedRate：上一个心跳卡在慢请求后面时，不会堆积出连续多次心跳
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 停止心跳。
     */
    private void stopHeartbeat() {
        ScheduledExecutorService scheduler;
        synchronized (this) {
            scheduler = heartbeat;
            heartbeat = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void sendHeartbeat() {
        SocketClient current;
        synchronized (this) {
            current = client;
        }
        if (current == null) {
            return;
        }
        try {
            current.send(new Message(null, MessageType.HEARTBEAT, null, "ping"));
        } catch (Exception e) {
            // 心跳失败说明连接已不可用：关掉它，下一次真实请求会自动重连并凭令牌恢复身份
            System.err.println("[ClientSession] 心跳失败，连接将被重建：" + e.getMessage());
            current.close();
        }
    }

    /**
     * 取得当前登录用户，未登录时为 null。
     */
    public UserVO getCurrentUser() {
        return currentUser;
    }

    /**
     * 结束会话：尽力通知服务端注销本连接的身份与令牌，随后关闭并丢弃连接。
     *
     * <p>会话状态在方法返回前即已清空，注销通知与关闭连接则交给后台线程执行 ——
     * 登出是 UI 线程上的操作，不能因为服务端不可达而卡住界面。</p>
     *
     * <p>下次登录会得到一条全新连接——服务端那边的旧会话随旧连接一起消失。</p>
     */
    public void end() {
        SocketClient toClose;
        String uid;
        stopHeartbeat();
        synchronized (this) {
            toClose = client;
            uid = currentUser == null ? null : currentUser.getAccountNumber();
            currentUser = null;
            token = null;
            client = null;
        }

        if (toClose == null) {
            return;
        }
        CLEANUP_POOL.execute(() -> {
            // 登出请求要带上令牌：服务端据此把令牌一并作废，否则它在有效期内仍可被冒用，
            // 令牌在请求发出后被清掉，避免残留在已废弃的连接上
            try {
                toClose.send(new Message(uid, MessageType.LOGOUT, null, null));
            } catch (IOException | ClassNotFoundException ignored) {
                // 服务端不可达或已断开，直接关闭即可
            } finally {
                toClose.setSessionToken(null);
                toClose.close();
            }
        });
    }

    /**
     * 注册 JVM 关停钩子：进程退出时尽力补发一次登出。
     *
     * <p>为什么要补：服务端的令牌独立于连接保存到过期为止，而关闭窗口、托盘退出都不会经过
     * 登出按钮，令牌会一直滞留到自然过期，这期间被别人拿到仍然管用。退出前打一声招呼就能让它立刻作废。</p>
     *
     * <p>这只是令牌卫生，不是重新登录的前提——登录判重看的是「还有没有活连接的账号在用这个身份」，
     * 钩子没跑成（强杀、断电）时账号也会随连接断开立刻下线。</p>
     */
    private void registerShutdownLogout() {
        Runtime.getRuntime().addShutdownHook(
                new Thread(this::sendLogoutOnExit, "ClientSession-ShutdownLogout"));
    }

    /**
     * 关停钩子的实际动作，见 {@link #registerShutdownLogout()}。
     */
    private void sendLogoutOnExit() {
        SocketClient current;
        String uid;
        synchronized (this) {
            current = client;
            uid = currentUser == null ? null : currentUser.getAccountNumber();
        }
        // 已经登出过（client 被置空）或压根没登录，无需通知
        if (current == null || uid == null) {
            return;
        }
        // 发送放到独立线程并限时等待：共享连接可能正卡在某个慢请求上，也可能服务端已经不可达，
        // 两种情况都不该让「关闭应用」跟着卡住
        Thread sender = new Thread(() -> {
            try {
                current.send(new Message(uid, MessageType.LOGOUT, null, null));
            } catch (IOException | ClassNotFoundException | RuntimeException ignored) {
                // 服务端不可达或已断开，连接会随进程退出一起消失
            }
        }, "ClientSession-ShutdownLogout-Send");
        sender.setDaemon(true);
        sender.start();
        try {
            sender.join(SHUTDOWN_LOGOUT_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 丢弃当前连接（例如偏好设置里修改了服务器地址或端口）。
     *
     * <p>与 {@link #end()} 的区别：不发登出通知、保留登录用户，仅让下次请求按新配置重建连接。</p>
     */
    public void reset() {
        SocketClient toClose;
        synchronized (this) {
            toClose = client;
            client = null;
        }
        if (toClose != null) {
            toClose.close();
        }
    }
}
