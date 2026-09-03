package com.vcampus.server.dispatcher;

import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.BookVO;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.GoodsVO;
import com.vcampus.common.vo.GradeVO;
import com.vcampus.common.vo.OrderVO;
import com.vcampus.common.vo.ResourceFileVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.common.vo.UserVO;
import com.vcampus.common.vo.UserRole;
import com.vcampus.server.service.BookService;
import com.vcampus.server.service.BorrowService;
import com.vcampus.common.vo.UserRole;
import com.vcampus.server.service.BookService;
import com.vcampus.server.service.BorrowService;
import com.vcampus.server.service.CourseSelectionService;
import com.vcampus.server.service.CourseService;
import com.vcampus.server.service.GradeService;
import com.vcampus.server.service.IGoodsService;
import com.vcampus.server.service.IOrderService;
import com.vcampus.server.service.ResourceService;
import com.vcampus.server.service.UserService;
import com.vcampus.server.service.impl.BookServiceImpl;
import com.vcampus.server.service.impl.BorrowServiceImpl;
import com.vcampus.server.service.impl.CourseSelectionServiceImpl;
import com.vcampus.server.service.impl.CourseServiceImpl;
import com.vcampus.server.service.impl.GradeServiceImpl;
import com.vcampus.server.service.impl.GoodsServiceImpl;
import com.vcampus.server.service.impl.OrderServiceImpl;
import com.vcampus.server.service.impl.UserServiceImpl;

import java.math.BigDecimal;

/**
 * 服务端消息路由与业务调度中心。
 *
 * @author GGbongy
 */
public class Dispatcher {

    private final CourseService courseService = new CourseServiceImpl();
    private final CourseSelectionService selectionService = new CourseSelectionServiceImpl();
    private final GradeService gradeService = new GradeServiceImpl();
    private final UserService userService = new UserServiceImpl();
    private final BookService bookService = new BookServiceImpl();
    private final BorrowService borrowService = new BorrowServiceImpl();
    private final ResourceService resourceService = new ResourceService();
    private final IGoodsService goodsService = new GoodsServiceImpl();
    private final IOrderService orderService = new OrderServiceImpl();

