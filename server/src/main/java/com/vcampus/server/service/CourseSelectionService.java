package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseVO;

import java.util.List;

/**
 * 选退课业务接口。
 */
public interface CourseSelectionService {

    /**
     * 学生选课，返回具体业务状态码。
     */
    ResponseCode selectCourse(String studentId, String courseCode);

    /**
     * 学生退课，返回具体业务状态码。
     */
    ResponseCode dropCourse(String studentId, String courseCode);

    /**
     * 查询学生已选课程。
     */
    List<CourseVO> listMyCourses(String studentId);
}
