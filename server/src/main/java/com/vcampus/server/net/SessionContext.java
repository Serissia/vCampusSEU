package com.vcampus.server.net;

import com.vcampus.common.vo.UserVO;

/**
 * 连接级会话上下文：记录「这条 TCP 连接登录成了谁」。
 *
 * <p>改造背景：在此之前服务端完全无状态，每个请求都靠报文里客户端自填的 {@code Message.uid} 来认定身份，
 * 任何人伪造一个 uid 即可越权。现在身份的唯一来源是本类的实例——它随连接创建、随连接销毁，
 * 只有在本连接上成功执行过 LOGIN 之后才会被写入。</p>
 *
 * <p>生命周期：一条连接 ↔ 一个 {@code SessionContext} ↔ 一个 {@code Dispatcher}，
 * 三者由 {@link ClientHandler} 在构造时一并创建，从结构上保证不会被拆开或共享。</p>
 *
 * @author GGbongy
 */
public class SessionContext {

    /**
     * 已登录用户；未登录或已登出时为 null。
     * 由连接处理线程写入、可能被其它线程读取，故声明为 volatile。
     */
    private volatile UserVO user;

    /**
     * 判断当前连接是否已完成登录。
     *
     * @return true 表示已认证
     */
    public boolean isAuthenticated() {
        return user != null;
    }

    /**
     * 获取已登录用户（含角色、状态等），未登录时为 null。
     */
    public UserVO getUser() {
        return user;
    }

    /**
     * 获取已登录用户的一卡通号，未登录时为 null。
     */
    public String getUid() {
        UserVO current = user;
        return current == null ? null : current.getUid();
    }

    /**
     * 登录成功后写入会话身份。
     *
     * @param user 登录用户
     */
    public void authenticate(UserVO user) {
        this.user = user;
    }

    /**
     * 清空会话身份（登出或连接关闭时调用）。
     */
    public void clear() {
        this.user = null;
    }
}
