package com.vcampus.common.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 课程成绩值对象。
 *
 * @author xingyi852
 */
public class GradeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学生账号 */
    private String studentId;
    /** 课程代码 */
    private String courseCode;
    /** 课程名称 */
    private String courseName;
    /** 各成绩组成项的得分 */
    private List<GradeScoreVO> componentScores = new ArrayList<GradeScoreVO>();
    /** 最终成绩 */
    private double finalScore;
    /** 绩点 */
    private double gpa;
    /** 成绩状态 */
    private String status;

    public GradeVO() {
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
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

    public List<GradeScoreVO> getComponentScores() {
        return componentScores;
    }

    public void setComponentScores(List<GradeScoreVO> componentScores) {
        this.componentScores = componentScores;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(double finalScore) {
        this.finalScore = finalScore;
    }

    public double getGpa() {
        return gpa;
    }

    public void setGpa(double gpa) {
        this.gpa = gpa;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
