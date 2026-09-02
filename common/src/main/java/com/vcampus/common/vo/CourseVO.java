package com.vcampus.common.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程信息值对象。
 *
 * @author xingyi852
 */
public class CourseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String STATUS_PENDING = "PENDING";

    /** 课程代码 */
    private String courseCode;
    /** 课程名称 */
    private String courseName;
    /** 学分 */
    private double credit;
    /** 任课教师工号 */
    private String teacherId;
    /** 任课教师姓名 */
    private String teacherName;
    /** 课程容量 */
    private int capacity;
    /** 已选人数 */
    private int selectedCount;
    /** 开课学期 */
    private String semester;
    /** 上课时间 */
    private String classTime;
    /** 多个上课时间段 */
    private List<CourseTimeSlotVO> timeSlots = new ArrayList<CourseTimeSlotVO>();
    /** 起始周次 */
    private int startWeek;
    /** 结束周次 */
    private int endWeek;
    /** 上课教室 */
    private String location;
    /** 成绩组成项及权重 */
    private List<ScoreComponentVO> scoreComponents = new ArrayList<ScoreComponentVO>();
    /**
     * 课程状态。
     *
     * <p>该字段描述课程当前所处的业务生命周期，服务端在选课、课程目录展示
     * 与停开课操作时都会读取它。当前约定取值如下：</p>
     *
     * <ul>
     *   <li>{@code ACTIVE}：正常开课，允许学生查询和选课；</li>
     *   <li>{@code DISABLED}：已停开，不允许再选课，但历史数据仍然保留；</li>
     *   <li>{@code PENDING}：待审核或待发布状态，可用于后续扩展排课审批流程。</li>
     * </ul>
     *
     * <p>典型转换流程：</p>
     *
     * <pre>
     * PENDING --审核通过--> ACTIVE --停开--> DISABLED
     * </pre>
     *
     * <p>注意：当前业务代码中已经用 {@code "ACTIVE"} 和 {@code "DISABLED"}
     * 字符串进行比较，因此修改状态值时需要同时检查
     * {@code CourseSelectionServiceImpl} 与相关 SQL 逻辑。</p>
     */
    private String status;

    public CourseVO() {
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public double getCredit() {
        return credit;
    }

    public void setCredit(double credit) {
        this.credit = credit;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getSelectedCount() {
        return selectedCount;
    }

    public void setSelectedCount(int selectedCount) {
        this.selectedCount = selectedCount;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getClassTime() {
        return classTime;
    }

    public void setClassTime(String classTime) {
        this.classTime = classTime;
    }

    public List<CourseTimeSlotVO> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<CourseTimeSlotVO> timeSlots) {
        this.timeSlots = timeSlots;
    }

    public int getStartWeek() {
        return startWeek;
    }

    public void setStartWeek(int startWeek) {
        this.startWeek = startWeek;
    }

    public int getEndWeek() {
        return endWeek;
    }

    public void setEndWeek(int endWeek) {
        this.endWeek = endWeek;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<ScoreComponentVO> getScoreComponents() {
        return scoreComponents;
    }

    public void setScoreComponents(List<ScoreComponentVO> scoreComponents) {
        this.scoreComponents = scoreComponents;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 将多个上课时间段序列化为 time_slot 字段可存储的字符串。
     */
    public String toScheduleText() {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CourseTimeSlotVO slot : timeSlots) {
            if (slot == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(";");
            }
            sb.append(slot.getStartWeek()).append("-").append(slot.getEndWeek()).append("周 ")
                    .append(slot.getDay()).append(" 第")
                    .append(slot.getStartPeriod()).append("-")
                    .append(slot.getEndPeriod()).append("节");
        }
        return sb.toString();
    }

    /**
     * 从 time_slot 字符串解析多个上课时间段。
     */
    public void parseScheduleText(String scheduleText) {
        timeSlots = new ArrayList<CourseTimeSlotVO>();
        if (scheduleText == null || scheduleText.trim().isEmpty()) {
            return;
        }
        String[] segments = scheduleText.split(";");
        for (String segment : segments) {
            CourseTimeSlotVO slot = parseTimeSlot(segment);
            if (slot != null) {
                timeSlots.add(slot);
            }
        }
    }

    private CourseTimeSlotVO parseTimeSlot(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String[] numbers = text.replaceAll("[^0-9]+", " ").trim().split("\\s+");
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String day = null;
        for (String candidate : days) {
            if (text.contains(candidate)) {
                day = candidate;
                break;
            }
        }
        if (day == null || numbers.length < 2) {
            return null;
        }
        try {
            if (numbers.length >= 4) {
                int startWeek = Integer.parseInt(numbers[0]);
                int endWeek = Integer.parseInt(numbers[1]);
                int startPeriod = Integer.parseInt(numbers[2]);
                int endPeriod = Integer.parseInt(numbers[3]);
                return new CourseTimeSlotVO(startWeek, endWeek, day, startPeriod, endPeriod);
            }
            if (numbers.length >= 2 && startWeek > 0 && endWeek > 0) {
                int startPeriod = Integer.parseInt(numbers[0]);
                int endPeriod = Integer.parseInt(numbers[1]);
                return new CourseTimeSlotVO(this.startWeek, this.endWeek, day, startPeriod, endPeriod);
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
