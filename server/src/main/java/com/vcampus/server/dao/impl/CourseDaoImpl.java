package com.vcampus.server.dao.impl;

import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.ScoreComponentVO;
import com.vcampus.server.dao.CourseDao;
import com.vcampus.server.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程 JDBC 实现。
 *
 * @author xingyi852
 */
public class CourseDaoImpl implements CourseDao {

    private static final String COURSE_COLUMNS = "course_id, course_name, credits, teacher_id, teacher_name, "
            + "max_capacity, current_num, open_semester, time_slot, classroom, start_week, end_week, status";

    /**
     * 按课程代码、名称或教师姓名进行模糊查询。
     */
    @Override
    public List<CourseVO> queryCourses(String keyword) throws SQLException {
        String sql = "SELECT " + COURSE_COLUMNS
                + " FROM tbl_course WHERE course_id LIKE ? OR course_name LIKE ? OR teacher_name LIKE ?";
        List<CourseVO> result = new ArrayList<CourseVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // LIKE 参数统一前后加通配符，便于多字段模糊匹配
            String like = "%" + keyword + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapCourse(rs));
                }
            }
        }
        return result;
    }

    @Override
    public List<CourseVO> listAllCourses() throws SQLException {
        String sql = "SELECT " + COURSE_COLUMNS + " FROM tbl_course ORDER BY course_id";
        List<CourseVO> result = new ArrayList<CourseVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CourseVO course = mapCourse(rs);
                loadScoreComponents(conn, course);
                result.add(course);
            }
        }
        return result;
    }

    @Override
    public List<CourseVO> queryByTeacher(String teacherId) throws SQLException {
        String sql = "SELECT " + COURSE_COLUMNS + " FROM tbl_course WHERE teacher_id = ?";
        List<CourseVO> result = new ArrayList<CourseVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CourseVO course = mapCourse(rs);
                    loadScoreComponents(conn, course);
                    result.add(course);
                }
            }
        }
        return result;
    }

    @Override
    public List<CourseVO> queryBySemester(String semester) throws SQLException {
        String sql = "SELECT " + COURSE_COLUMNS + " FROM tbl_course WHERE open_semester = ?";
        List<CourseVO> result = new ArrayList<CourseVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, semester);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CourseVO course = mapCourse(rs);
                    loadScoreComponents(conn, course);
                    result.add(course);
                }
            }
        }
        return result;
    }

    @Override
    public List<CourseVO> listPendingCourses() throws SQLException {
        String sql = "SELECT " + COURSE_COLUMNS
                + " FROM tbl_course WHERE status = 'PENDING' ORDER BY course_id";
        List<CourseVO> result = new ArrayList<CourseVO>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CourseVO course = mapCourse(rs);
                loadScoreComponents(conn, course);
                result.add(course);
            }
        }
        return result;
    }

    /**
     * 按课程代码精确查询课程。
     */
    @Override
    public CourseVO findByCode(String courseCode) throws SQLException {
        String sql = "SELECT " + COURSE_COLUMNS + " FROM tbl_course WHERE course_id = ?";
        CourseVO course = null;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    course = mapCourse(rs);
                    loadScoreComponents(conn, course);
                }
            }
        }
        return course;
    }

    /**
     * 插入课程时同时写入课程余量和状态。
     */
    @Override
    public boolean insertCourse(CourseVO course) throws SQLException {
        String sql = "INSERT INTO tbl_course(course_id, course_name, credits, teacher_id, "
                + "teacher_name, max_capacity, current_num, open_semester, time_slot, classroom, "
                + "start_week, end_week, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, course.getCourseCode());
                ps.setString(2, course.getCourseName());
                ps.setDouble(3, course.getCredit());
                ps.setString(4, course.getTeacherId());
                ps.setString(5, course.getTeacherName());
                ps.setInt(6, course.getCapacity());
                ps.setInt(7, course.getSelectedCount());
                ps.setString(8, course.getSemester());
                ps.setString(9, course.getClassTime());
                ps.setString(10, course.getLocation());
                ps.setInt(11, course.getStartWeek());
                ps.setInt(12, course.getEndWeek());
                ps.setString(13, course.getStatus());
                ps.executeUpdate();
            }

            insertScoreComponents(conn, course);
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * 更新课程信息。
     */
    @Override
    public boolean updateCourse(CourseVO course) throws SQLException {
        String sql = "UPDATE tbl_course SET course_name=?, credits=?, teacher_id=?, teacher_name=?, "
                + "max_capacity=?, current_num=?, open_semester=?, time_slot=?, classroom=?, "
                + "start_week=?, end_week=?, status=? "
                + "WHERE course_id=?";

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, course.getCourseName());
                ps.setDouble(2, course.getCredit());
                ps.setString(3, course.getTeacherId());
                ps.setString(4, course.getTeacherName());
                ps.setInt(5, course.getCapacity());
                ps.setInt(6, course.getSelectedCount());
                ps.setString(7, course.getSemester());
                ps.setString(8, course.getClassTime());
                ps.setString(9, course.getLocation());
                ps.setInt(10, course.getStartWeek());
                ps.setInt(11, course.getEndWeek());
                ps.setString(12, course.getStatus());
                ps.setString(13, course.getCourseCode());
                ps.executeUpdate();
            }

            try (PreparedStatement deletePs = conn.prepareStatement(
                    "DELETE FROM tbl_course_score_component WHERE course_id = ?")) {
                deletePs.setString(1, course.getCourseCode());
                deletePs.executeUpdate();
            }

            insertScoreComponents(conn, course);
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * 通过 SQL 原子加减已选人数，避免并发选课时出现超选。
     */
    @Override
    public boolean updateSelectedCount(String courseCode, int delta) throws SQLException {
        String sql = "UPDATE tbl_course SET current_num = current_num + ? WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setString(2, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将课程状态改为 DISABLED。
     */
    @Override
    public boolean disableCourse(String courseCode) throws SQLException {
        String sql = "UPDATE tbl_course SET status = 'DISABLED' WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 删除课程，关联的成绩组成、选课记录与成绩会随外键级联删除。
     */
    @Override
    public boolean deleteCourse(String courseCode) throws SQLException {
        String sql = "DELETE FROM tbl_course WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean approveCourse(String courseCode) throws SQLException {
        String sql = "UPDATE tbl_course SET status = 'ACTIVE' WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean rejectCourse(String courseCode) throws SQLException {
        String sql = "UPDATE tbl_course SET status = 'DISABLED' WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新课程上课时间，供教务老师统一排课。
     */
    @Override
    public boolean updateCourseTime(String courseCode, String classTime) throws SQLException {
        String sql = "UPDATE tbl_course SET time_slot = ? WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, classTime);
            ps.setString(2, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 更新课程起止周次，供教务老师安排周次。
     */
    @Override
    public boolean updateCourseWeeks(String courseCode, int startWeek, int endWeek) throws SQLException {
        String sql = "UPDATE tbl_course SET start_week = ?, end_week = ? WHERE course_id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, startWeek);
            ps.setInt(2, endWeek);
            ps.setString(3, courseCode);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 将 ResultSet 当前行转换为 CourseVO。
     */
    private CourseVO mapCourse(ResultSet rs) throws SQLException {
        CourseVO course = new CourseVO();
        course.setCourseCode(rs.getString("course_id"));
        course.setCourseName(rs.getString("course_name"));
        course.setCredit(rs.getDouble("credits"));
        course.setTeacherId(rs.getString("teacher_id"));
        course.setTeacherName(rs.getString("teacher_name"));
        course.setCapacity(rs.getInt("max_capacity"));
        course.setSelectedCount(rs.getInt("current_num"));
        course.setSemester(rs.getString("open_semester"));
        String scheduleText = rs.getString("time_slot");
        course.setClassTime(scheduleText);
        course.setLocation(rs.getString("classroom"));
        course.setStartWeek(rs.getInt("start_week"));
        course.setEndWeek(rs.getInt("end_week"));
        course.parseScheduleText(scheduleText);
        course.setStatus(rs.getString("status"));
        return course;
    }

    private void insertScoreComponents(Connection conn, CourseVO course) throws SQLException {
        if (course.getScoreComponents() == null || course.getScoreComponents().isEmpty()) {
            return;
        }
        String sql = "INSERT INTO tbl_course_score_component(course_id, component_name, weight) "
                + "VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ScoreComponentVO component : course.getScoreComponents()) {
                ps.setString(1, course.getCourseCode());
                ps.setString(2, component.getComponentName());
                ps.setDouble(3, component.getWeight());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadScoreComponents(Connection conn, CourseVO course) throws SQLException {
        String sql = "SELECT component_name, weight FROM tbl_course_score_component WHERE course_id = ?";
        List<ScoreComponentVO> components = new ArrayList<ScoreComponentVO>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, course.getCourseCode());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    components.add(new ScoreComponentVO(
                            rs.getString("component_name"),
                            rs.getDouble("weight")));
                }
            }
        }
        course.setScoreComponents(components);
    }
}
