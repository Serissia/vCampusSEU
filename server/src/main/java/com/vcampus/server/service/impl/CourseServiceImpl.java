package com.vcampus.server.service.impl;

import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.ScoreComponentVO;
import com.vcampus.server.dao.CourseDao;
import com.vcampus.server.dao.impl.CourseDaoImpl;
import com.vcampus.server.service.CourseService;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

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

    @Override
    public List<CourseVO> listAllCourses() {
        try {
            return courseDao.listAllCourses();
        } catch (SQLException e) {
            throw new RuntimeException("查询全部课程失败", e);
        }
    }

    @Override
    public List<CourseVO> queryByTeacher(String teacherId) {
        try {
            return courseDao.queryByTeacher(teacherId);
        } catch (SQLException e) {
            throw new RuntimeException("查询教师课程失败", e);
        }
    }

    @Override
    public List<CourseVO> queryBySemester(String semester) {
        try {
            return courseDao.queryBySemester(semester);
        } catch (SQLException e) {
            throw new RuntimeException("查询学期课程失败", e);
        }
    }

    @Override
    public List<CourseVO> listPendingCourses() {
        try {
            return courseDao.listPendingCourses();
        } catch (SQLException e) {
            throw new RuntimeException("查询待审核课程失败", e);
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
            String displayCode = course.getCourseCode().trim();
            course.setDisplayCode(displayCode);
            course.setCourseCode(UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            // 未指定状态时默认按待审核处理
            if (course.getStatus() == null || course.getStatus().trim().length() == 0) {
                course.setStatus(CourseVO.STATUS_PENDING);
            }
            // 上课时间由教务老师统一安排，教师提交课程时允许为空。
            if (course.getClassTime() == null) {
                course.setClassTime("");
            }
            if (course.getTimeSlots() != null && !course.getTimeSlots().isEmpty()) {
                course.setClassTime(course.toScheduleText());
            }
            if (course.getNature() == null || course.getNature().trim().isEmpty()) {
                course.setNature("选修");
            }
            if (!isValidScoreComponents(course)) {
                return false;
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
            if (course != null && course.getClassTime() == null) {
                course.setClassTime("");
            }
            if (course != null && course.getTimeSlots() != null && !course.getTimeSlots().isEmpty()) {
                course.setClassTime(course.toScheduleText());
            }
            if (course != null && (course.getNature() == null || course.getNature().trim().isEmpty())) {
                course.setNature("选修");
            }
            return course != null && isValidScoreComponents(course) && courseDao.updateCourse(course);
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

    /**
     * 删除课程。
     */
    @Override
    public boolean deleteCourse(String courseCode) {
        try {
            return courseCode != null && !courseCode.trim().isEmpty()
                    && courseDao.deleteCourse(courseCode.trim());
        } catch (SQLException e) {
            throw new RuntimeException("删除课程失败", e);
        }
    }

    @Override
    public boolean approveCourse(String courseCode) {
        try {
            CourseVO course = courseDao.findByCode(courseCode);
            if (course == null || !isValidScoreComponents(course)) {
                return false;
            }
            return courseDao.approveCourse(courseCode);
        } catch (SQLException e) {
            throw new RuntimeException("审核通过课程失败", e);
        }
    }

    @Override
    public boolean rejectCourse(String courseCode) {
        try {
            return courseCode != null && courseDao.rejectCourse(courseCode);
        } catch (SQLException e) {
            throw new RuntimeException("驳回课程失败", e);
        }
    }

    /**
     * 教务老师安排或修改上课时间。
     */
    @Override
    public boolean scheduleCourseTime(String courseCode, String classTime) {
        try {
            if (courseCode == null || courseCode.trim().isEmpty()
                    || classTime == null || classTime.trim().isEmpty()) {
                return false;
            }
            return courseDao.updateCourseTime(courseCode.trim(), classTime.trim());
        } catch (SQLException e) {
            throw new RuntimeException("安排课程时间失败", e);
        }
    }

    /**
     * 教务老师安排或修改课程起止周次。
     */
    @Override
    public boolean scheduleCourseWeeks(String courseCode, int startWeek, int endWeek) {
        try {
            if (courseCode == null || courseCode.trim().isEmpty()) {
                return false;
            }
            CourseVO course = courseDao.findByCode(courseCode.trim());
            if (course == null) {
                return false;
            }
            int maxWeek = getMaxWeek(course.getSemester());
            if (startWeek < 1 || endWeek < startWeek || endWeek > maxWeek) {
                return false;
            }
            return courseDao.updateCourseWeeks(courseCode.trim(), startWeek, endWeek);
        } catch (SQLException e) {
            throw new RuntimeException("安排课程周次失败", e);
        }
    }

    /**
     * 教务老师安排或修改课程上课地点。
     */
    @Override
    public boolean scheduleCourseLocation(String courseCode, String location) {
        try {
            if (courseCode == null || courseCode.trim().isEmpty()
                    || location == null || location.trim().isEmpty()) {
                return false;
            }
            return courseDao.updateCourseLocation(courseCode.trim(), location.trim());
        } catch (SQLException e) {
            throw new RuntimeException("安排课程地点失败", e);
        }
    }

    /**
     * 校验课程成绩组成是否合法：非空、名称非空、权重大于 0、权重总和为 1。
     */
    private boolean isValidScoreComponents(CourseVO course) {
        if (course.getScoreComponents() == null || course.getScoreComponents().isEmpty()) {
            return false;
        }
        double total = 0.0;
        for (ScoreComponentVO component : course.getScoreComponents()) {
            if (component.getComponentName() == null
                    || component.getComponentName().trim().length() == 0
                    || component.getWeight() <= 0) {
                return false;
            }
            total += component.getWeight();
        }
        return Math.abs(total - 1.0) < 0.0001;
    }

    /**
     * 根据学期字段解析学期序号，并返回该学期允许的最大周数。
     * 第 1 学期为 4 周，第 2、3 学期为 18 周。
     */
    private int getMaxWeek(String semester) {
        int semesterNo = parseSemesterNo(semester);
        return semesterNo == 1 ? 4 : 18;
    }

    /**
     * 从“2026-2027-1”或“2026-2027学年 第1学期”等格式中解析学期序号。
     */
    private int parseSemesterNo(String semester) {
        if (semester == null || semester.trim().isEmpty()) {
            return 0;
        }
        String[] numbers = semester.replaceAll("[^0-9]+", " ").trim().split("\\s+");
        if (numbers.length >= 3) {
            try {
                return Integer.parseInt(numbers[2]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
