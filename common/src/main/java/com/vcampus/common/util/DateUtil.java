package com.vcampus.common.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 通用日期格式化工具。
 *
 * @author xingyi852
 */
public final class DateUtil {

    public static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private DateUtil() {
    }

    /**
     * 将日期格式化为统一的字符串。
     */
    public static String format(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(DATE_PATTERN).format(date);
    }

    /**
     * 将统一格式的日期字符串解析为 Date。
     */
    public static Date parse(String text) throws ParseException {
        return new SimpleDateFormat(DATE_PATTERN).parse(text);
    }
}
