package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 学生某课程单项成绩得分。
 */
public class GradeScoreVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩组成名称 */
    private String componentName;
    /** 该组成项的具体得分 */
    private double score;

    /**
     * 无参构造方法，供序列化框架使用。
     */
    public GradeScoreVO() {
    }

    /**
     * 构造一个单项成绩得分。
     *
     * @param componentName 成绩组成名称
     * @param score         得分
     */
    public GradeScoreVO(String componentName, double score) {
        this.componentName = componentName;
        this.score = score;
    }

    /**
     * 获取成绩组成名称。
     */
    public String getComponentName() {
        return componentName;
    }

    /**
     * 设置成绩组成名称。
     */
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    /**
     * 获取具体得分。
     */
    public double getScore() {
        return score;
    }

    /**
     * 设置具体得分。
     */
    public void setScore(double score) {
        this.score = score;
    }
}
