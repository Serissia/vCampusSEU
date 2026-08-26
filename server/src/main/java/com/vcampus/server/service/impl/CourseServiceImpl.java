package com.vcampus.server.service.impl;

import com.vcampus.common.vo.CourseVO;
import com.vcampus.server.dao.CourseDao;
import com.vcampus.server.dao.impl.CourseDaoImpl;
import com.vcampus.server.service.CourseService;

import java.sql.SQLException;
import java.util.List;

/**
 * 课程管理业务实现。
 *
 * @author xingyi852
 */
public class CourseServiceImpl implements CourseService {

    private final CourseDao courseDao = new CourseDaoImpl();

    /**
     * 查询课程，数据库异常统一转换为运行时异常交由 Dispatcher 处理。
     */
    @Override
    public List<CourseVO> queryCourses(String keyword) {
        try {
            return courseDao.queryCourses(keyword);
        } catch (SQLException e) {
            throw new RuntimeException("查询课程失败", e);
        }
    }

    /**
     * 新增课程，并在状态为空时补默认状态。
     */
    @Override
    public boolean addCourse(CourseVO course) {
        try {
            // 课程代码作为业务主键，不能为空
            if (course == null || course.getCourseCode() == null
                    || course.getCourseCode().trim().length() == 0) {
                return false;
            }
            // 未指定状态时默认按开课处理
            if (course.getStatus() == null || course.getStatus().trim().length() == 0) {
                course.setStatus("ACTIVE");
            }
            return courseDao.insertCourse(course);
        } catch (SQLException e) {
            throw new RuntimeException("新增课程失败", e);
        }
    }

    /**
     * 更新课程。
     */
    @Override
    public boolean updateCourse(CourseVO course) {
        try {
            return course != null && courseDao.updateCourse(course);
        } catch (SQLException e) {
            throw new RuntimeException("更新课程失败", e);
        }
    }

    /**
     * 停开课程。
     */
    @Override
    public boolean disableCourse(String courseCode) {
        try {
            return courseCode != null && courseDao.disableCourse(courseCode);
        } catch (SQLException e) {
            throw new RuntimeException("停开课程失败", e);
        }
    }
}
