package com.vcampus.server.dispatcher;

import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BookVO;
import com.vcampus.common.vo.CartVO;
import com.vcampus.common.vo.ChatMessageVO;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.CourseReviewVO;
import com.vcampus.common.vo.EbookSubmissionVO;
import com.vcampus.common.vo.GoodsVO;
import com.vcampus.common.vo.GradeVO;
import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.PdfPageRequestVO;
import com.vcampus.common.vo.ResourceFileVO;
import com.vcampus.common.vo.SecondHandVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import com.vcampus.common.vo.NoticeQueryVO;
import com.vcampus.server.net.SessionContext;
import com.vcampus.server.session.SessionManager;
import com.vcampus.server.service.BookService;
import com.vcampus.server.service.BorrowService;
import com.vcampus.server.service.CourseSelectionService;
import com.vcampus.server.service.EbookSubmissionService;
import com.vcampus.server.service.CourseService;
import com.vcampus.server.service.GradeService;
import com.vcampus.server.service.ICartService;
import com.vcampus.server.service.IGoodsService;
import com.vcampus.server.service.IOrderService;
import com.vcampus.server.service.PdfRenderService;
import com.vcampus.server.service.ISecondHandService;
import com.vcampus.server.service.IChatService;
import com.vcampus.server.service.UserService;
import com.vcampus.server.service.ResourceService;
import com.vcampus.server.service.NoticeService;
import com.vcampus.server.service.impl.BookServiceImpl;
import com.vcampus.server.service.impl.BorrowServiceImpl;
import com.vcampus.server.service.impl.CourseSelectionServiceImpl;
import com.vcampus.server.service.impl.EbookSubmissionServiceImpl;
import com.vcampus.server.service.impl.CourseReviewServiceImpl;
import com.vcampus.server.service.impl.CourseServiceImpl;
import com.vcampus.server.service.impl.GradeServiceImpl;
import com.vcampus.server.service.impl.CartServiceImpl;
import com.vcampus.server.service.impl.GoodsServiceImpl;
import com.vcampus.server.service.impl.OrderServiceImpl;
import com.vcampus.server.service.impl.NoticeServiceImpl;
import com.vcampus.server.service.impl.SecondHandServiceImpl;
import com.vcampus.server.service.impl.ChatServiceImpl;
import com.vcampus.server.service.impl.UserServiceImpl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务端消息路由与业务调度中心。
 *
 * @author GGbongy
 */
public class Dispatcher {

    /**
     * 本连接对应的会话上下文。身份的唯一可信来源：所有业务分支取用户一律用它，不再读报文里的 uid。
     */
    private final SessionContext session;

    /**
     * 服务端共享的令牌会话表。连接上没有会话时，允许凭令牌认证并把该连接绑定成会话。
     */
    private final SessionManager sessionManager;

    private final CourseService courseService = new CourseServiceImpl();
    private final CourseSelectionService selectionService = new CourseSelectionServiceImpl();
    private final CourseReviewServiceImpl courseReviewService = new CourseReviewServiceImpl();
    private final GradeService gradeService = new GradeServiceImpl();
    private final UserService userService = new UserServiceImpl();
    private final BookService bookService = new BookServiceImpl();
    private final BorrowService borrowService = new BorrowServiceImpl();
    private final ResourceService resourceService = new ResourceService();
    private final PdfRenderService pdfRenderService = new PdfRenderService();
    private final EbookSubmissionService ebookSubmissionService = new EbookSubmissionServiceImpl();
    private final IGoodsService goodsService = new GoodsServiceImpl();
    private final IOrderService orderService = new OrderServiceImpl();
    private final ICartService cartService = new CartServiceImpl();
    private final ISecondHandService secondHandService = new SecondHandServiceImpl();
    private final IChatService chatService = new ChatServiceImpl();
    private final NoticeService noticeService = new NoticeServiceImpl();

    /**
     * 构造一个绑定到指定连接会话的分发器。
     *
     * <p>刻意不提供无参构造：分发器一旦脱离会话就没有可信身份来源，
     * 从编译期杜绝「随手 new 一个 Dispatcher」而绕过认证。</p>
     *
     * @param session        本连接的会话上下文
     * @param sessionManager 服务端共享的令牌会话表
     */
    public Dispatcher(SessionContext session, SessionManager sessionManager) {
        this.session = session;
        this.sessionManager = sessionManager;
    }

