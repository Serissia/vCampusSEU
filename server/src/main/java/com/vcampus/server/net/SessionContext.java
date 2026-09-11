package com.vcampus.server.net;

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
 * @author GGbongy
 */
public class SessionContext {

    /**
     * 已登录用户的一卡通号；未登录或已登出时为 null。
     * 由连接处理线程写入、可能被其它线程读取，故声明为 volatile。
     */
    private volatile String uid;

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
     * 认证成功后写入会话身份。
     *
     * @param uid 用户一卡通号
     */
    public void authenticate(String uid) {
        this.uid = uid;
    }

    /**
     * 清空会话身份（登出或连接关闭时调用）。
     */
    public void clear() {
        this.uid = null;
    }
}
