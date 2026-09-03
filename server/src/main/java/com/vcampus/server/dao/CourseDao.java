package com.vcampus.server.dao;

import com.vcampus.common.vo.CourseVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 课程数据访问接口。
 *
 * @author xingyi852
 */
public interface CourseDao {

    /**
     * 根据关键字模糊查询课程。
     */
    List<CourseVO> queryCourses(String keyword) throws SQLException;

    /**
     * 查询全部课程。
     */
    List<CourseVO> listAllCourses() throws SQLException;

    /**
     * 查询指定教师的课程。
     */
    List<CourseVO> queryByTeacher(String teacherId) throws SQLException;

    /**
     * 查询指定学期的课程。
     */
    List<CourseVO> queryBySemester(String semester) throws SQLException;

    /**
     * 查询待审核课程。
     */
    List<CourseVO> listPendingCourses() throws SQLException;

    /**
     * 根据课程代码查询单个课程。
     */
    CourseVO findByCode(String courseCode) throws SQLException;

    /**
     * 新增课程。
     */
    boolean insertCourse(CourseVO course) throws SQLException;

    /**
     * 更新课程。
     */
    boolean updateCourse(CourseVO course) throws SQLException;

    /**
     * 更新课程已选人数，选课增加、退课减少。
     */
    boolean updateSelectedCount(String courseCode, int delta) throws SQLException;

    /**
     * 停开课程。
     */
    boolean disableCourse(String courseCode) throws SQLException;

    /**
     * 删除课程。
     */
    boolean deleteCourse(String courseCode) throws SQLException;

    /**
     * 审核通过课程。
     */
    boolean approveCourse(String courseCode) throws SQLException;

    /**
     * 驳回课程。
     */
    boolean rejectCourse(String courseCode) throws SQLException;

    /**
     * 更新课程上课时间。
     */
    boolean updateCourseTime(String courseCode, String classTime) throws SQLException;

    /**
     * 更新课程起止周次。
     */
    boolean updateCourseWeeks(String courseCode, int startWeek, int endWeek) throws SQLException;
}