    /**
     * 根据 Message.type 将请求分发到对应业务服务，并统一构造响应报文。
     *
     * <p>身份一律取自连接会话（{@link SessionContext}），报文里的 {@code uid} 仅作为回显字段，
     * 服务端不再据此认定调用者是谁。</p>
     *
     * @param request 客户端请求消息
     * @return 响应消息
     */
    public Message dispatch(Message request) {
        Message response = new Message();
        response.setType(request.getType());

        // 公开接口（登录、心跳）无需身份；其余接口一律要求已认证
        if (!PermissionTable.isPublic(request.getType())) {
            String uid = resolveIdentity(request);
            if (uid == null) {
                // 报文带过令牌说明「曾经登录过但凭据已失效」，与「从未登录」区分开，
                // 便于客户端决定是提示重新登录还是打开登录页
                boolean hadToken = request.getToken() != null && !request.getToken().isEmpty();
                response.setCode(hadToken ? ResponseCode.SESSION_EXPIRED : ResponseCode.UNAUTHORIZED);
                response.setData(hadToken
                        ? "登录状态已过期，请重新登录"
                        : "尚未登录或会话已失效，请重新登录");
                return response;
            }

            // 以主键复核账号：被删除或被冻结的账号即使连接仍在，也立即失效
            UserVO currentUser = userService.queryByUid(uid);
            if (currentUser == null) {
                session.clear();
                response.setCode(ResponseCode.UNAUTHORIZED);
                response.setData("账号不存在或已被删除，请重新登录");
                return response;
            }
            if (currentUser.getStatus() != null && currentUser.getStatus() == 0) {
                session.clear();
                response.setCode(ResponseCode.ACCOUNT_FROZEN);
                response.setData("账号已被冻结，请联系管理员");
                return response;
            }

            if (!PermissionTable.isAuthorized(currentUser.getRole(), request.getType())) {
                response.setCode(ResponseCode.PERMISSION_DENIED);
                response.setData("当前角色无权执行该操作");
                return response;
            }
        }

        response.setUid(session.getUid());

        try {
            // 请求类型是服务端唯一的业务路由入口
            switch (request.getType()) {
                case LOGIN:
                    handleLogin(request, response);
                    break;
                case LOGOUT:
                    handleLogout(request, response);
                    break;
                case CHANGE_PASSWORD:
                    handlePasswordChange(request, response);
                    break;
                case UPDATE_USER_INFO:
                    handleUpdateUserInfo(request, response);
                    break;
                case USER_REGISTER:
                    handleUserRegister(request, response);
                    break;
                case USER_LIST:
                    handleUserList(request, response);
                    break;
                case STUDENT_LIST:
                    handleStudentList(request, response);
                    break;
                case USER_UPDATE:
                    handleUserUpdate(request, response);
                    break;
                case USER_DELETE:
                    handleUserDelete(request, response);
                    break;
                case USER_RESET_PASSWORD:
                    handleUserResetPassword(request, response);
                    break;
                case COURSE_ADD:
                    response.setCode(courseService.addCourse((CourseVO) request.getData())
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case COURSE_UPDATE:
                    response.setCode(courseService.updateCourse((CourseVO) request.getData())
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case COURSE_DISABLE:
                    response.setCode(courseService.disableCourse(String.valueOf(request.getData()))
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case COURSE_DELETE:
                    response.setCode(courseService.deleteCourse(String.valueOf(request.getData()))
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case COURSE_APPROVE:
                    response.setCode(courseService.approveCourse(String.valueOf(request.getData()))
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case COURSE_REJECT:
                    response.setCode(courseService.rejectCourse(String.valueOf(request.getData()))
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case COURSE_SCHEDULE:
                    response.setCode(handleCourseSchedule(request));
                    break;
                case COURSE_WEEK_SCHEDULE:
                    response.setCode(handleCourseWeekSchedule(request));
                    break;
                case COURSE_LOCATION_SCHEDULE:
                    response.setCode(handleCourseLocationSchedule(request));
                    break;
                case COURSE_QUERY:
                    response.setData(courseService.queryCourses(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case HEARTBEAT:
                    response.setCode(ResponseCode.SUCCESS);
                    response.setData("pong");
                    break;
                case COURSE_LIST_ALL:
                    response.setData(courseService.listAllCourses());
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case COURSE_QUERY_BY_TEACHER:
                    response.setData(courseService.queryByTeacher(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case COURSE_QUERY_BY_SEMESTER:
                    response.setData(courseService.queryBySemester(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case COURSE_PENDING_LIST:
                    response.setData(courseService.listPendingCourses());
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case COURSE_SELECT:
                case COURSE_DROP:
                    response.setCode(handleSelection(request));
                    break;
                case COURSE_TIMETABLE:
                    response.setData(selectionService.listMyCourses(session.getUid()));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case COURSE_STUDENT_LIST:
                    response.setData(selectionService.listStudentsByCourse(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case GRADE_SUBMIT:
                    response.setCode(gradeService.submitGrade((GradeVO) request.getData()));
                    break;
                case GRADE_QUERY:
                    response.setData(gradeService.queryByStudent(session.getUid()));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case GRADE_QUERY_BY_COURSE:
                    response.setData(gradeService.queryByCourse(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case GRADE_STATISTICS:
                    response.setData(gradeService.getCourseStatistics(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case COURSE_REVIEW_SUBMIT:
                    response.setCode(courseReviewService.submit((CourseReviewVO) request.getData()));
                    break;
                case COURSE_REVIEW_LIST:
                    response.setData(courseReviewService.listByCourse(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case COURSE_REVIEW_DELETE:
                    response.setCode(courseReviewService.delete(session.getUid(),
                            String.valueOf(request.getData())));
                    break;
                case BOOK_QUERY:
                    response.setData(bookService.queryBooks(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case BOOK_ADD:
                    response.setCode(bookService.addBook((BookVO) request.getData())
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case BOOK_UPDATE:
                    response.setCode(bookService.updateBook((BookVO) request.getData())
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case BOOK_DELETE:
                    response.setCode(bookService.deleteBook(String.valueOf(request.getData()))
                            ? ResponseCode.SUCCESS : ResponseCode.FAIL);
                    break;
                case BOOK_BORROW:
                    response.setCode(handleBorrow(request));
                    break;
                case BOOK_RETURN:
                    response.setCode(handleReturn(request));
                    break;
                case BOOK_RENEW:
                    response.setCode(borrowService.renew(session.getUid(), String.valueOf(request.getData())));
                    break;
                case BORROW_MY_LIST:
                    response.setData(borrowService.listByStudent(session.getUid()));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case BORROW_BY_STUDENT:
                    response.setData(borrowService.listByStudent(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case GOODS_QUERY:
                    response.setData(goodsService.queryGoods(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case GOODS_ADD:
                    handleGoodsAdd(request, response);
                    break;
                case GOODS_UPDATE:
                    handleGoodsUpdate(request, response);
                    break;
                case GOODS_DELETE:
                    handleGoodsDelete(request, response);
                    break;
                case GOODS_OFF_SHELF:
                    handleGoodsOffShelf(request, response);
                    break;
                case GOODS_IMAGE_UPLOAD:
                    handleGoodsImageUpload(request, response);
                    break;
                case GOODS_IMAGE_DOWNLOAD:
                    handleGoodsImageDownload(request, response);
                    break;
                case GOODS_IMAGE_DELETE:
                    handleGoodsImageDelete(request, response);
                    break;
                case ORDER_CREATE:
                    OrderVO orderPayload = (OrderVO) request.getData();
                    orderPayload.setStudentId(session.getUid());
                    response.setCode(orderService.createOrder(orderPayload));
                    if (response.getCode() == ResponseCode.SUCCESS) {
                        response.setData(orderPayload);
                    }
                    break;
                case ORDER_QUERY:
                    response.setData(orderService.listOrders(session.getUid()));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case PAYMENT_RECHARGE:
                    handleRecharge(request, response);
                    break;
                case PAYMENT_BALANCE:
                    handleBalance(request, response);
                    break;
                case CART_ADD:
                    handleCartAdd(request, response);
                    break;
                case CART_QUERY:
                    handleCartQuery(request, response);
                    break;
                case CART_UPDATE:
                    handleCartUpdate(request, response);
                    break;
                case CART_REMOVE:
                    handleCartRemove(request, response);
                    break;
                case CART_CLEAR:
                    handleCartClear(request, response);
                    break;
                case ORDER_CHECKOUT:
                    handleCartCheckout(request, response);
                    break;
                case ORDER_LIST_ALL:
                    response.setData(orderService.listAll());
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case ORDER_STATISTICS:
                    response.setData(orderService.getStatistics());
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case SECOND_HAND_QUERY:
                    response.setData(secondHandService.listOnSale());
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case SECOND_HAND_PUBLISH:
                    handleSecondHandPublish(request, response);
                    break;
                case SECOND_HAND_OFF_SHELF:
                    handleSecondHandOffShelf(request, response);
                    break;
                case SECOND_HAND_BUY:
                    handleSecondHandBuy(request, response);
                    break;
                case SECOND_HAND_MY_LIST:
                    response.setData(secondHandService.listMine(session.getUid()));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case SECOND_HAND_PENDING_LIST:
                    response.setData(secondHandService.listPending());
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case SECOND_HAND_REVIEW:
                    handleSecondHandReview(request, response);
                    break;
                case SECOND_HAND_UPDATE_PRICE:
                    handleSecondHandUpdatePrice(request, response);
                    break;
                case CHAT_SEND:
                    handleChatSend(request, response);
                    break;
                case CHAT_HISTORY:
                    handleChatHistory(request, response);
                    break;
                case CHAT_CONVERSATIONS:
                    handleChatConversations(request, response);
                    break;
                case BOOK_RESOURCE_UPLOAD:
                    handleResourceUpload(request, response);
                    break;
                case BOOK_RESOURCE_DOWNLOAD:
                    handleResourceDownload(request, response);
                    break;
                case BOOK_RESOURCE_DELETE:
                    handleResourceDelete(request, response);
                    break;
                case BOOK_RESOURCE_PAGE_COUNT:
                    handleResourcePageCount(request, response);
                    break;
                case BOOK_RESOURCE_RENDER_PAGE:
                    handleResourceRenderPage(request, response);
                    break;
                case EBK_SUBMIT:
                    handleEbookSubmit(request, response);
                    break;
                case EBK_MY_LIST:
                    handleEbookMyList(request, response);
                    break;
                case EBK_PENDING_LIST:
                    handleEbookPendingList(request, response);
                    break;
                case EBK_REVIEW:
                    handleEbookReview(request, response);
                    break;
                case NOTICE_QUERY:
                    handleNoticeQuery(request, response);
                    break;
                case NOTICE_TRIGGER_SYNC:
                    handleNoticeTriggerSync(request, response);
                    break;
                case NOTICE_GET_STATUS:
                    handleNoticeGetStatus(request, response);
                    break;
                default:
                    response.setCode(ResponseCode.INVALID_REQUEST);
                    response.setData("不支持的请求类型");
            }
        } catch (Exception e) {
            response.setCode(ResponseCode.FAIL);
            response.setData(e.getMessage());
        }

        return response;
    }

    /**
     * 解析请求的调用者身份：连接会话优先，其次令牌。
     *
     * <p>两条来源的关系是「会话为主、令牌为辅」：连接上已有会话就直接采信它（这是 A 阶段的机制，
     * 记不住也偷不走）；只有连接尚未认证时才去看令牌，令牌有效即顺手把该连接绑定成会话，
     * 于是后续请求不必再带令牌。报文里的 uid 自始至终不参与判断。</p>
     *
     * @param request 客户端请求
     * @return 认证通过的一卡通号；两条来源都给不出身份时返回 null
     */
    private String resolveIdentity(Message request) {
        if (session.isAuthenticated()) {
            // 已认证的连接：顺带为同属该用户的令牌续期，使滑动过期真正以「还在操作」为准
            sessionManager.touch(request.getToken(), session.getUid());
            return session.getUid();
        }

        String uid = sessionManager.resolveUid(request.getToken());
        if (uid != null) {
            // 令牌即身份，绑定到本连接后无需每个请求再带令牌
            session.authenticate(uid);
        }
        return uid;
    }

    /**
     * 登录成功时返回完整用户信息，客户端据此识别角色。
     */
    private void handleLogin(Message request, Message response) {
        UserVO loginInfo = (UserVO) request.getData();
        UserVO user = userService.login(
                loginInfo.getAccountNumber(),
                loginInfo.getPassword());
        if (user == null) {
            response.setCode(ResponseCode.UNAUTHORIZED);
            response.setData("账号或密码错误");
            return;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            response.setCode(ResponseCode.ACCOUNT_FROZEN);
            response.setData("账号已被冻结，请联系管理员");
            return;
        }
        // 登录成功即把身份写入本连接的会话，此后该连接上的请求都以这个身份为准
        session.authenticate(user.getAccountNumber());
        // 同时签发令牌：换一条连接（重连或另开专门跑大文件的连接）也能凭它自证身份
        response.setToken(sessionManager.createSession(user.getAccountNumber()));
        response.setUid(user.getAccountNumber());
        response.setData(user);
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 注销当前连接上的会话身份，并作废该令牌。客户端登出时调用，之后本连接需重新登录。
     */
    private void handleLogout(Message request, Message response) {
        sessionManager.invalidate(request.getToken());
        session.clear();
        response.setCode(ResponseCode.SUCCESS);
        response.setData("已退出登录");
    }

    /**
     * 注册新用户（仅系统管理员）。
     */
    private void handleUserRegister(Message request, Message response) {
        Object data = request.getData();
        if (!(data instanceof UserVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("注册参数不合法");
            return;
        }
        UserVO user = (UserVO) data;
        if (userService.uidExists(user.getUid())) {
            response.setCode(ResponseCode.USER_EXISTS);
            response.setData("账号已存在");
            return;
        }
        boolean ok = userService.createUser(user);
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
    }

    /**
     * 列出所有用户（仅系统管理员）。
     */
    private void handleUserList(Message request, Message response) {
        response.setData(userService.listAllUsers());
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 返回所有学生账号（学号与姓名），供教师、教务老师登记/统计使用。
     */
    private void handleStudentList(Message request, Message response) {
        List<UserVO> students = new ArrayList<UserVO>();
        for (UserVO user : userService.listAllUsers()) {
            if (user.getRole() == UserRole.STUDENT) {
                students.add(user);
            }
        }
        response.setData(students);
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 修改用户信息（账号/姓名/角色/状态，仅系统管理员）。
     */
    private void handleUserUpdate(Message request, Message response) {
        Object data = request.getData();
        if (!(data instanceof String[]) || ((String[]) data).length < 5) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("修改参数不合法");
            return;
        }
        String[] payload = (String[]) data;
        boolean ok = userService.updateUserInfo(payload[0], payload[1], payload[2], payload[3], payload[4]);
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
    }

    /**
     * 删除用户（仅系统管理员，且不能删除自己、不能删除有未归还图书的用户）。
     */
    private void handleUserDelete(Message request, Message response) {
        String uid = String.valueOf(request.getData());
        if (uid == null || "null".equals(uid) || uid.trim().isEmpty()) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("账号为空");
            return;
        }
        uid = uid.trim();
        if (uid.equals(session.getUid())) {
            response.setCode(ResponseCode.FAIL);
            response.setData("不能删除当前登录的账号");
            return;
        }
        if (borrowService.hasActiveBorrows(uid)) {
            response.setCode(ResponseCode.USER_HAS_ACTIVE_BORROW);
            response.setData("该用户还有未归还图书，请先办理归还");
            return;
        }
        boolean ok = userService.deleteUser(uid);
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
    }

    /**
     * 管理员重置用户密码（仅系统管理员）。
     */
    private void handleUserResetPassword(Message request, Message response) {
        Object data = request.getData();
        if (!(data instanceof String[]) || ((String[]) data).length < 2) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("参数不合法");
            return;
        }
        String[] payload = (String[]) data;
        boolean ok = userService.resetPassword(payload[0], payload[1]);
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
    }

    /**
     * 读者提交纯电子书投稿。
     */
    private void handleEbookSubmit(Message request, Message response) {
        Object data = request.getData();
        if (!(data instanceof EbookSubmissionVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("投稿参数不合法");
            return;
        }
        EbookSubmissionVO submission = (EbookSubmissionVO) data;
        submission.setUploaderUid(session.getUid());
        boolean ok = ebookSubmissionService.submit(submission);
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
    }

    /**
     * 查询当前用户的投稿记录。
     */
    private void handleEbookMyList(Message request, Message response) {
        response.setData(ebookSubmissionService.listByUploader(session.getUid()));
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 查询待审核投稿。
     */
    private void handleEbookPendingList(Message request, Message response) {
        response.setData(ebookSubmissionService.listPending());
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 审核投稿：通过则上架为纯电子书，驳回则删除资源文件。
     */
    private void handleEbookReview(Message request, Message response) {
        Object data = request.getData();
        if (!(data instanceof String[]) || ((String[]) data).length < 2) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("审核参数不合法");
            return;
        }
        String[] payload = (String[]) data;
        int id;
        try {
            id = Integer.parseInt(payload[0]);
        } catch (NumberFormatException e) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("投稿 ID 不合法");
            return;
        }
        boolean approve = "APPROVE".equals(payload[1]);
        String comment = payload.length >= 3 ? payload[2] : null;

        EbookSubmissionVO submission = ebookSubmissionService.findById(id);
        if (submission == null || !"PENDING".equals(submission.getStatus())) {
            response.setCode(ResponseCode.FAIL);
            response.setData("投稿不存在或已审核");
            return;
        }

        if (approve) {
            BookVO book = new BookVO();
            book.setIsbn("EB-" + submission.getId());
            book.setTitle(submission.getTitle());
            book.setAuthor(submission.getAuthor());
            book.setPublisher(submission.getPublisher());
            book.setLocation(null);
            book.setResourceFile(submission.getResourceFile());
            book.setType("EBOOK");
            book.setTotalNum(0);
            book.setCurrentNum(0);
            boolean added = bookService.addBook(book);
            if (!added) {
                response.setCode(ResponseCode.FAIL);
                response.setData("上架电子书失败");
                return;
            }
            ebookSubmissionService.updateStatus(id, "APPROVED", session.getUid(), comment);
            response.setCode(ResponseCode.SUCCESS);
        } else {
            resourceService.delete(submission.getResourceFile());
            ebookSubmissionService.updateStatus(id, "REJECTED", session.getUid(), comment);
            response.setCode(ResponseCode.SUCCESS);
        }
    }

    /**
     * 处理修改密码请求（接收 String[]{oldPassword, newPassword}）
     */
    private void handlePasswordChange(Message request, Message response) {
        if (request.getData() instanceof String[] pwdData) {
            if (pwdData.length >= 2) {
                String oldPwd = pwdData[0];
                String newPwd = pwdData[1];
                boolean ok = userService.changePassword(session.getUid(), oldPwd, newPwd);
                if (ok) {
                    response.setCode(ResponseCode.SUCCESS);
                    response.setData("密码修改成功");
                } else {
                    response.setCode(ResponseCode.FAIL);
                    response.setData("原密码错误或新密码与原密码相同");
                }
                return;
            }
        }
        response.setCode(ResponseCode.INVALID_REQUEST);
        response.setData("修改密码请求参数不合法");
    }

    /**
     * 处理用户信息更新（如充值新余额，支持传入 BigDecimal 或 UserVO）
     */
    private void handleUpdateUserInfo(Message request, Message response) {
        BigDecimal newBalance = null;
        if (request.getData() instanceof BigDecimal) {
            newBalance = (BigDecimal) request.getData();
        } else if (request.getData() instanceof UserVO) {
            newBalance = ((UserVO) request.getData()).getBalance();
        }

        if (newBalance != null) {
            boolean ok = userService.updateBalance(session.getUid(), newBalance);
            if (ok) {
                UserVO updatedUser = userService.queryByUid(session.getUid());
                response.setCode(ResponseCode.SUCCESS);
                response.setData(updatedUser);
            } else {
                response.setCode(ResponseCode.FAIL);
                response.setData("更新用户余额失败");
            }
            return;
        }
        response.setCode(ResponseCode.INVALID_REQUEST);
        response.setData("更新信息参数错误");
    }

    /**
     * 处理选课或退课请求
     * @author xingyi852
     */
    private ResponseCode handleSelection(Message request) {
        String courseCode = String.valueOf(request.getData()).trim();
        if (courseCode.isEmpty() || "null".equals(courseCode)) {
            return ResponseCode.INVALID_REQUEST;
        }
        if (request.getType() == MessageType.COURSE_SELECT) {
            return selectionService.selectCourse(session.getUid(), courseCode);
        }
        return selectionService.dropCourse(session.getUid(), courseCode);
    }

    /**
     * 办理借阅：从负载解析借阅人学号与 ISBN。
     */
    private ResponseCode handleBorrow(Message request) {
        String[] payload = toBorrowPayload(request.getData());
        if (payload == null) {
            return ResponseCode.INVALID_REQUEST;
        }
        return borrowService.borrow(payload[0], payload[1]);
    }

    /**
     * 办理归还：从负载解析借阅人学号与 ISBN。
     */
    private ResponseCode handleReturn(Message request) {
        String[] payload = toBorrowPayload(request.getData());
        if (payload == null) {
            return ResponseCode.INVALID_REQUEST;
        }
        return borrowService.returnBook(payload[0], payload[1]);
    }

    /**
     * 借还请求负载约定为 String[]{借阅人学号, isbn}。
     */
    private String[] toBorrowPayload(Object data) {
        if (data instanceof String[] && ((String[]) data).length >= 2) {
            return (String[]) data;
        }
        return null;
    }

    /**
     * 处理电子资源上传：保存文件并返回服务器端文件名。
     */
    private void handleResourceUpload(Message request, Message response) {
        Object data = request.getData();
        if (!(data instanceof ResourceFileVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("上传参数不合法");
            return;
        }
        ResourceFileVO file = (ResourceFileVO) data;
        if (file.getData() == null || file.getData().length == 0) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("上传文件为空");
            return;
        }
        String name = resourceService.store(file.getData());
        response.setCode(ResponseCode.SUCCESS);
        response.setData(name);
    }

    /**
     * 处理电子资源下载：按文件名读取文件并返回字节内容。
     */
    private void handleResourceDownload(Message request, Message response) {
        String name = String.valueOf(request.getData());
        if (name == null || "null".equals(name) || name.trim().isEmpty()) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("资源标识为空");
            return;
        }
        byte[] data = resourceService.load(name.trim());
        ResourceFileVO file = new ResourceFileVO();
        file.setFileName(name.trim());
        file.setData(data);
        response.setCode(ResponseCode.SUCCESS);
        response.setData(file);
    }

    /**
     * 处理电子资源删除：按文件名删除服务器本地文件。
     */
    private void handleResourceDelete(Message request, Message response) {
        String name = String.valueOf(request.getData());
        if (name == null || "null".equals(name) || name.trim().isEmpty()) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("资源标识为空");
            return;
        }
        boolean ok = resourceService.delete(name.trim());
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
    }

    /**
     * 处理电子资源页数查询：返回总页数。
     */
    private void handleResourcePageCount(Message request, Message response) {
        String name = request.getData() == null ? "" : String.valueOf(request.getData());
        if (name.isEmpty() || "null".equals(name)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("资源标识为空");
            return;
        }
        try {
            response.setData(pdfRenderService.getPageCount(name.trim()));
            response.setCode(ResponseCode.SUCCESS);
        } catch (Exception e) {
            response.setCode(ResponseCode.FAIL);
            response.setData("读取电子资源失败: " + e.getMessage());
        }
    }

    /**
     * 处理电子资源单页渲染：负载为 PdfPageRequestVO，返回 PNG 图片字节。
     */
    private void handleResourceRenderPage(Message request, Message response) {
        if (!(request.getData() instanceof PdfPageRequestVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("渲染参数不合法");
            return;
        }
        PdfPageRequestVO req = (PdfPageRequestVO) request.getData();
        if (req.getResourceName() == null || req.getResourceName().trim().isEmpty()) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("资源标识为空");
            return;
        }
        try {
            byte[] data = pdfRenderService.renderPage(req.getResourceName().trim(), req.getPageIndex(), req.getWidth());
            ResourceFileVO file = new ResourceFileVO();
            file.setFileName(req.getResourceName().trim());
            file.setData(data);
            response.setCode(ResponseCode.SUCCESS);
            response.setData(file);
        } catch (Exception e) {
            response.setCode(ResponseCode.FAIL);
            response.setData("渲染电子资源失败: " + e.getMessage());
        }
    }

    /**
     * 处理超市下单结账：负载为 OrderVO，studentId 以请求方为准。
     */
    private ResponseCode handleOrderCreate(Message request) {
        if (!(request.getData() instanceof OrderVO)) {
            return ResponseCode.INVALID_REQUEST;
        }
        OrderVO order = (OrderVO) request.getData();
        order.setStudentId(session.getUid());
        return orderService.createOrder(order);
    }

    /**
     * 处理一卡通在线充值：负载为充值金额（BigDecimal 或字符串）。
     */
    private void handleRecharge(Message request, Message response) {
        BigDecimal amount = toRechargeAmount(request.getData());
        if (amount == null) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("充值金额不合法");
            return;
        }
        UserVO user = userService.queryByUid(session.getUid());
        if (user == null) {
            response.setCode(ResponseCode.FAIL);
            response.setData("用户不存在");
            return;
        }
        BigDecimal current = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal target = current.add(amount);
        if (!userService.updateBalance(session.getUid(), target)) {
            response.setCode(ResponseCode.FAIL);
            response.setData("余额更新失败");
            return;
        }
        response.setCode(ResponseCode.SUCCESS);
        response.setData(userService.queryByUid(session.getUid()));
    }

    /**
     * 处理一卡通余额查询：返回最新用户实体（含余额）。
     */
    private void handleBalance(Message request, Message response) {
        UserVO user = userService.queryByUid(session.getUid());
        if (user == null) {
            response.setCode(ResponseCode.FAIL);
            response.setData("用户不存在");
            return;
        }
        response.setCode(ResponseCode.SUCCESS);
        response.setData(user);
    }

    /**
     * 解析充值金额，仅接受正数。
     */
    private BigDecimal toRechargeAmount(Object data) {
        BigDecimal amount = null;
        if (data instanceof BigDecimal) {
            amount = (BigDecimal) data;
        } else if (data instanceof String) {
            try {
                amount = new BigDecimal(((String) data).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null;
    }

    /**
     * 处理新增商品：仅管理员或卖家允许。
     */
    private void handleGoodsAdd(Message request, Message response) {
        if (!(request.getData() instanceof GoodsVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("商品参数不合法");
            return;
        }
        boolean ok = goodsService.addGoods((GoodsVO) request.getData());
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
        if (!ok) {
            response.setData("新增商品失败，商品编号可能已存在");
        }
    }

    /**
     * 处理修改商品：仅管理员或卖家允许。
     */
    private void handleGoodsUpdate(Message request, Message response) {
        if (!(request.getData() instanceof GoodsVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("商品参数不合法");
            return;
        }
        boolean ok = goodsService.updateGoods((GoodsVO) request.getData());
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
        if (!ok) {
            response.setData("修改商品失败，商品可能不存在或参数有误");
        }
    }

    /**
     * 处理删除商品：仅管理员或卖家允许。
     */
    private void handleGoodsDelete(Message request, Message response) {
        String goodsId = String.valueOf(request.getData());
        if (goodsId == null || "null".equals(goodsId) || goodsId.trim().isEmpty()) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("商品编号为空");
            return;
        }
        boolean ok = goodsService.deleteGoods(goodsId.trim());
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
        if (!ok) {
            response.setData("删除商品失败，商品可能不存在");
        }
    }

    /**
     * 处理商品强制下架：仅管理员允许（权限由 PermissionTable 统一校验）。
     */
    private void handleGoodsOffShelf(Message request, Message response) {
        String goodsId = String.valueOf(request.getData());
        if (goodsId == null || "null".equals(goodsId) || goodsId.trim().isEmpty()) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("商品编号为空");
            return;
        }
        boolean ok = goodsService.offShelf(goodsId.trim());
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
        if (!ok) {
            response.setData("下架失败，商品可能不存在或已下架");
        }
    }

    /**
     * 处理商品图片上传：仅管理员或卖家允许（权限由 PermissionTable 统一校验），保存后返回服务端文件名。
     */
    private void handleGoodsImageUpload(Message request, Message response) {
        if (!(request.getData() instanceof ResourceFileVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("图片参数不合法");
            return;
        }
        ResourceFileVO file = (ResourceFileVO) request.getData();
        if (file.getData() == null || file.getData().length == 0) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("上传图片为空");
            return;
        }
        String name = resourceService.storeImage(file.getData(), file.getFileName());
        response.setCode(ResponseCode.SUCCESS);
        response.setData(name);
    }

    /**
     * 处理商品图片下载：按文件名读取图片字节并返回。所有登录用户均可浏览商品图片。
     */
    private void handleGoodsImageDownload(Message request, Message response) {
        String name = request.getData() == null ? "" : String.valueOf(request.getData());
        if (name.isEmpty() || "null".equals(name)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("图片标识为空");
            return;
        }
        byte[] data = resourceService.loadImage(name.trim());
        ResourceFileVO file = new ResourceFileVO();
        file.setFileName(name.trim());
        file.setData(data);
        response.setCode(ResponseCode.SUCCESS);
        response.setData(file);
    }

    /**
     * 处理商品图片删除：仅管理员或卖家允许。
     */
    private void handleGoodsImageDelete(Message request, Message response) {
        String name = request.getData() == null ? "" : String.valueOf(request.getData());
        if (name.isEmpty() || "null".equals(name)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("图片标识为空");
            return;
        }
        boolean ok = resourceService.deleteImage(name.trim());
        response.setCode(ok ? ResponseCode.SUCCESS : ResponseCode.FAIL);
    }

    /**
     * 处理教务老师安排或修改课程上课时间请求。
     */
    private ResponseCode handleCourseSchedule(Message request) {
        String[] payload = toBorrowPayload(request.getData());
        if (payload == null || payload[0] == null || payload[1] == null) {
            return ResponseCode.INVALID_REQUEST;
        }
        boolean ok = courseService.scheduleCourseTime(payload[0].trim(), payload[1].trim());
        return ok ? ResponseCode.SUCCESS : ResponseCode.FAIL;
    }

    /**
     * 处理教务老师安排或修改课程起止周次请求。
     */
    private ResponseCode handleCourseWeekSchedule(Message request) {
        String[] payload = toStringArray(request.getData(), 3);
        if (payload == null) {
            return ResponseCode.INVALID_REQUEST;
        }
        try {
            int startWeek = Integer.parseInt(payload[1].trim());
            int endWeek = Integer.parseInt(payload[2].trim());
            boolean ok = courseService.scheduleCourseWeeks(payload[0].trim(), startWeek, endWeek);
            return ok ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (NumberFormatException e) {
            return ResponseCode.INVALID_REQUEST;
        }
    }

    /**
     * 处理教务老师安排或修改课程上课地点请求。
     */
    private ResponseCode handleCourseLocationSchedule(Message request) {
        String[] payload = toBorrowPayload(request.getData());
        if (payload == null || payload[0] == null || payload[1] == null) {
            return ResponseCode.INVALID_REQUEST;
        }
        boolean ok = courseService.scheduleCourseLocation(payload[0].trim(), payload[1].trim());
        return ok ? ResponseCode.SUCCESS : ResponseCode.FAIL;
    }

    /**
     * 将消息负载安全转换为指定长度的字符串数组。
     */
    private String[] toStringArray(Object data, int expectedLength) {
        if (data instanceof String[] && ((String[]) data).length >= expectedLength) {
            return (String[]) data;
        }
        return null;
    }
    /**
     * 处理加入购物车：负载为 CartVO（含 goodsId 与 count），studentId 以请求方为准。
     */
    private void handleCartAdd(Message request, Message response) {
        if (!(request.getData() instanceof CartVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("购物车参数不合法");
            return;
        }
        CartVO cart = (CartVO) request.getData();
        String goodsId = cart.getGoodsId() == null ? "" : cart.getGoodsId();
        ResponseCode code = cartService.addItem(session.getUid(), goodsId, cart.getCount());
        response.setCode(code);
        if (code == ResponseCode.GOODS_STOCK_INSUFFICIENT) {
            response.setData("加入失败：已达该商品库存上限（含购物车已有数量）");
        } else if (code == ResponseCode.GOODS_NOT_FOUND) {
            response.setData("商品不存在或已下架");
        } else if (code != ResponseCode.SUCCESS) {
            response.setData("加入购物车失败，请稍后重试");
        }
    }

    /**
     * 处理查询购物车：返回购物车条目列表（含商品快照）。
     */
    private void handleCartQuery(Message request, Message response) {
        response.setData(cartService.listCart(session.getUid()));
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 处理更新购物车数量：负载为 CartVO（含 goodsId 与新 count）。
     */
    private void handleCartUpdate(Message request, Message response) {
        if (!(request.getData() instanceof CartVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("购物车参数不合法");
            return;
        }
        CartVO cart = (CartVO) request.getData();
        String goodsId = cart.getGoodsId() == null ? "" : cart.getGoodsId();
        response.setCode(cartService.updateCount(session.getUid(), goodsId, cart.getCount()));
    }

    /**
     * 处理移除购物车条目：负载为商品编码字符串。
     */
    private void handleCartRemove(Message request, Message response) {
        response.setCode(cartService.removeItem(session.getUid(), String.valueOf(request.getData())));
    }

    /**
     * 处理清空购物车。
     */
    private void handleCartClear(Message request, Message response) {
        response.setCode(cartService.clearCart(session.getUid()));
    }

    /**
     * 处理购物车批量结算：成功后返回本次生成的订单列表。
     */
    private void handleCartCheckout(Message request, Message response) {
        List<OrderVO> created = new ArrayList<>();
        ResponseCode code = orderService.checkoutCart(session.getUid(), created);
        response.setCode(code);
        if (code == ResponseCode.SUCCESS) {
            response.setData(created);
        } else {
            response.setData("结算失败，请检查商品状态、库存与余额");
        }
    }

    /**
     * 处理教务公告查询请求
     *
     * @author Serissia
     */
    private void handleNoticeQuery(Message request, Message response) {
        NoticeQueryVO query;
        if (request.getData() instanceof NoticeQueryVO) {
            query = (NoticeQueryVO) request.getData();
        } else if (request.getData() instanceof String) {
            query = new NoticeQueryVO((String) request.getData(), null, null);
        } else {
            query = new NoticeQueryVO();
        }
        response.setData(noticeService.queryNotices(query));
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 处理手动同步教务公告请求
     *
     * @author Serissia
     */
    private void handleNoticeTriggerSync(Message request, Message response) {
        int days = 7;
        if (request.getData() instanceof Integer) {
            days = (Integer) request.getData();
        } else if (request.getData() instanceof String) {
            try {
                days = Integer.parseInt(((String) request.getData()).trim());
            } catch (NumberFormatException ignored) {
                days = 7;
            }
        }

        // 组装调用来源描述：包含操作人一卡通号/UID
        String source = "用户手动触发 (UID: " + session.getUid() + ")";
        ResponseCode code = noticeService.triggerSync(days, source);
        response.setCode(code);
        response.setData(noticeService.getStatus());
    }

    /**
     * 处理获取教务公告同步状态请求
     *
     * @author Serissia
     */
    private void handleNoticeGetStatus(Message request, Message response) {
        response.setData(noticeService.getStatus());
        response.setCode(ResponseCode.SUCCESS);
    }
    /**
     * 处理发布二手商品：负载为 SecondHandVO（title/price/description）。
     */
    private void handleSecondHandPublish(Message request, Message response) {
        if (!(request.getData() instanceof SecondHandVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("发布参数不合法");
            return;
        }
        SecondHandVO vo = (SecondHandVO) request.getData();
        ResponseCode code = secondHandService.publish(session.getUid(), vo);
        response.setCode(code);
        if (code != ResponseCode.SUCCESS) {
            response.setData("发布失败，请检查标题与定价");
        }
    }

    /**
     * 处理卖家下架自己发布的二手商品：负载为商品 ID。
     */
    private void handleSecondHandOffShelf(Message request, Message response) {
        Integer id = toIntegerId(request.getData());
        if (id == null) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("商品编号不合法");
            return;
        }
        ResponseCode code = secondHandService.offShelf(session.getUid(), id);
        response.setCode(code);
        if (code != ResponseCode.SUCCESS) {
            response.setData("下架失败，可能不是你的商品或已售出");
        }
    }

    /**
     * 处理购买二手商品：负载为商品 ID。
     */
    private void handleSecondHandBuy(Message request, Message response) {
        Integer id = toIntegerId(request.getData());
        if (id == null) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("商品编号不合法");
            return;
        }
        ResponseCode code = secondHandService.buy(session.getUid(), id);
        response.setCode(code);
        if (code != ResponseCode.SUCCESS) {
            String msg;
            if (code == ResponseCode.SECOND_HAND_SOLD) {
                msg = "该商品已被买走或已下架";
            } else if (code == ResponseCode.BALANCE_INSUFFICIENT) {
                msg = "校园卡余额不足，请先充值";
            } else if (code == ResponseCode.INVALID_REQUEST) {
                msg = "不能购买自己发布的商品";
            } else {
                msg = "购买失败，请稍后重试";
            }
            response.setData(msg);
        }
    }

    /**
     * 处理卖家修改二手商品价格：负载为 SecondHandVO（id 与 price）。
     */
    private void handleSecondHandUpdatePrice(Message request, Message response) {
        if (!(request.getData() instanceof SecondHandVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("参数不合法");
            return;
        }
        SecondHandVO vo = (SecondHandVO) request.getData();
        ResponseCode code = secondHandService.updatePrice(request.getUid(), vo.getId(), vo.getPrice());
        response.setCode(code);
        if (code == ResponseCode.INVALID_REQUEST) {
            response.setData("价格需在 0.01 ~ 99999.99 之间（最多两位小数）");
        } else if (code == ResponseCode.SECOND_HAND_SOLD) {
            response.setData("商品已售出或已下架，无法改价");
        } else if (code == ResponseCode.UNAUTHORIZED) {
            response.setData("只能修改自己发布的商品价格");
        } else if (code != ResponseCode.SUCCESS) {
            response.setData("改价失败，请稍后重试");
        }
    }
    /**
     * 处理管理员审核二手商品：负载为 SecondHandVO（id 为商品 ID，status 为审核结果
     * APPROVE 通过 / REJECT 拒绝）。
     */
    private void handleSecondHandReview(Message request, Message response) {
        if (!(request.getData() instanceof SecondHandVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("审核参数不合法");
            return;
        }
        SecondHandVO vo = (SecondHandVO) request.getData();
        Integer id = vo.getId();
        boolean approve = "APPROVE".equalsIgnoreCase(vo.getStatus());
        if (id == null) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("商品编号不合法");
            return;
        }
        ResponseCode code = secondHandService.review(session.getUid(), id, approve);
        response.setCode(code);
        if (code != ResponseCode.SUCCESS) {
            response.setData("审核失败，商品可能不存在或已被处理");
        }
    }

    /**
     * 处理发送聊天消息：负载为 ChatMessageVO（itemId + toUid + content）。
     */
    private void handleChatSend(Message request, Message response) {
        if (!(request.getData() instanceof ChatMessageVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("消息参数不合法");
            return;
        }
        ChatMessageVO vo = (ChatMessageVO) request.getData();
        ResponseCode code = chatService.send(session.getUid(), vo.getItemId(), vo.getToUid(), vo.getContent());
        response.setCode(code);
        if (code != ResponseCode.SUCCESS) {
            response.setData("发送失败，请检查消息内容");
        }
    }

    /**
     * 处理拉取聊天历史：负载为 ChatMessageVO（itemId + toUid，toUid 为对方）。
     */
    private void handleChatHistory(Message request, Message response) {
        if (!(request.getData() instanceof ChatMessageVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("参数不合法");
            return;
        }
        ChatMessageVO vo = (ChatMessageVO) request.getData();
        response.setData(chatService.history(vo.getItemId(), session.getUid(), vo.getToUid()));
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 处理卖家查看某商品咨询会话：负载为 ChatMessageVO（仅 itemId）。
     */
    private void handleChatConversations(Message request, Message response) {
        if (!(request.getData() instanceof ChatMessageVO)) {
            response.setCode(ResponseCode.INVALID_REQUEST);
            response.setData("参数不合法");
            return;
        }
        ChatMessageVO vo = (ChatMessageVO) request.getData();
        response.setData(chatService.conversations(vo.getItemId(), session.getUid()));
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 将负载安全转换为 Integer 商品 ID。
     */
    private Integer toIntegerId(Object data) {
        if (data instanceof Integer) {
            return (Integer) data;
        }
        if (data instanceof String) {
            try {
                return Integer.valueOf(((String) data).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
