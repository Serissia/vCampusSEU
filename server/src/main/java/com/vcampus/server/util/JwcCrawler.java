package com.vcampus.server.util;

import com.vcampus.common.util.DateUtil;
import com.vcampus.common.vo.NoticeVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东南大学教务处 (jwc.seu.edu.cn) 公告抓取工具类。
 *
 * @author Serissia
 */
public final class JwcCrawler {

    /**
     * 教务处网站根路径
     */
    private static final String BASE_URL = "https://jwc.seu.edu.cn";

    /**
     * 目标抓取栏目相对路径与栏目名称
     */
    private static final String[][] TARGET_CHANNELS = {
            {"/1598", "教务信息"},
            {"/1599", "教学动态"}
    };

    /**
     * 日期解析正则表达式，兼容 2026-03-01、2026/3/1、2026.03.01
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\\d|3[01])");

    /**
     * 日期标准格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 网络连接超时时间（毫秒）
     */
    private static final int TIMEOUT_MS = 8000;

    /**
     * 模拟客户端请求头 User-Agent
     */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private JwcCrawler() {
    }

    /**
     * 爬取指定天数内的教务公告。
     *
     * @param days 抓取天数范围（如 3、7、15、30、120）
     * @return 解析后的公告列表
     */
    public static List<NoticeVO> crawl(int days) {
        List<NoticeVO> results = new ArrayList<>();
        LocalDate cutoffDate = LocalDate.now().minusDays(Math.max(1, days));
        String crawlTime = DateUtil.format(new Date());

        int maxPages = Math.max(1, Math.min(10, days / 15 + 1));

        for (String[] channel : TARGET_CHANNELS) {
            String channelPath = channel[0];
            String category = channel[1];
            crawlChannel(channelPath, category, cutoffDate, maxPages, crawlTime, results);
        }

        return results;
    }

    /**
     * 针对单一栏目执行分页请求与 DOM 抽取。
     */
    private static void crawlChannel(String channelPath, String category, LocalDate cutoffDate,
                                     int maxPages, String crawlTime, List<NoticeVO> results) {
        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = (page == 1)
                    ? BASE_URL + channelPath + "/list.htm"
                    : BASE_URL + channelPath + "/list" + page + ".htm";

            try {
                Document doc = Jsoup.connect(pageUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();

                Elements items = doc.select("li[class*=news], tr[class*=list], div[class*=news_item], ul.news_list li");
                if (items.isEmpty()) {
                    items = doc.select("li:has(a), tr:has(a)");
                }
                if (items.isEmpty()) {
                    break;
                }

                boolean reachCutoff = false;
                for (Element item : items) {
                    Element aTag = item.selectFirst("a[href]");
                    if (aTag == null) {
                        continue;
                    }

                    String href = aTag.absUrl("href");
                    if (href == null || !href.startsWith("http")) {
                        continue;
                    }

                    String title = aTag.hasAttr("title") && !aTag.attr("title").trim().isEmpty()
                            ? aTag.attr("title").trim()
                            : aTag.text().trim();

                    if (title.isEmpty()) {
                        continue;
                    }

                    String rawDate = extractDate(item.text());
                    if (rawDate == null) {
                        continue;
                    }

                    LocalDate pubDate;
                    try {
                        pubDate = LocalDate.parse(rawDate, DATE_FORMATTER);
                    } catch (Exception e) {
                        continue;
                    }

                    if (pubDate.isBefore(cutoffDate)) {
                        reachCutoff = true;
                        break;
                    }

                    NoticeVO notice = new NoticeVO();
                    notice.setTitle(title);
                    notice.setPublishDate(rawDate);
                    notice.setCategory(category);
                    notice.setUrl(href);
                    notice.setCrawledTime(crawlTime);
                    results.add(notice);
                }

                if (reachCutoff) {
                    break;
                }
            } catch (Exception e) {
                System.err.println("[JwcCrawler] 抓取栏目 [" + category + "] 异常 (" + pageUrl + "): " + e.getMessage());
                break;
            }
        }
    }

    /**
     * 文本日期截取与标准化。
     */
    private static String extractDate(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            String year = matcher.group(1);
            String month = matcher.group(2);
            String day = matcher.group(3);
            if (month.length() == 1) {
                month = "0" + month;
            }
            if (day.length() == 1) {
                day = "0" + day;
            }
            return year + "-" + month + "-" + day;
        }
        return null;
    }
}