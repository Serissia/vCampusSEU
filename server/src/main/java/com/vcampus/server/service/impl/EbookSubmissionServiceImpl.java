package com.vcampus.server.service.impl;

import com.vcampus.common.vo.EbookSubmissionVO;
import com.vcampus.server.dao.EbookSubmissionDao;
import com.vcampus.server.dao.impl.EbookSubmissionDaoImpl;
import com.vcampus.server.service.EbookSubmissionService;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 图书馆纯电子书投稿业务实现。
 *
 * @author GGbongy
 */
public class EbookSubmissionServiceImpl implements EbookSubmissionService {

    private final EbookSubmissionDao submissionDao = new EbookSubmissionDaoImpl();

    @Override
    public boolean submit(EbookSubmissionVO submission) {
        if (submission == null || submission.getUploaderUid() == null
                || submission.getTitle() == null || submission.getTitle().trim().isEmpty()
                || submission.getAuthor() == null || submission.getAuthor().trim().isEmpty()
                || submission.getPublisher() == null || submission.getPublisher().trim().isEmpty()
                || submission.getResourceFile() == null || submission.getResourceFile().trim().isEmpty()) {
            return false;
        }
        submission.setCreatedTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        try {
            return submissionDao.insert(submission);
        } catch (SQLException e) {
            throw new RuntimeException("提交投稿失败", e);
        }
    }

    @Override
    public List<EbookSubmissionVO> listByUploader(String uid) {
        try {
            return submissionDao.listByUploader(uid);
        } catch (SQLException e) {
            throw new RuntimeException("查询投稿失败", e);
        }
    }

    @Override
    public List<EbookSubmissionVO> listPending() {
        try {
            return submissionDao.listPending();
        } catch (SQLException e) {
            throw new RuntimeException("查询待审核投稿失败", e);
        }
    }

    @Override
    public EbookSubmissionVO findById(int id) {
        try {
            return submissionDao.findById(id);
        } catch (SQLException e) {
            throw new RuntimeException("查询投稿失败", e);
        }
    }

    @Override
    public boolean updateStatus(int id, String status, String reviewer, String reviewComment) {
        try {
            return submissionDao.updateStatus(id, status, reviewer, reviewComment);
        } catch (SQLException e) {
            throw new RuntimeException("更新投稿状态失败", e);
        }
    }
}
