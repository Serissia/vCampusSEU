package com.vcampus.server.service;

import com.vcampus.common.vo.EbookSubmissionVO;

import java.util.List;

/**
 * 图书馆纯电子书投稿业务接口。
 *
 * @author GGbongy
 */
public interface EbookSubmissionService {

    /**
     * 提交投稿。
     */
    boolean submit(EbookSubmissionVO submission);

    /**
     * 查询某用户的投稿。
     */
    List<EbookSubmissionVO> listByUploader(String uid);

    /**
     * 查询待审核投稿。
     */
    List<EbookSubmissionVO> listPending();

    /**
     * 按 ID 查询投稿。
     */
    EbookSubmissionVO findById(int id);

    /**
     * 更新审核状态。
     */
    boolean updateStatus(int id, String status, String reviewer, String reviewComment);
}
