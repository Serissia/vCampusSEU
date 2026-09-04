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

    /** 默认后台抓取间隔时间（分钟）*/
    private final Integer DEFAULT_REFRESH_TIME = 120;

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
     * 初始化后台自动抓取调度任务（每 120 分钟增量拉取一次最近 7 天公告）
     */
    private void initAutoCrawler() {
        if (SCHEDULER_INITIALIZED.compareAndSet(false, true)) {
            AUTO_CRAWL_SCHEDULER.scheduleAtFixedRate(() -> {
                try {
                    System.out.println("[NoticeService] 执行后台定时公告自动抓取任务...");
                    triggerSync(7);
                } catch (Exception e) {
                    System.err.println("[NoticeService] 后台定时抓取失败: " + e.getMessage());
                }
            }, 1, DEFAULT_REFRESH_TIME, TimeUnit.MINUTES);
        }
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
        if (!IS_SYNCING.compareAndSet(false, true)) {
            return ResponseCode.SUCCESS;
        }
        try {
            int targetDays = days <= 0 ? 7 : days;
            List<NoticeVO> notices = JwcCrawler.crawl(targetDays);
            if (notices != null && !notices.isEmpty()) {
                noticeDao.batchInsertOrUpdate(notices);
            }
            String now = DateUtil.format(new Date());
            noticeDao.setMeta("last_sync_time", now);
            return ResponseCode.SUCCESS;
        } catch (Exception e) {
            System.err.println("[NoticeService] 抓取并同步教务公告失败: " + e.getMessage());
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