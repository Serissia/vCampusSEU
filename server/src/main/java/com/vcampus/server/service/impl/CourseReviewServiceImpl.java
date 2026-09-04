package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseReviewVO;
import com.vcampus.server.dao.CourseReviewDao;
import com.vcampus.server.dao.impl.CourseReviewDaoImpl;

import java.sql.SQLException;
import java.util.List;

/**
 * 课程评价业务实现。
 */
public class CourseReviewServiceImpl {

    private final CourseReviewDao reviewDao = new CourseReviewDaoImpl();

    public ResponseCode submit(CourseReviewVO review) {
        try {
            if (review == null || review.getStudentId() == null || review.getCourseId() == null
                    || review.getRating() < 1 || review.getRating() > 5) {
                return ResponseCode.INVALID_REQUEST;
            }
            String comment = review.getComment() == null ? "" : review.getComment().trim();
            review.setComment(comment);
            return reviewDao.submit(review) ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }

    public List<CourseReviewVO> listByCourse(String courseId) {
        try {
            return reviewDao.listByCourse(courseId);
        } catch (SQLException e) {
            throw new RuntimeException("查询课程评价失败", e);
        }
    }

    public ResponseCode delete(String studentId, String courseId) {
        try {
            if (studentId == null || courseId == null || courseId.trim().isEmpty()) {
                return ResponseCode.INVALID_REQUEST;
            }
            return reviewDao.delete(studentId, courseId.trim())
                    ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }
}
