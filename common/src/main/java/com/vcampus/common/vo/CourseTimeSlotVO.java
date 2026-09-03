package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 课程单个上课时间段。
 *
 * <p>每个时间段由起止周、星期、起始节次和结束节次组成。</p>
 */
public class CourseTimeSlotVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 起始周 */
    private int startWeek;
    /** 结束周 */
    private int endWeek;
    /** 星期，例如：周一 */
    private String day;
    /** 起始节次 */
    private int startPeriod;
    /** 结束节次 */
    private int endPeriod;

    public CourseTimeSlotVO() {
    }

    public CourseTimeSlotVO(int startWeek, int endWeek, String day,
                            int startPeriod, int endPeriod) {
        this.startWeek = startWeek;
        this.endWeek = endWeek;
        this.day = day;
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
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

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public int getStartPeriod() {
        return startPeriod;
    }

    public void setStartPeriod(int startPeriod) {
        this.startPeriod = startPeriod;
    }

    public int getEndPeriod() {
        return endPeriod;
    }

    public void setEndPeriod(int endPeriod) {
        this.endPeriod = endPeriod;
    }
}
