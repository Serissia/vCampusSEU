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
     * 查询全部课程。
     */
    List<CourseVO> listAllCourses();

    /**
     * 查询指定教师的课程。
     */
    List<CourseVO> queryByTeacher(String teacherId);

    /**
     * 查询指定学期的课程。
     */
    List<CourseVO> queryBySemester(String semester);

    /**
     * 查询待审核课程。
     */
    List<CourseVO> listPendingCourses();

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

    /**
     * 删除课程。
     */
    boolean deleteCourse(String courseCode);

    /**
     * 审核通过课程。
     */
    boolean approveCourse(String courseCode);

    /**
     * 驳回课程。
     */
    boolean rejectCourse(String courseCode);

    /**
     * 教务老师安排或修改课程上课时间。
     */
    boolean scheduleCourseTime(String courseCode, String classTime);

    /**
     * 教务老师安排或修改课程起止周次。
     */
    boolean scheduleCourseWeeks(String courseCode, int startWeek, int endWeek);

    /**
     * 教务老师安排或修改课程上课地点。
     */
    boolean scheduleCourseLocation(String courseCode, String location);
}
