package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.GradeVO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 教务模块客户端控制器。
 *
 * @author xingyi852
 */
public class AcademicController {

    private final SocketClient socketClient;
    private String uid;

    public AcademicController(SocketClient socketClient) {
        this.socketClient = socketClient;
    }

    /**
     * 登录后设置当前用户账号，后续教务请求都会携带该 uid。
     */
    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * 按关键字查询课程列表。
     */
    public List<CourseVO> queryCourses(String keyword) {
        Message request = new Message(uid, MessageType.COURSE_QUERY, null, keyword);
        Message response = send(request);
        if (response.getCode() != ResponseCode.SUCCESS || !(response.getData() instanceof List)) {
            return new ArrayList<CourseVO>();
        }
        List<CourseVO> result = new ArrayList<CourseVO>();
        for (Object item : (List<?>) response.getData()) {
            if (item instanceof CourseVO) {
                result.add((CourseVO) item);
            }
        }
        return result;
    }

    /**
     * 请求服务端执行选课。
     */
    public ResponseCode selectCourse(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_SELECT, null, courseCode);
        return send(request).getCode();
    }

    /**
     * 请求服务端执行退课。
     */
    public ResponseCode dropCourse(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_DROP, null, courseCode);
        return send(request).getCode();
    }

    /**
     * 查询当前学生已选课程。
     */
    public List<CourseVO> listMyCourses() {
        Message request = new Message(uid, MessageType.COURSE_TIMETABLE, null, null);
        Message response = send(request);
        return toCourseList(response);
    }

    /**
     * 提交某学生的课程成绩。
     */
    public ResponseCode submitGrade(GradeVO grade) {
        Message request = new Message(uid, MessageType.GRADE_SUBMIT, null, grade);
        return send(request).getCode();
    }

    /**
     * 查询当前学生成绩。
     */
    public List<GradeVO> queryMyGrades() {
        Message request = new Message(uid, MessageType.GRADE_QUERY, null, null);
        Message response = send(request);
        if (response.getCode() != ResponseCode.SUCCESS || !(response.getData() instanceof List)) {
            return new ArrayList<GradeVO>();
        }
        List<GradeVO> result = new ArrayList<GradeVO>();
        for (Object item : (List<?>) response.getData()) {
            if (item instanceof GradeVO) {
                result.add((GradeVO) item);
            }
        }
        return result;
    }

    /**
     * 统一发送教务请求。
     */
    private Message send(Message request) {
        try {
            return socketClient.send(request);
        } catch (IOException e) {
            throw new RuntimeException("无法连接教务服务端", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("服务端返回数据无法识别", e);
        }
    }

    /**
     * 将响应负载安全转换为课程列表。
     */
    private List<CourseVO> toCourseList(Message response) {
        if (response.getCode() != ResponseCode.SUCCESS || !(response.getData() instanceof List)) {
            return new ArrayList<CourseVO>();
        }
        List<CourseVO> result = new ArrayList<CourseVO>();
        for (Object item : (List<?>) response.getData()) {
            if (item instanceof CourseVO) {
                result.add((CourseVO) item);
            }
        }
        return result;
    }
}