    /**
     * 根据 Message.type 将请求分发到对应业务服务，并统一构造响应报文。
     *
     * @param request 客户端请求消息
     * @return 响应消息
     */
    public Message dispatch(Message request) {
        Message response = new Message();
        response.setUid(request.getUid());
        response.setType(request.getType());

        if (requiresPermissionCheck(request.getType())
                && !hasPermission(request.getUid(), request.getType())) {
            response.setCode(ResponseCode.PERMISSION_DENIED);
            response.setData("当前角色无权执行该操作");
            return response;
        }

        try {
            // 请求类型是服务端唯一的业务路由入口
            switch (request.getType()) {
                case LOGIN:
                    handleLogin(request, response);
                    break;
                case CHANGE_PASSWORD:
                    handlePasswordChange(request, response);
                    break;
                case UPDATE_USER_INFO:
                    handleUpdateUserInfo(request, response);
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
                    response.setData(selectionService.listMyCourses(request.getUid()));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case GRADE_SUBMIT:
                    response.setCode(gradeService.submitGrade((GradeVO) request.getData()));
                    break;
                case GRADE_QUERY:
                    response.setData(gradeService.queryByStudent(request.getUid()));
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
                case BORROW_MY_LIST:
                    response.setData(borrowService.listByStudent(request.getUid()));
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
                case ORDER_CREATE:
                    response.setCode(handleOrderCreate(request));
                    break;
                case ORDER_QUERY:
                    response.setData(orderService.listOrders(request.getUid()));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case PAYMENT_RECHARGE:
                    handleRecharge(request, response);
                    break;
                case PAYMENT_BALANCE:
                    handleBalance(request, response);
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

    private boolean requiresPermissionCheck(MessageType type) {
        switch (type) {
            case COURSE_QUERY:
            case COURSE_ADD:
            case COURSE_UPDATE:
            case COURSE_DISABLE:
            case COURSE_DELETE:
            case COURSE_APPROVE:
            case COURSE_REJECT:
            case COURSE_LIST_ALL:
            case COURSE_QUERY_BY_TEACHER:
            case COURSE_QUERY_BY_SEMESTER:
            case COURSE_PENDING_LIST:
            case COURSE_SCHEDULE:
            case COURSE_WEEK_SCHEDULE:
            case COURSE_SELECT:
            case COURSE_DROP:
            case COURSE_TIMETABLE:
            case GRADE_SUBMIT:
            case GRADE_QUERY:
            case GRADE_QUERY_BY_COURSE:
            case GRADE_STATISTICS:
            case BOOK_QUERY:
            case BOOK_ADD:
            case BOOK_UPDATE:
            case BOOK_DELETE:
            case BOOK_BORROW:
            case BOOK_RETURN:
            case BORROW_MY_LIST:
            case BORROW_BY_STUDENT:
            case BOOK_RESOURCE_UPLOAD:
            case BOOK_RESOURCE_DOWNLOAD:
                return true;
            default:
                return false;
        }
    }

    private boolean hasPermission(String uid, MessageType type) {
        UserVO user = userService.queryByUid(uid);
        if (user == null || user.getRole() == null) {
            return false;
        }

        UserRole role = user.getRole();
        switch (type) {
            case COURSE_QUERY:
            case COURSE_LIST_ALL:
            case COURSE_QUERY_BY_TEACHER:
            case COURSE_QUERY_BY_SEMESTER:
                return true;
            case COURSE_ADD:
            case COURSE_UPDATE:
            case COURSE_DISABLE:
                return isCourseManager(role);
            case COURSE_DELETE:
                return role == UserRole.ADMIN || role == UserRole.ACADEMIC_AFFAIRS_TEACHER;
            case COURSE_APPROVE:
            case COURSE_REJECT:
            case COURSE_PENDING_LIST:
                return role == UserRole.ADMIN || role == UserRole.ACADEMIC_AFFAIRS_TEACHER;
            case COURSE_SCHEDULE:
                return role == UserRole.ADMIN || role == UserRole.ACADEMIC_AFFAIRS_TEACHER;
            case COURSE_WEEK_SCHEDULE:
                return role == UserRole.ADMIN || role == UserRole.ACADEMIC_AFFAIRS_TEACHER;
            case COURSE_SELECT:
            case COURSE_DROP:
            case COURSE_TIMETABLE:
                return role == UserRole.STUDENT;
            case GRADE_SUBMIT:
                return role == UserRole.ADMIN
                        || role == UserRole.ACADEMIC_AFFAIRS_TEACHER
                        || role == UserRole.TEACHER;
            case GRADE_QUERY:
                return role == UserRole.STUDENT
                        || role == UserRole.TEACHER
                        || role == UserRole.ACADEMIC_AFFAIRS_TEACHER
                        || role == UserRole.ADMIN;
            case GRADE_QUERY_BY_COURSE:
            case GRADE_STATISTICS:
                return role == UserRole.ADMIN
                        || role == UserRole.ACADEMIC_AFFAIRS_TEACHER
                        || role == UserRole.TEACHER;
            case BOOK_QUERY:
                return true;
            case BOOK_ADD:
            case BOOK_UPDATE:
            case BOOK_DELETE:
            case BOOK_RESOURCE_UPLOAD:
                return role == UserRole.ADMIN;
            case BOOK_BORROW:
            case BOOK_RETURN:
            case BORROW_MY_LIST:
                return role == UserRole.STUDENT
                        || role == UserRole.ADMIN;
            case BORROW_BY_STUDENT:
            case BOOK_RESOURCE_DOWNLOAD:
                return role == UserRole.ADMIN
                        || role == UserRole.STUDENT
                        || role == UserRole.TEACHER;
            default:
                return false;
        }
    }

    private boolean isCourseManager(UserRole role) {
        return role == UserRole.ADMIN
                || role == UserRole.ACADEMIC_AFFAIRS_TEACHER
                || role == UserRole.TEACHER;
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
            response.setData(null);
            return;
        }
        response.setUid(user.getAccountNumber());
        response.setData(user);
        response.setCode(ResponseCode.SUCCESS);
    }

    /**
     * 处理修改密码请求（接收 String[]{oldPassword, newPassword}）
     */
    private void handlePasswordChange(Message request, Message response) {
        if (request.getData() instanceof String[]) {
            String[] pwdData = (String[]) request.getData();
            if (pwdData.length >= 2) {
                String oldPwd = pwdData[0];
                String newPwd = pwdData[1];
                boolean ok = userService.changePassword(request.getUid(), oldPwd, newPwd);
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
            boolean ok = userService.updateBalance(request.getUid(), newBalance);
            if (ok) {
                UserVO updatedUser = userService.queryByUid(request.getUid());
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
            return selectionService.selectCourse(request.getUid(), courseCode);
        }
        return selectionService.dropCourse(request.getUid(), courseCode);
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
     * 处理超市下单结账：负载为 OrderVO，studentId 以请求方为准。
     */
    private ResponseCode handleOrderCreate(Message request) {
        if (!(request.getData() instanceof OrderVO)) {
            return ResponseCode.INVALID_REQUEST;
        }
        OrderVO order = (OrderVO) request.getData();
        order.setStudentId(request.getUid());
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
        UserVO user = userService.queryByUid(request.getUid());
        if (user == null) {
            response.setCode(ResponseCode.FAIL);
            response.setData("用户不存在");
            return;
        }
        BigDecimal current = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal target = current.add(amount);
        if (!userService.updateBalance(request.getUid(), target)) {
            response.setCode(ResponseCode.FAIL);
            response.setData("余额更新失败");
            return;
        }
        response.setCode(ResponseCode.SUCCESS);
        response.setData(userService.queryByUid(request.getUid()));
    }

    /**
     * 处理一卡通余额查询：返回最新用户实体（含余额）。
     */
    private void handleBalance(Message request, Message response) {
        UserVO user = userService.queryByUid(request.getUid());
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
        if (!isGoodsManager(request)) {
            response.setCode(ResponseCode.UNAUTHORIZED);
            response.setData("无权执行该操作：仅管理员或卖家可管理商品");
            return;
        }
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
        if (!isGoodsManager(request)) {
            response.setCode(ResponseCode.UNAUTHORIZED);
            response.setData("无权执行该操作：仅管理员或卖家可管理商品");
            return;
        }
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
        if (!isGoodsManager(request)) {
            response.setCode(ResponseCode.UNAUTHORIZED);
            response.setData("无权执行该操作：仅管理员或卖家可管理商品");
            return;
        }
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
     * 商品管理权限校验：仅 ADMIN 或 SELLER 允许。
     */
    private boolean isGoodsManager(Message request) {
        UserVO user = userService.queryByUid(request.getUid());
        if (user == null || user.getRole() == null) {
            return false;
        }
        UserRole role = user.getRole();
        return role == UserRole.ADMIN || role == UserRole.SELLER;
    }

    /**
     * 处理商品强制下架：仅管理员允许。
     */
    private void handleGoodsOffShelf(Message request, Message response) {
        if (!isAdmin(request)) {
            response.setCode(ResponseCode.UNAUTHORIZED);
            response.setData("无权执行该操作：仅管理员可强制下架商品");
            return;
        }
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
     * 管理员权限校验。
     */
    private boolean isAdmin(Message request) {
        UserVO user = userService.queryByUid(request.getUid());
        return user != null && user.getRole() == UserRole.ADMIN;
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
     * 将消息负载安全转换为指定长度的字符串数组。
     */
    private String[] toStringArray(Object data, int expectedLength) {
        if (data instanceof String[] && ((String[]) data).length >= expectedLength) {
            return (String[]) data;
        }
        return null;
    }
}
