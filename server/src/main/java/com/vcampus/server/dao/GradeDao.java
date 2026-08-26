package com.vcampus.server.dao;

import com.vcampus.common.vo.GradeVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 成绩数据访问接口。
 *
 * @author xingyi852
 */
public interface GradeDao {

    /**
     * 新增成绩记录。
     */
    boolean insert(GradeVO grade) throws SQLException;

    /**
     * 更新成绩记录。
     */
    boolean update(GradeVO grade) throws SQLException;

    /**
     * 判断学生某门课程成绩是否已存在。
     */
    boolean exists(String studentId, String courseCode) throws SQLException;

    /**
     * 查询学生全部成绩。
     */
    List<GradeVO> listByStudent(String studentId) throws SQLException;

    /**
     * 查询课程全部成绩。
     */
    List<GradeVO> listByCourse(String courseCode) throws SQLException;
}
