package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.GradeStatisticsVO;
import com.vcampus.common.vo.GradeScoreVO;
import com.vcampus.common.vo.GradeVO;
import com.vcampus.common.vo.ScoreComponentVO;
import com.vcampus.server.dao.CourseDao;
import com.vcampus.server.dao.impl.CourseDaoImpl;
import com.vcampus.server.dao.GradeDao;
import com.vcampus.server.dao.impl.GradeDaoImpl;
import com.vcampus.server.service.GradeService;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩管理业务实现。
 *
 * @author xingyi852
 */
public class GradeServiceImpl implements GradeService {

    private final GradeDao gradeDao = new GradeDaoImpl();
    private final CourseDao courseDao = new CourseDaoImpl();

    /**
     * 录入成绩：重复提交同一课程成绩时返回 GRADE_ALREADY_EXISTS。
     */
    @Override
    public ResponseCode submitGrade(GradeVO grade) {
        try {
            // 学生和课程代码是成绩记录的必要信息
            if (grade == null || grade.getStudentId() == null || grade.getCourseCode() == null) {
                return ResponseCode.INVALID_REQUEST;
            }
            // 同一学生同一课程只允许存在一条成绩
            // 未指定状态时默认标记为已提交
            if (grade.getStatus() == null || grade.getStatus().trim().length() == 0) {
                grade.setStatus("SUBMITTED");
            }
            // 读取课程教师配置的成绩组成及权重
            CourseVO course = courseDao.findByCode(grade.getCourseCode());
            if (course == null || course.getScoreComponents() == null
                    || course.getScoreComponents().isEmpty()) {
                return ResponseCode.INVALID_REQUEST;
            }

            // 将课程成绩组成转换为名称 -> 权重的映射
            Map<String, Double> weightMap = new HashMap<String, Double>();
            double weightSum = 0.0;
            for (ScoreComponentVO component : course.getScoreComponents()) {
                weightMap.put(component.getComponentName(), component.getWeight());
                weightSum += component.getWeight();
            }
            if (weightSum <= 0) {
                return ResponseCode.INVALID_REQUEST;
            }

            // 按课程配置动态计算最终成绩
            double finalScore = 0.0;
            for (GradeScoreVO score : grade.getComponentScores()) {
                Double weight = weightMap.get(score.getComponentName());
                if (weight != null) {
                    finalScore += score.getScore() * weight / weightSum;
                }
            }
            grade.setFinalScore(round(finalScore));
            grade.setGpa(calculateGpa(finalScore));
            boolean exists = gradeDao.exists(grade.getStudentId(), grade.getCourseCode());
            boolean ok = exists ? gradeDao.update(grade) : gradeDao.insert(grade);
            return ok ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }

    /**
     * 查询学生成绩。
     */
    @Override
    public List<GradeVO> queryByStudent(String studentId) {
        try {
            return gradeDao.listByStudent(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("查询学生成绩失败", e);
        }
    }

    /**
     * 查询课程成绩，用于教师/教务统计。
     */
    @Override
    public List<GradeVO> queryByCourse(String courseCode) {
        try {
            return gradeDao.listByCourse(courseCode);
        } catch (SQLException e) {
            throw new RuntimeException("查询课程成绩失败", e);
        }
    }

    @Override
    public GradeStatisticsVO getCourseStatistics(String courseCode) {
        try {
            List<GradeVO> grades = gradeDao.listByCourse(courseCode);
            GradeStatisticsVO stats = new GradeStatisticsVO();
            stats.setCourseCode(courseCode);

            if (grades.isEmpty()) {
                stats.setStudentCount(0);
                stats.setAverageScore(0);
                stats.setMaxScore(0);
                stats.setMinScore(0);
                stats.setPassRate(0);
                stats.setScoreDistribution(new HashMap<Integer, Integer>());
                return stats;
            }

            stats.setCourseName(grades.get(0).getCourseName());
            stats.setStudentCount(grades.size());

            double total = 0.0;
            double max = Double.MIN_VALUE;
            double min = Double.MAX_VALUE;
            int passCount = 0;
            Map<Integer, Integer> distribution = new HashMap<Integer, Integer>();

            for (GradeVO grade : grades) {
                double score = grade.getFinalScore();
                total += score;
                if (score > max) {
                    max = score;
                }
                if (score < min) {
                    min = score;
                }
                if (score >= 60) {
                    passCount++;
                }
                int bucket = Math.min(90, (int) (score / 10) * 10);
                Integer count = distribution.get(bucket);
                distribution.put(bucket, count == null ? 1 : count + 1);
            }

            stats.setAverageScore(round(total / grades.size()));
            stats.setMaxScore(max);
            stats.setMinScore(min);
            stats.setPassRate(round(passCount * 100.0 / grades.size()));
            stats.setScoreDistribution(distribution);
            return stats;
        } catch (SQLException e) {
            throw new RuntimeException("统计课程成绩失败", e);
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double calculateGpa(double score) {
        if (score >= 96) {
            return 4.8;
        }
        if (score >= 93) {
            return 4.5;
        }
        if (score >= 90) {
            return 4.0;
        }
        if (score >= 86) {
            return 3.8;
        }
        if (score >= 83) {
            return 3.5;
        }
        if (score >= 80) {
            return 3.0;
        }
        if (score >= 76) {
            return 2.8;
        }
        if (score >= 73) {
            return 2.5;
        }
        if (score >= 70) {
            return 2.0;
        }
        if (score >= 66) {
            return 1.8;
        }
        if (score >= 63) {
            return 1.5;
        }
        if (score >= 60) {
            return 1.0;
        }
        return 0.0;
    }
}
