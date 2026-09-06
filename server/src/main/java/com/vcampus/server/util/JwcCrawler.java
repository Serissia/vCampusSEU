package com.vcampus.server.util;

import com.vcampus.common.util.DateUtil;
import com.vcampus.common.vo.NoticeVO;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东南大学教务处 (jwc.seu.edu.cn) 公告抓取工具类。
 *
 * <br>教务公告合法性判定与内容解析核心逻辑如下：</br>
 * <ol>
 *   <li><b>链接类型过滤</b>：排除非 HTTP(S) 及栏目列表链接（如 /list*.htm）；仅提取具有文章正文特征（含 page.psp、page.htm、/c...a...）的链接，并进行全局内存排重。</li>
 *   <li><b>标题完整性校验</b>：优先提取 {@code <a>} 标签的 {@code title} 属性，避免被前端省略号截断，且字符长度不得小于 3。</li>
 *   <li><b>发布日期三级解析</b>：
 *     <ul>
 *       <li>优先级 0（最高）：从专属日期类标签 {@code .Article_PublishDate} 提取；</li>
 *       <li>优先级 1：从文章 URL 路径提取 {@code /YYYY/MMDD/}，规避标题与摘要中业务日期的干扰；</li>
 *       <li>优先级 2：从专属日期类标签（如 {@code .Article_PublishDate}）提取；</li>
 *       <li>优先级 3：剔除容器内超链接（移除标题干扰）后提取行末最后一个日期，短日期自动补齐当年年份。</li>
 *     </ul>
 *   </li>
 *   <li><b>动态时间窗口与置顶容错</b>：单条公告早于目标天数窗口时仅标记为“过期跳过”，不阻断同页后续解析（防止置顶旧公告导致当页最新通知漏抓）；非首页整页全过期时终止翻页。</li>
 *   <li><b>栏目细分归一化</b>：优先提取同级容器中的中括号标签（如 {@code [联合培养]}）作为精确分类，缺失时回退为入口默认栏目。</li>
 * </ol>
 *
 * @author Serissia
 */

public final class JwcCrawler {

    /**
     * 目标抓取栏目及其名称（支持绝对 URL 与相对路径）
     */
    private static final String[][] TARGET_CHANNELS = {
            {"https://jwc.seu.edu.cn/jwxx/list.htm", "教务信息"},
            {"https://jwc.seu.edu.cn/xjgl/list.htm", "学籍管理"},
            {"https://jwc.seu.edu.cn/jxyj/list.htm", "教学研究"},
            {"https://jwc.seu.edu.cn/sjjx/list.htm", "实践教学"},
            {"https://jwc.seu.edu.cn/cbxx/list.htm", "文化素质教育"},
            {"https://jwc.seu.edu.cn/gj/24752/list.htm", "国际交流-联合培养"},
            {"https://jwc.seu.edu.cn/gj/24753/list.htm", "国际交流-学期交流"},
            {"https://jwc.seu.edu.cn/gj/24754/list.htm", "国际交流-短期交流"},
            {"https://jwc.seu.edu.cn/gj/247551/list.htm", "国际交流-项目资助"},
            {"https://jwc.seu.edu.cn/gj/24756/list.htm", "国际交流-宣讲活动"}
    };

    /**
     * WebPlus 站群系统文章 URL 日期路径正则（例：/2026/0830/c21678...）
     */
    private static final Pattern URL_DATE_PATTERN = Pattern.compile("/(\\d{4})/(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])/");
    /**
     * 日期解析正则表达式，兼容 2026-03-01、2026/3/1、2026.03.01
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\\d|3[01])");
    /**
     * 无年份的短日期正则（例：08-30、[8/30]）
     */
    private static final Pattern SHORT_DATE_PATTERN = Pattern.compile("(?:^|\\D)(0?[1-9]|1[0-2])[-/.月](0?[1-9]|[12]\\d|3[01])(?:日|\\D|$)");
    /**
     * 标准日期格式化器
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 中括号标签正则（例：[联合培养]、【联合培养】）
     */
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("[\\[【]([^]】]+)[]】]");

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
        Set<String> seenUrls = new HashSet<>();
        LocalDate cutoffDate = LocalDate.now().minusDays(Math.max(1, days));
        String crawlTime = DateUtil.format(new Date());

        int maxPages = Math.max(1, Math.min(10, days / 15 + 1));
        System.out.println("[JwcCrawler] >>> 开始执行教务公告抓取，时间截止阈值: " + cutoffDate + " (近 " + days + " 天)，最大翻页数: " + maxPages);

        for (String[] channel : TARGET_CHANNELS) {
            String channelPath = channel[0];
            String category = channel[1];
            crawlChannel(channelPath, category, cutoffDate, maxPages, crawlTime, results, seenUrls);
        }

