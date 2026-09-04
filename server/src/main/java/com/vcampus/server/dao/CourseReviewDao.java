package com.vcampus.server.dao;

import com.vcampus.common.vo.CourseReviewVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 课程评价数据访问接口。
 */
public interface CourseReviewDao {
    boolean submit(CourseReviewVO review) throws SQLException;
    List<CourseReviewVO> listByCourse(String courseId) throws SQLException;
    boolean delete(String studentId, String courseId) throws SQLException;
}
