package com.vcampus.server.dao;

import com.vcampus.common.vo.EbookSubmissionVO;

import java.sql.SQLException;
import java.util.List;

/**
 * 图书馆纯电子书投稿数据访问接口。
 *
 * @author GGbongy
 */
public interface EbookSubmissionDao {

    /**
     * 写入一条投稿记录。
     */
    boolean insert(EbookSubmissionVO submission) throws SQLException;

    /**
     * 查询某用户的全部投稿。
     */
    List<EbookSubmissionVO> listByUploader(String uid) throws SQLException;

    /**
     * 查询全部待审核投稿。
     */
    List<EbookSubmissionVO> listPending() throws SQLException;

    /**
     * 按 ID 查询投稿。
     */
    EbookSubmissionVO findById(int id) throws SQLException;

    /**
     * 更新审核状态与审核信息。
     */
    boolean updateStatus(int id, String status, String reviewer, String reviewComment) throws SQLException;
}
