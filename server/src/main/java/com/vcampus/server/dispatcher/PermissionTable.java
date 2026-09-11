package com.vcampus.server.dispatcher;

import com.vcampus.common.message.MessageType;
import com.vcampus.common.vo.UserRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务端接口权限表：{@link MessageType} 到「允许访问的角色集合」的唯一权威映射。
 *
 * <p>设计要点（取代原先 {@code Dispatcher} 内 requiresPermissionCheck / hasPermission 两个手写 switch）：</p>
 * <ul>
 *   <li><b>默认拒绝</b>：未在本表登记的接口一律无权访问，不再出现「忘了登记就静默放行」。</li>
 *   <li><b>单一来源</b>：一个接口的「是否需要校验」与「谁能访问」只写在一处，不存在两份规则漂移。</li>
 *   <li><b>启动自检</b>：{@link #verifyComplete()} 会遍历全部 {@link MessageType}，
 *       只要有枚举既不属于公开接口、又没登记权限，服务端启动即失败。新增接口必须显式声明权限。</li>
 * </ul>
 *
 * <p>注意：本表只回答「这个角色能不能调这个接口」，不回答「调用者是不是这个角色」——
 * 后者由 {@code SessionContext} 负责（身份来自连接会话，而非报文自称的 uid）。</p>
 *
 * @author GGbongy
 */
public final class PermissionTable {

    /**
     * 无需登录即可访问的公开接口。
     * LOGIN 是登录入口本身；HEARTBEAT 供「测试网络连接」在未登录状态下探测服务端是否可达。
     */
    private static final Set<MessageType> PUBLIC_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MessageType.LOGIN, MessageType.HEARTBEAT));

    /**
     * 任意已登录角色均可访问（含 ADMIN / 教务老师 / 图书管理员 / 商店管理员 / 教职工 / 学生 / 商店卖家）。
     */
    private static final Set<UserRole> ANY_ROLE = Collections.unmodifiableSet(EnumSet.allOf(UserRole.class));

    /**
     * 接口权限表。未登记的接口视作无权访问。
     */
    private static final Map<MessageType, Set<UserRole>> PERMISSIONS = new EnumMap<>(MessageType.class);

    static {
        // ===================== 教务 =====================
        allowAny(MessageType.COURSE_QUERY,
                MessageType.COURSE_LIST_ALL,
                MessageType.COURSE_QUERY_BY_TEACHER,
                MessageType.COURSE_QUERY_BY_SEMESTER);

        // 课程维护：教务老师与任课教师均可
        grant(UserRole.ADMIN, UserRole.ACADEMIC_AFFAIRS_TEACHER, UserRole.TEACHER).on(
                MessageType.COURSE_ADD,
                MessageType.COURSE_UPDATE,
                MessageType.COURSE_DISABLE);

        // 删除课程、审批开课、排课：仅教务侧
        grant(UserRole.ADMIN, UserRole.ACADEMIC_AFFAIRS_TEACHER).on(
                MessageType.COURSE_DELETE,
                MessageType.COURSE_APPROVE,
                MessageType.COURSE_REJECT,
                MessageType.COURSE_PENDING_LIST,
                MessageType.COURSE_SCHEDULE,
                MessageType.COURSE_WEEK_SCHEDULE,
                MessageType.COURSE_LOCATION_SCHEDULE);

        // 选课 / 退课 / 课表：仅学生
        grant(UserRole.STUDENT).on(
                MessageType.COURSE_SELECT,
                MessageType.COURSE_DROP,
                MessageType.COURSE_TIMETABLE);

        grant(UserRole.ADMIN, UserRole.ACADEMIC_AFFAIRS_TEACHER, UserRole.TEACHER).on(
                MessageType.COURSE_STUDENT_LIST,
                MessageType.GRADE_SUBMIT);

        // 成绩查询：学生查自己的，教师/教务查所授课程的
        grant(UserRole.ADMIN, UserRole.ACADEMIC_AFFAIRS_TEACHER, UserRole.TEACHER, UserRole.STUDENT).on(
                MessageType.GRADE_QUERY,
                MessageType.COURSE_REVIEW_LIST);

        grant(UserRole.ADMIN, UserRole.ACADEMIC_AFFAIRS_TEACHER, UserRole.TEACHER).on(
                MessageType.GRADE_QUERY_BY_COURSE,
                MessageType.GRADE_STATISTICS);

        grant(UserRole.STUDENT).on(
                MessageType.COURSE_REVIEW_SUBMIT,
                MessageType.COURSE_REVIEW_DELETE);

        // ===================== 图书馆 / 电子资源 =====================
        allowAny(MessageType.BOOK_QUERY);

        grant(UserRole.ADMIN, UserRole.LIBRARIAN).on(
                MessageType.BOOK_ADD,
                MessageType.BOOK_UPDATE,
                MessageType.BOOK_DELETE,
                MessageType.BOOK_RESOURCE_DELETE,
                // 借书/还书为柜台业务，仅图书管理员
                MessageType.BOOK_BORROW,
                MessageType.BOOK_RETURN,
                MessageType.BORROW_BY_STUDENT);

        // 电子资源上传开放给学生/教师投稿，审核另计
        grant(UserRole.ADMIN, UserRole.LIBRARIAN, UserRole.STUDENT, UserRole.TEACHER).on(
                MessageType.BOOK_RESOURCE_UPLOAD,
                MessageType.BOOK_RESOURCE_DOWNLOAD,
                // 在线阅读：按需拉取单页渲染图
                MessageType.BOOK_RESOURCE_PAGE_COUNT,
                MessageType.BOOK_RESOURCE_RENDER_PAGE);

        grant(UserRole.STUDENT, UserRole.TEACHER).on(
                MessageType.BOOK_RENEW,
                MessageType.BORROW_MY_LIST,
                MessageType.EBK_SUBMIT,
                MessageType.EBK_MY_LIST);

        grant(UserRole.ADMIN, UserRole.LIBRARIAN).on(
                MessageType.EBK_PENDING_LIST,
                MessageType.EBK_REVIEW);

        // ===================== 用户管理 =====================
        // 账号增删改与重置密码：仅系统管理员
        grant(UserRole.ADMIN).on(
                MessageType.USER_REGISTER,
                MessageType.USER_LIST,
                MessageType.USER_UPDATE,
                MessageType.USER_DELETE,
                MessageType.USER_RESET_PASSWORD);

        grant(UserRole.ADMIN, UserRole.ACADEMIC_AFFAIRS_TEACHER, UserRole.TEACHER).on(
                MessageType.STUDENT_LIST);

        // 查询/修改个人信息、改密码、余额查询与充值：任意已登录用户，
        // 且只能作用于自己 —— 身份取自会话，报文里的 uid 不再被采信
        allowAny(MessageType.GET_USER_INFO,
                MessageType.UPDATE_USER_INFO,
                MessageType.CHANGE_PASSWORD,
                MessageType.PAYMENT_BALANCE,
                MessageType.PAYMENT_RECHARGE,
                MessageType.LOGOUT);

        // ===================== 商店 / 订单 =====================
        allowAny(MessageType.GOODS_QUERY, MessageType.GOODS_IMAGE_DOWNLOAD);

        // 商品维护：系统管理员与商店卖家
        grant(UserRole.ADMIN, UserRole.SELLER).on(
                MessageType.GOODS_ADD,
                MessageType.GOODS_UPDATE,
                MessageType.GOODS_DELETE,
                MessageType.GOODS_IMAGE_UPLOAD,
                MessageType.GOODS_IMAGE_DELETE);

        // 强制下架：仅管理员
        grant(UserRole.ADMIN).on(MessageType.GOODS_OFF_SHELF);

        allowAny(MessageType.CART_ADD,
                MessageType.CART_QUERY,
                MessageType.CART_UPDATE,
                MessageType.CART_REMOVE,
                MessageType.CART_CLEAR,
                MessageType.ORDER_CREATE,
                MessageType.ORDER_CHECKOUT,
                MessageType.ORDER_QUERY);

        // 全店订单与统计：管理员与卖家
        grant(UserRole.ADMIN, UserRole.SELLER).on(
                MessageType.ORDER_LIST_ALL,
                MessageType.ORDER_STATISTICS);

        // ===================== 二手市场 / 站内信 =====================
        allowAny(MessageType.SECOND_HAND_QUERY,
                MessageType.SECOND_HAND_PUBLISH,
                MessageType.SECOND_HAND_OFF_SHELF,
                MessageType.SECOND_HAND_BUY,
                MessageType.SECOND_HAND_MY_LIST,
                MessageType.CHAT_SEND,
                MessageType.CHAT_HISTORY,
                MessageType.CHAT_CONVERSATIONS);

        grant(UserRole.ADMIN).on(
                MessageType.SECOND_HAND_PENDING_LIST,
                MessageType.SECOND_HAND_REVIEW);

        // ===================== 通知 =====================
        // 公告查询与手动同步都是界面上的可见功能，任意已登录用户可触发
        allowAny(MessageType.NOTICE_QUERY,
                MessageType.NOTICE_GET_STATUS,
                MessageType.NOTICE_TRIGGER_SYNC);
    }

    private PermissionTable() {
    }

    /**
     * 判断接口是否属于无需登录的公开接口。
     *
     * @param type 业务动作
     * @return true 表示无需校验身份即可访问
     */
    public static boolean isPublic(MessageType type) {
        return type != null && PUBLIC_TYPES.contains(type);
    }

    /**
     * 判断角色是否有权访问指定接口。
     *
     * @param role 已认证用户的角色
     * @param type 业务动作
     * @return true 表示允许；角色为空或接口未登记均返回 false（默认拒绝）
     */
    public static boolean isAuthorized(UserRole role, MessageType type) {
        if (role == null || type == null) {
            return false;
        }
        Set<UserRole> allowed = PERMISSIONS.get(type);
        return allowed != null && allowed.contains(role);
    }

    /**
     * 启动期完整性校验：确保每一个 {@link MessageType} 都已被显式分类。
     *
     * <p>这是本表「默认拒绝」得以成立的前提——新增接口若忘记声明权限，服务端将在启动时直接失败，
     * 而不是变成一条静默的越权通道。</p>
     *
     * @throws IllegalStateException 存在未分类接口，或公开接口被重复登记进权限表
     */
    public static void verifyComplete() {
        List<MessageType> unclassified = new ArrayList<>();
        for (MessageType type : MessageType.values()) {
            if (!PUBLIC_TYPES.contains(type) && !PERMISSIONS.containsKey(type)) {
                unclassified.add(type);
            }
        }
        if (!unclassified.isEmpty()) {
            throw new IllegalStateException(
                    "以下 MessageType 既未登记为公开接口、也不在权限表中，新增接口必须显式声明其权限：" + unclassified);
        }

        List<MessageType> publicButRegistered = new ArrayList<>();
        for (MessageType type : PUBLIC_TYPES) {
            if (PERMISSIONS.containsKey(type)) {
                publicButRegistered.add(type);
            }
        }
        if (!publicButRegistered.isEmpty()) {
            throw new IllegalStateException(
                    "以下 MessageType 被同时登记为公开接口与受限接口，语义冲突：" + publicButRegistered);
        }
    }

    /**
     * 登记为「任意已登录角色可访问」。
     */
    private static void allowAny(MessageType... types) {
        for (MessageType type : types) {
            register(type, ANY_ROLE);
        }
    }

    /**
     * 开始一条授权声明，链式写法：{@code grant(角色...).on(接口...)}。
     */
    private static Grant grant(UserRole... roles) {
        return new Grant(roles);
    }

    private static void register(MessageType type, Set<UserRole> roles) {
        if (PERMISSIONS.put(type, roles) != null) {
            throw new IllegalStateException("接口权限重复登记：" + type);
        }
    }

    /**
     * 授权声明中间态：持有角色集合，等待 {@link #on(MessageType...)} 指定其可访问的接口。
     */
    private static final class Grant {

        private final Set<UserRole> roles;

        private Grant(UserRole[] roles) {
            if (roles.length == 0) {
                throw new IllegalArgumentException("授权声明至少需要一个角色");
            }
            this.roles = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(roles)));
        }

        private void on(MessageType... types) {
            for (MessageType type : types) {
                register(type, roles);
            }
        }
    }
}
