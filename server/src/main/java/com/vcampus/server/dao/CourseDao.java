package com.vcampus.server.dao;

import com.vcampus.common.vo.CourseVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 课程数据访问接口。
 */
public interface CourseDao {

    /**
     * 根据关键字模糊查询课程。
     */
    List<CourseVO> queryCourses(String keyword) throws SQLException;

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
}