        System.out.println("[JwcCrawler] <<< 所有栏目抓取完毕，共成功提取有效公告: " + results.size() + " 条");
        return results;
    }

    /**
     * 针对单一栏目执行分页请求与文档解析。
     */
    private static void crawlChannel(String channelPath, String category, LocalDate cutoffDate,
                                     int maxPages, String crawlTime, List<NoticeVO> results, Set<String> seenUrls) {
        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = buildPageUrl(channelPath, page);
            if (pageUrl == null) {
                break;
            }

            System.out.println("[JwcCrawler] 正在请求栏目 [" + category + "] 第 " + page + " 页: " + pageUrl);

            Document doc;
            try {
                Connection.Response response = Jsoup.connect(pageUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .followRedirects(true)
                        .execute();

                int statusCode = response.statusCode();
                doc = response.parse();
                // System.out.println("[JwcCrawler] 响应成功 [HTTP " + statusCode + "] 页面标题: <" + doc.title() + ">");
            } catch (Exception e) {
                System.err.println("[JwcCrawler] 连接异常 [" + category + "] (" + pageUrl + "): " + e.getClass().getSimpleName() + " - " + e.getMessage());
                break;
            }

            // 直接定位页面中所有指向正文的 a 标签
            Elements articleLinks = doc.select("a[href*='page.psp'], a[href*='page.htm'], a[href*='page.html'], a[href*='/c']");
            if (articleLinks.isEmpty()) {
                articleLinks = doc.select("tr a[href], li a[href]");
            }

            if (articleLinks.isEmpty()) {
                break;
            }

            // 统计本页有效与过期条目数，用于判断是否提前终止翻页
            int validCountInPage = 0;
            int expiredCountInPage = 0;

            for (Element a : articleLinks) {
                String href = a.absUrl("href");
                // 过滤非 HTTP(S) 链接及栏目列表页
                if (href == null || !href.startsWith("http") || isListOrChannelUrl(href)) {
                    continue;
                }

                String title = getLinkTitle(a);
                if (title.length() < 3) {
                    continue;
                }

                if (!seenUrls.add(href)) {
                    continue;
                }

                // 高优先级准确日期抽取
                Element container = a.closest("tr, li");
                if (container == null) {
                    container = a.closest("p, div");
                }

                String rawDate = resolvePublishDate(href, container);
                if (rawDate == null) {
                    // System.out.println("  - [跳过] 无法获取发布日期: 《" + title + "》 -> " + href);
                    continue;
                }

                LocalDate pubDate;
                try {
                    pubDate = LocalDate.parse(rawDate, DATE_FORMATTER);
                } catch (Exception e) {
                    continue;
                }

                // 提取同级容器可能存在的标签（如 [联合培养]）
                String detectedCategory = extractSubCategory(container, category);

                if (pubDate.isBefore(cutoffDate)) {
                    expiredCountInPage++;
                    // System.out.println("  - [过期] 《" + title + "》 (" + rawDate + " 早于 " + cutoffDate + ")");
                    continue;
                }

                validCountInPage++;
                NoticeVO notice = new NoticeVO();
                notice.setTitle(title);
                notice.setPublishDate(rawDate);
                notice.setCategory(detectedCategory);
                notice.setUrl(href);
                notice.setCrawledTime(crawlTime);
                results.add(notice);

                // System.out.println("  + [录入] [" + detectedCategory + "] 《" + title + "》 (" + rawDate + ") -> " + href);
            }

            System.out.println("[JwcCrawler] 栏目 [" + category + "] 第 " + page + " 页解析结束: 有效新条目 " + validCountInPage + " 条, 过期跳过 " + expiredCountInPage + " 条");

            // 若非首页且本页全部过期，说明已越过时间窗口，提前中断
            if (page > 1 && validCountInPage == 0 && expiredCountInPage > 0) {
                // System.out.println("[JwcCrawler] 时间窗口已满足，提前停止栏目 [" + category + "] 的后续翻页");
                break;
            }
        }
    }

    /**
     * 高精度发布日期解析器。
     * <ol>
     *     <li>第一优先级：查找 WebPlus 官方标准类 {@code .Article_PublishDate}；</li>
     *     <li>第二优先级：从文章 URL 路径推导（如 {@code /2026/0830/}），杜绝正文标题干扰；</li>
     *     <li>第三优先级：查找其它通用日期属性标签；</li>
     *     <li>第四优先级：剔除行容器所有超链接（移去标题）后提取剩余文本末尾日期。</li>
     * </ol>
     *
     * @param href      公告正文 URL
     * @param container 所属行容器
     * @return 格式化日期字符串（yyyy-MM-dd）
     */
    private static String resolvePublishDate(String href, Element container) {
        if (container != null) {
            // 第一优先级：精准提取站群专属日期节点
            Element publishDateEl = container.selectFirst(".Article_PublishDate");
            if (publishDateEl != null) {
                String d = extractDate(publishDateEl.text());
                if (d != null) {
                    return d;
                }
            }
        }

        // 其次优先从 URL 路径获取系统生成的标准发布日期
        String urlDate = extractDateFromUrl(href);
        if (urlDate != null) {
            return urlDate;
        }

        if (container == null) {
            return null;
        }

        // 查找专门的日期展示节点（避免混入标题文本）
        Element dateEl = container.selectFirst(".Article_PublishDate, [class*='date'], [class*='time'], [class*='days']");
        if (dateEl != null) {
            String d = extractDate(dateEl.text());
            if (d != null) {
                return d;
            }
        }

        // 剔除容器内的超链接文本（移去标题），再搜索最右侧出现的发布日期
        Element clone = container.clone();
        clone.select("a").remove();
        String pureText = clone.text().trim();

        return extractLastDate(pureText);
    }

    /**
     * 从文章 URL（如 /2026/0830/c21678a580815/page.psp）中精准提取年月日
     */
    private static String extractDateFromUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = URL_DATE_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
        }
        return null;
    }

    /**
     * 从文本中提取最后一个匹配的日期（因为行末一般为真正的发布时间）
     */
    private static String extractLastDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String lastFound = null;
        Matcher fullMatcher = DATE_PATTERN.matcher(text);
        while (fullMatcher.find()) {
            String year = fullMatcher.group(1);
            String month = fullMatcher.group(2);
            String day = fullMatcher.group(3);
            lastFound = String.format("%s-%02d-%02d", year, Integer.parseInt(month), Integer.parseInt(day));
        }
        if (lastFound != null) {
            return lastFound;
        }

        Matcher shortMatcher = SHORT_DATE_PATTERN.matcher(text);
        while (shortMatcher.find()) {
            int currentYear = LocalDate.now().getYear();
            String month = shortMatcher.group(1);
            String day = shortMatcher.group(2);
            lastFound = String.format("%d-%02d-%02d", currentYear, Integer.parseInt(month), Integer.parseInt(day));
        }

        return lastFound;
    }

    /**
     * 单段文本日期抽取
     */
    private static String extractDate(String text) {
        return extractLastDate(text);
    }

    /**
     * 智能构造分页请求 URL
     */
    private static String buildPageUrl(String rawUrl, int page) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return null;
        }
        String url = rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = url.startsWith("/") ? "https://jwc.seu.edu.cn" + url : "https://" + url;
        }

        if (page == 1) {
            return url;
        }

        if (url.contains("/list.htm")) {
            return url.replace("/list.htm", "/list" + page + ".htm");
        }
        if (url.contains("/list.psp")) {
            return url.replace("/list.psp", "/list" + page + ".psp");
        }
        if (url.endsWith(".htm")) {
            return url.replaceAll("\\.htm$", page + ".htm");
        }
        return url.endsWith("/") ? url + "list" + page + ".htm" : url + "/list" + page + ".htm";
    }

    /**
     * 过滤非文章页面的栏目索引链接
     */
    private static boolean isListOrChannelUrl(String href) {
        if (href == null) {
            return false;
        }
        String lower = href.toLowerCase();
        return lower.endsWith("/list.htm")
                || lower.endsWith("/list.psp")
                || lower.matches(".*/list\\d*\\.(htm|html|psp).*");
    }

    /**
     * 获取完整标题
     */
    private static String getLinkTitle(Element a) {
        if (a == null) {
            return "";
        }
        if (a.hasAttr("title") && !a.attr("title").trim().isEmpty()) {
            return a.attr("title").trim();
        }
        return a.text().trim();
    }

    /**
     * 从当前行提取中括号标签或前置链接作为具体分类
     */
    private static String extractSubCategory(Element container, String fallbackCategory) {
        if (container == null) {
            return fallbackCategory;
        }
        Elements listLinks = container.select("a[href*='list']");
        for (Element l : listLinks) {
            String txt = l.text().replaceAll("[\\[\\]【】\\s]", "").trim();
            if (!txt.isEmpty() && txt.length() <= 12) {
                return txt;
            }
        }
        Matcher matcher = CATEGORY_PATTERN.matcher(container.text());
        if (matcher.find()) {
            String tag = matcher.group(1).trim();
            if (tag.length() <= 12) {
                return tag;
            }
        }
        return fallbackCategory;
    }
}