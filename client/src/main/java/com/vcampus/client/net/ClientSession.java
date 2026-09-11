package com.vcampus.client.net;

import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.vo.UserVO;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    /** 当前共享连接；为 null 表示尚未建立（首次请求时按配置懒创建） */
    private SocketClient client;

    /** 已登录用户 */
    private UserVO currentUser;

    /** 服务端签发的登录令牌，随每个请求发出，使连接失效重连后仍能自证身份 */
    private String token;

    private ClientSession() {
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
