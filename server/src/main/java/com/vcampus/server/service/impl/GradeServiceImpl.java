package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.GradeVO;
import com.vcampus.server.dao.GradeDao;
import com.vcampus.server.dao.impl.GradeDaoImpl;
import com.vcampus.server.service.GradeService;

import java.sql.SQLException;
import java.util.List;

/**
 * 成绩管理业务实现。
 */
public class GradeServiceImpl implements GradeService {

    private final GradeDao gradeDao = new GradeDaoImpl();

    /**
     * 录入成绩：重复提交同一课程成绩时返回 GRADE_ALREADY_EXISTS。
     */
    @Override
    public ResponseCode submitGrade(GradeVO grade) {
        try {
            // 学生和课程代码是成绩记录的必要信息
            if (grade == null || grade.getStudentId() == null || grade.getCourseCode() == null) {
                return ResponseCode.INVALID_REQUEST;
            }
            // 同一学生同一课程只允许存在一条成绩
            if (gradeDao.exists(grade.getStudentId(), grade.getCourseCode())) {
                return ResponseCode.GRADE_ALREADY_EXISTS;
            }
            // 未指定状态时默认标记为已提交
            if (grade.getStatus() == null || grade.getStatus().trim().length() == 0) {
                grade.setStatus("SUBMITTED");
            }
            return gradeDao.insert(grade) ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }

    /**
     * 查询学生成绩。
     */
    @Override
    public List<GradeVO> queryByStudent(String studentId) {
        try {
            return gradeDao.listByStudent(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("查询学生成绩失败", e);
        }
    }

    /**
     * 查询课程成绩，用于教师/教务统计。
     */
    @Override
    public List<GradeVO> queryByCourse(String courseCode) {
        try {
            return gradeDao.listByCourse(courseCode);
        } catch (SQLException e) {
            throw new RuntimeException("查询课程成绩失败", e);
        }
    }
}
