package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.util.DateUtil;
import com.vcampus.common.vo.NoticeQueryVO;
import com.vcampus.common.vo.NoticeStatusVO;
import com.vcampus.common.vo.NoticeVO;
import com.vcampus.server.dao.NoticeDao;
import com.vcampus.server.dao.impl.NoticeDaoImpl;
import com.vcampus.server.service.NoticeService;
import com.vcampus.server.util.JwcCrawler;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 教务处公告业务服务实现。
 *
 * @author Serissia
 */
public class NoticeServiceImpl implements NoticeService {

    /** 初次启动跳过抓取的间隔阈值（24小时，单位：毫秒）*/
    private static final long INITIAL_CRAWL_THRESHOLD_MS = 24L * 60 * 60 * 1000;

    /** 默认后台抓取间隔时间（分钟）*/
    private static final Integer DEFAULT_REFRESH_TIME = 120;

    /**
     * 公告持久层访问对象
     */
    private final NoticeDao noticeDao = new NoticeDaoImpl();

    /**
     * 并发任务互斥锁（防止多客户端同时触发重复爬虫）
     */
    private static final AtomicBoolean IS_SYNCING = new AtomicBoolean(false);

    /**
     * 定时调度器初始化标记
     */
    private static final AtomicBoolean SCHEDULER_INITIALIZED = new AtomicBoolean(false);

    /**
     * 后台静默抓取调度执行器
     */
    private static final ScheduledExecutorService AUTO_CRAWL_SCHEDULER = new ScheduledThreadPoolExecutor(
            1,
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Notice-AutoCrawler-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public NoticeServiceImpl() {
        initAutoCrawler();
    }

    /**
     * 初始化后台自动抓取调度任务。
     * 服务启动时若距离上次抓取不足24小时则跳过初次请求；运行期间每120分钟增量轮询一次。
     */
    private void initAutoCrawler() {
        if (SCHEDULER_INITIALIZED.compareAndSet(false, true)) {
            // 异步检查上次抓取时间，防止启动时频繁请求目标网站
            checkAndPerformInitialCrawl();

            // 注册周期调度任务，服务器持续运行期间每 120 分钟轮询一次近 7 天公告
            AUTO_CRAWL_SCHEDULER.scheduleAtFixedRate(() -> {
                try {
                    System.out.println("[NoticeService] 执行后台定时公告自动抓取任务...");
                    triggerSync(7);
                } catch (Exception e) {
                    System.err.println("[NoticeService] 后台定时抓取失败: " + e.getMessage());
                }
            }, DEFAULT_REFRESH_TIME, DEFAULT_REFRESH_TIME, TimeUnit.MINUTES);
        }
    }

    /**
     * 校验上次同步时间并决定是否执行启动初次同步。
     */
    private void checkAndPerformInitialCrawl() {
        AUTO_CRAWL_SCHEDULER.execute(() -> {
            try {
                String lastSyncStr = noticeDao.getMeta("last_sync_time");
                boolean needCrawl = true;

                if (lastSyncStr != null && !lastSyncStr.trim().isEmpty() && !"暂无记录".equals(lastSyncStr.trim())) {
                    try {
                        Date lastSyncDate = DateUtil.parse(lastSyncStr.trim());
                        long diff = System.currentTimeMillis() - lastSyncDate.getTime();
                        if (diff >= 0 && diff < INITIAL_CRAWL_THRESHOLD_MS) {
                            needCrawl = false;
                            long remainHours = (INITIAL_CRAWL_THRESHOLD_MS - diff) / (1000 * 60 * 60);
                            System.out.println("[NoticeService] 距上次同步 (" + lastSyncStr + ") 不足 24 小时，跳过启动爬取（约 " + remainHours + " 小时后允许初次检查）。");
                        }
                    } catch (Exception ignored) {
                        // 时间格式解析异常时降级执行一次抓取
                    }
                }

                if (needCrawl) {
                    System.out.println("[NoticeService] 距上次同步已超过 24 小时或无记录，执行启动初始化抓取...");
                    triggerSync(7);
                }
            } catch (Exception e) {
                System.err.println("[NoticeService] 检查初次抓取状态异常: " + e.getMessage());
            }
        });
    }

    @Override
    public List<NoticeVO> queryNotices(NoticeQueryVO query) {
        try {
            return noticeDao.queryNotices(query);
        } catch (SQLException e) {
            throw new RuntimeException("查询教务公告失败", e);
        }
    }

    @Override
    public ResponseCode triggerSync(int days) {
        return triggerSync(days, "系统内置任务");
    }

    @Override
    public ResponseCode triggerSync(int days, String triggerSource) {
        String source = (triggerSource == null || triggerSource.trim().isEmpty()) ? "未知来源" : triggerSource.trim();
        int targetDays = days <= 0 ? 7 : days;

        if (!IS_SYNCING.compareAndSet(false, true)) {
            System.out.println("[NoticeService] 忽略同步请求 [来源: " + source + "]：已有抓取任务在执行中。");
            return ResponseCode.SUCCESS;
        }

        long startTime = System.currentTimeMillis();
        System.out.println("[NoticeService] 开始执行公告抓取 [来源: " + source + ", 目标范围: 近 " + targetDays + " 天]...");

        try {
            List<NoticeVO> notices = JwcCrawler.crawl(targetDays);
            int crawledCount = (notices != null) ? notices.size() : 0;
            int affectedRows = 0;

            if (crawledCount > 0) {
                affectedRows = noticeDao.batchInsertOrUpdate(notices);
            }

            String now = DateUtil.format(new Date());
            noticeDao.setMeta("last_sync_time", now);

            long costMs = System.currentTimeMillis() - startTime;
            System.out.println("[NoticeService] 抓取入库完成 [来源: " + source
                    + ", 抓取条数: " + crawledCount
                    + ", 数据库变动: " + affectedRows + " 行"
                    + ", 耗时: " + costMs + "ms"
                    + ", 最新同步点: " + now + "]");

            return ResponseCode.SUCCESS;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            System.err.println("[NoticeService] 抓取失败 [来源: " + source
                    + ", 耗时: " + costMs + "ms], 原因: " + e.getMessage());
            return ResponseCode.FAIL;
        } finally {
            IS_SYNCING.set(false);
        }
    }

    @Override
    public NoticeStatusVO getStatus() {
        try {
            String lastSync = noticeDao.getMeta("last_sync_time");
            if (lastSync == null || lastSync.trim().isEmpty()) {
                lastSync = "暂无记录";
            }
            int total = noticeDao.countTotalNotices();
            return new NoticeStatusVO(lastSync, IS_SYNCING.get(), total);
        } catch (SQLException e) {
            return new NoticeStatusVO("获取失败", IS_SYNCING.get(), 0);
        }
    }
}