package com.vcampus.server.service;

import com.vcampus.common.vo.CourseVO;

import java.util.List;

/**
 * 课程管理业务接口。
 *
 * @author xingyi852
 */
public interface CourseService {

    /**
     * 按关键字查询课程。
     */
    List<CourseVO> queryCourses(String keyword);

    /**
     * 新增课程。
     */
    boolean addCourse(CourseVO course);

    /**
     * 更新课程。
     */
    boolean updateCourse(CourseVO course);

    /**
     * 停开课程。
     */
    boolean disableCourse(String courseCode);
}
