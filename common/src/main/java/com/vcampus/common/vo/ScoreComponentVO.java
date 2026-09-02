package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 课程成绩组成项。
 */
public class ScoreComponentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成绩组成名称，例如：平时成绩、实验成绩、期末成绩 */
    private String componentName;
    /** 该组成项在最终成绩中的权重 */
    private double weight;

    /**
     * 无参构造方法，供序列化框架使用。
     */
    public ScoreComponentVO() {
    }

    /**
     * 构造一个成绩组成项。
     *
     * @param componentName 组成名称
     * @param weight        权重
     */
    public ScoreComponentVO(String componentName, double weight) {
        this.componentName = componentName;
        this.weight = weight;
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
     * 获取成绩权重。
     */
    public double getWeight() {
        return weight;
    }

    /**
     * 设置成绩权重。
     */
    public void setWeight(double weight) {
        this.weight = weight;
    }
}
