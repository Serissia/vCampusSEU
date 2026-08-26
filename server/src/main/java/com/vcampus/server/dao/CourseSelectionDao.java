package com.vcampus.server.dao;

import com.vcampus.common.vo.CourseSelectionVO;
import com.vcampus.common.vo.CourseVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 选课数据访问接口。
 *
 * @author xingyi852
 */
public interface CourseSelectionDao {

    /**
     * 新增选课记录。
     */
    boolean insert(CourseSelectionVO selection) throws SQLException;

    /**
     * 删除选课记录。
     */
    boolean delete(String studentId, String courseCode) throws SQLException;

    /**
     * 判断学生是否已经选择该课程。
     */
    boolean exists(String studentId, String courseCode) throws SQLException;

    /**
     * 查询学生已选课程列表。
     */
    List<CourseVO> listByStudent(String studentId) throws SQLException;
}
