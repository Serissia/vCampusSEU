package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.GradeVO;

import java.util.List;

/**
 * 成绩管理业务接口。
 */
public interface GradeService {

    /**
     * 提交某学生某门课程的成绩。
     */
    ResponseCode submitGrade(GradeVO grade);

    /**
     * 查询某学生的全部成绩。
     */
    List<GradeVO> queryByStudent(String studentId);

    /**
     * 查询某课程下所有学生的成绩。
     */
    List<GradeVO> queryByCourse(String courseCode);
}
