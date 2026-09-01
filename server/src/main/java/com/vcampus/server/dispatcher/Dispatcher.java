package com.vcampus.server.dispatcher;

import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.GradeVO;
import com.vcampus.common.vo.UserVO;
import com.vcampus.server.service.CourseSelectionService;
import com.vcampus.server.service.CourseService;
import com.vcampus.server.service.GradeService;
import com.vcampus.server.service.UserService;
import com.vcampus.server.service.impl.CourseSelectionServiceImpl;
import com.vcampus.server.service.impl.CourseServiceImpl;
import com.vcampus.server.service.impl.GradeServiceImpl;
import com.vcampus.server.service.impl.UserServiceImpl;

import java.math.BigDecimal;

/**
 * 服务端消息路由与业务调度中心。
 *
 * @author vCampus Team
 */
public class Dispatcher {

    private final CourseService courseService = new CourseServiceImpl();
    private final CourseSelectionService selectionService = new CourseSelectionServiceImpl();
    private final GradeService gradeService = new GradeServiceImpl();
    private final UserService userService = new UserServiceImpl();

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
                case COURSE_QUERY:
                    response.setData(courseService.queryCourses(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
                    break;
                case HEARTBEAT:
                    response.setCode(ResponseCode.SUCCESS);
                    response.setData("pong");
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
                case GRADE_STATISTICS:
                    response.setData(gradeService.queryByCourse(String.valueOf(request.getData())));
                    response.setCode(ResponseCode.SUCCESS);
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
}