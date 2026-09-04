package com.vcampus.client.controller;

import com.vcampus.client.net.SocketClient;
import com.vcampus.common.message.Message;
import com.vcampus.common.message.MessageType;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.CourseReviewVO;
import com.vcampus.common.vo.GradeStatisticsVO;
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

    public List<CourseVO> listAllCourses() {
        Message request = new Message(uid, MessageType.COURSE_LIST_ALL, null, null);
        return toCourseList(send(request));
    }

    /**
     * 按教师工号查询课程。
     */
    public List<CourseVO> queryByTeacher(String teacherId) {
        Message request = new Message(uid, MessageType.COURSE_QUERY_BY_TEACHER, null, teacherId);
        return toCourseList(send(request));
    }

    /**
     * 按开课学期查询课程。
     */
    public List<CourseVO> queryBySemester(String semester) {
        Message request = new Message(uid, MessageType.COURSE_QUERY_BY_SEMESTER, null, semester);
        return toCourseList(send(request));
    }

    /**
     * 查询待教务审核的课程列表。
     */
    public List<CourseVO> listPendingCourses() {
        Message request = new Message(uid, MessageType.COURSE_PENDING_LIST, null, null);
        return toCourseList(send(request));
    }

    /**
     * 审核通过课程。
     */
    public ResponseCode approveCourse(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_APPROVE, null, courseCode);
        return send(request).getCode();
    }

    /**
     * 新增课程并提交审批。
     */
    public ResponseCode addCourse(CourseVO course) {
        Message request = new Message(uid, MessageType.COURSE_ADD, null, course);
        return send(request).getCode();
    }

    /**
     * 更新课程信息。
     */
    public ResponseCode updateCourse(CourseVO course) {
        Message request = new Message(uid, MessageType.COURSE_UPDATE, null, course);
        return send(request).getCode();
    }

    /**
     * 停开指定课程。
     */
    public ResponseCode disableCourse(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_DISABLE, null, courseCode);
        return send(request).getCode();
    }

    /**
     * 删除指定课程。
     */
    public ResponseCode deleteCourse(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_DELETE, null, courseCode);
        return send(request).getCode();
    }

    public ResponseCode rejectCourse(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_REJECT, null, courseCode);
        return send(request).getCode();
    }

    /**
     * 教务老师安排或修改课程上课时间。
     */
    public ResponseCode scheduleCourseTime(String courseCode, String classTime) {
        Message request = new Message(uid, MessageType.COURSE_SCHEDULE, null,
                new String[]{courseCode, classTime});
        return send(request).getCode();
    }

    /**
     * 教务老师安排或修改课程起止周次。
     */
    public ResponseCode scheduleCourseWeeks(String courseCode, int startWeek, int endWeek) {
        Message request = new Message(uid, MessageType.COURSE_WEEK_SCHEDULE, null,
                new String[]{courseCode, String.valueOf(startWeek), String.valueOf(endWeek)});
        return send(request).getCode();
    }

    /**
     * 教务老师安排或修改课程上课地点。
     */
    public ResponseCode scheduleCourseLocation(String courseCode, String location) {
        Message request = new Message(uid, MessageType.COURSE_LOCATION_SCHEDULE, null,
                new String[]{courseCode, location});
        return send(request).getCode();
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
     * 查询指定课程下所有学生的成绩。
     */
    public List<GradeVO> queryCourseGrades(String courseCode) {
        Message request = new Message(uid, MessageType.GRADE_QUERY_BY_COURSE, null, courseCode);
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
     * 查询指定课程的成绩统计结果。
     */
    public GradeStatisticsVO getCourseStatistics(String courseCode) {
        Message request = new Message(uid, MessageType.GRADE_STATISTICS, null, courseCode);
        Message response = send(request);
        if (response.getCode() == ResponseCode.SUCCESS
                && response.getData() instanceof GradeStatisticsVO) {
            return (GradeStatisticsVO) response.getData();
        }
        return null;
    }

    /**
     * 提交课程评价。
     */
    public ResponseCode submitReview(CourseReviewVO review) {
        Message request = new Message(uid, MessageType.COURSE_REVIEW_SUBMIT, null, review);
        return send(request).getCode();
    }

    /**
     * 查询课程评价列表。
     */
    public List<CourseReviewVO> listReviews(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_REVIEW_LIST, null, courseCode);
        Message response = send(request);
        if (response.getCode() != ResponseCode.SUCCESS || !(response.getData() instanceof List)) {
            return new ArrayList<CourseReviewVO>();
        }
        List<CourseReviewVO> result = new ArrayList<CourseReviewVO>();
        for (Object item : (List<?>) response.getData()) {
            if (item instanceof CourseReviewVO) {
                result.add((CourseReviewVO) item);
            }
        }
        return result;
    }

    /**
     * 删除当前学生自己的课程评价。
     */
    public ResponseCode deleteReview(String courseCode) {
        Message request = new Message(uid, MessageType.COURSE_REVIEW_DELETE, null, courseCode);
        return send(request).getCode();
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
