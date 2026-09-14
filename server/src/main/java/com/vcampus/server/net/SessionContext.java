package com.vcampus.server.net;

import com.vcampus.server.session.SessionManager;

/**
 * 连接级会话上下文：记录「这条 TCP 连接登录成了谁」。
 *
 * <p>改造背景：在此之前服务端完全无状态，每个请求都靠报文里客户端自填的 {@code Message.uid} 来认定身份，
 * 任何人伪造一个 uid 即可越权。现在身份的唯一来源是本类——它随连接创建、随连接销毁，
 * 只有在本连接上成功执行过 LOGIN（或凭有效令牌认证）之后才会被写入。</p>
 *
 * <p>只保存一卡通号而不保存整个用户对象：角色与账号状态在每个请求入口都重新查库复核，
 * 这样管理员改角色、冻结或删除账号都能立即生效，不需要额外考虑会话里的缓存何时失效。</p>
 *
 * <p>生命周期：一条连接 ↔ 一个 {@code SessionContext} ↔ 一个 {@code Dispatcher}，
 * 三者由 {@link ClientHandler} 在构造时一并创建，从结构上保证不会被拆开或共享。</p>
 *
 * <p>本类还负责在线状态的登记与归还：身份写入时把本连接计入服务端的在线账号表，身份清空时归还。
 * 两个动作随 {@link #authenticate} / {@link #clear} 一起发生，调用方不需要另外记得维护在线表，
 * 也就不存在「漏归还导致账号永远显示在线」的可能。</p>
 *
 * @author GGbongy
 */
public class SessionContext {

    /** 服务端共享的令牌会话表，同时作为在线账号表的归属地 */
    private final SessionManager sessionManager;

    /**
     * 已登录用户的一卡通号；未登录或已登出时为 null。
     * 由连接处理线程写入、可能被其它线程读取，故声明为 volatile。
     */
    private volatile String uid;

    /**
     * @param sessionManager 服务端共享的会话表，用于登记在线状态与凭令牌认证
     */
    public SessionContext(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 判断当前连接是否已完成认证。
     *
     * @return true 表示已认证
     */
    public boolean isAuthenticated() {
        return uid != null;
    }

    /**
     * 获取已登录用户的一卡通号，未登录时为 null。
     */
    public String getUid() {
        return uid;
    }

    /**
     * 无条件绑定身份（凭令牌认证、或同一连接换账号时调用），<b>不做登录判重</b>。
     *
     * <p>重复绑定同一身份是空操作，因此调用方不必关心是否已经认证过。</p>
     *
     * @param uid 用户一卡通号
     */
    public synchronized void authenticate(String uid) {
        if (uid == null || uid.equals(this.uid)) {
            return;
        }
        String previous = this.uid;
        this.uid = uid;
        if (previous != null) {
            sessionManager.leaveOnline(previous);
        }
        sessionManager.joinOnline(uid);
    }

    /**
     * 登录入口专用：绑定身份，但账号已在别处登录时拒绝。
     *
     * <p>与 {@link #authenticate(String)} 的区别在「排他」：只允许一条从 LOGIN 进来的连接持有一个账号。
     * 判定与占用在 {@link SessionManager#occupyForLogin(String)} 内一步完成，避免两个连接同时登录
     * 双双通过。凭令牌认证走的是上面那个不排他的方法，因为那属于本人客户端的断线重连。</p>
     *
     * @param uid 登录用户的一卡通号
     * @return true 表示绑定成功；false 表示该账号已在线，本次登录应被拒绝
     */
    public synchronized boolean tryAuthenticateForLogin(String uid) {
        if (uid == null) {
            return false;
        }
        if (uid.equals(this.uid)) {
            return true;
        }
        if (!sessionManager.occupyForLogin(uid)) {
            return false;
        }
        String previous = this.uid;
        this.uid = uid;
        if (previous != null) {
            sessionManager.leaveOnline(previous);
        }
        return true;
    }

    /**
     * 清空会话身份（登出或连接关闭时调用），并归还本连接占用的在线名额。
     */
    public synchronized void clear() {
        String previous = this.uid;
        if (previous == null) {
            return;
        }
        this.uid = null;
        sessionManager.leaveOnline(previous);
    }
}
