package com.vcampus.common.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公告抓取与同步状态值对象。
 *
 * @author Serissia
 */
public class NoticeStatusVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 上次成功抓取时间（格式：yyyy-MM-dd HH:mm:ss）
     */
    private String lastSyncTime;

    /**
     * 当前是否正在执行抓取任务
     */
    private boolean syncing;

    /**
     * 本地已收录的公告总条数
     */
    private int totalCount;

    public NoticeStatusVO() {
    }

    public NoticeStatusVO(String lastSyncTime, boolean syncing, int totalCount) {
        this.lastSyncTime = lastSyncTime;
        this.syncing = syncing;
        this.totalCount = totalCount;
    }

    public String getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(String lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public boolean isSyncing() {
        return syncing;
    }

    public void setSyncing(boolean syncing) {
        this.syncing = syncing;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    @Override
    public String toString() {
        return "NoticeStatusVO{" +
                "lastSyncTime='" + lastSyncTime + '\'' +
                ", syncing=" + syncing +
                ", totalCount=" + totalCount +
                '}';
    }
}