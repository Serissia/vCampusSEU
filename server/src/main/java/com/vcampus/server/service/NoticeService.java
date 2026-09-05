package com.vcampus.server.service;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.NoticeQueryVO;
import com.vcampus.common.vo.NoticeStatusVO;
import com.vcampus.common.vo.NoticeVO;

import java.util.List;

/**
 * 教务处公告业务服务接口。
 *
 * @author Serissia
 */
public interface NoticeService {

    /**
     * 按条件查询公告列表。
     *
     * @param query 查询条件
     * @return 公告列表
     */
    List<NoticeVO> queryNotices(NoticeQueryVO query);

    /**
     * 手动触发同步爬取。
     *
     * @param days 爬取天数范围
     * @return 业务响应状态码
     */
    ResponseCode triggerSync(int days);

    /**
     * 触发同步爬取并指明任务来源。
     *
     * @param days          爬取天数范围
     * @param triggerSource 任务来源说明（如：系统启动检查、后台定时任务、用户UID等）
     * @return 业务响应状态码
     */
    ResponseCode triggerSync(int days, String triggerSource);

    /**
     * 获取公告同步状态及上次抓取时间。
     *
     * @return 状态实体
     */
    NoticeStatusVO getStatus();
}