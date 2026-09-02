package com.vcampus.common.vo;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 课程成绩统计结果。
 */
public class GradeStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 课程代码 */
    private String courseCode;
    /** 课程名称 */
    private String courseName;
    /** 参与统计的学生人数 */
    private int studentCount;
    /** 平均分 */
    private double averageScore;
    /** 最高分 */
    private double maxScore;
    /** 最低分 */
    private double minScore;
    /** 及格率 */
    private double passRate;
    /** 分数段分布：分数段 -> 人数 */
    private Map<Integer, Integer> scoreDistribution = new HashMap<Integer, Integer>();

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

    public int getStudentCount() {
        return studentCount;
    }

    public void setStudentCount(int studentCount) {
        this.studentCount = studentCount;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(double maxScore) {
        this.maxScore = maxScore;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public double getPassRate() {
        return passRate;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    public Map<Integer, Integer> getScoreDistribution() {
        return scoreDistribution;
    }

    public void setScoreDistribution(Map<Integer, Integer> scoreDistribution) {
        this.scoreDistribution = scoreDistribution;
    }
}
