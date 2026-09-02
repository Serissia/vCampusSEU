package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseSelectionVO;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.server.dao.CourseDao;
import com.vcampus.server.dao.CourseSelectionDao;
import com.vcampus.server.dao.impl.CourseDaoImpl;
import com.vcampus.server.dao.impl.CourseSelectionDaoImpl;
import com.vcampus.server.service.CourseSelectionService;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * 选退课业务实现。
 *
 * @author xingyi852
 */
public class CourseSelectionServiceImpl implements CourseSelectionService {

    private final CourseDao courseDao = new CourseDaoImpl();
    private final CourseSelectionDao selectionDao = new CourseSelectionDaoImpl();

    /**
     * 选课：依次校验课程状态、是否已选、课程容量，再写入选课记录。
     */
    @Override
    public ResponseCode selectCourse(String studentId, String courseCode) {
        try {
            // 只有正常开课状态的课程允许选课
            CourseVO course = courseDao.findByCode(courseCode);
            if (course == null || !CourseVO.STATUS_ACTIVE.equals(course.getStatus())) {
                return ResponseCode.COURSE_NOT_FOUND;
            }
            // 防止同一学生重复选择同一课程
            if (selectionDao.exists(studentId, courseCode)) {
                return ResponseCode.ALREADY_SELECTED;
            }
            // 课程容量已满时拒绝选课
            if (course.getSelectedCount() >= course.getCapacity()) {
                return ResponseCode.COURSE_FULL;
            }

            List<CourseVO> selectedCourses = selectionDao.listByStudent(studentId);
            for (CourseVO selected : selectedCourses) {
                if (selected.getClassTime() != null && course.getClassTime() != null
                        && selected.getClassTime().equals(course.getClassTime())) {
                    return ResponseCode.COURSE_TIME_CONFLICT;
                }
            }

            // 在同一事务中写入选课记录并增加课程已选人数
            CourseSelectionVO selection = new CourseSelectionVO();
            selection.setStudentId(studentId);
            selection.setCourseCode(courseCode);
            selection.setSelectTime(new Date());
            selection.setStatus("SELECTED");
            selectionDao.insertWithCountUpdate(selection);
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }

    /**
     * 退课：仅对已有选课记录执行删除，并同步减少已选人数。
     */
    @Override
    public ResponseCode dropCourse(String studentId, String courseCode) {
        try {
            if (!selectionDao.exists(studentId, courseCode)) {
                return ResponseCode.COURSE_NOT_FOUND;
            }
            selectionDao.deleteWithCountUpdate(studentId, courseCode);
            return ResponseCode.SUCCESS;
        } catch (SQLException e) {
            return ResponseCode.FAIL;
        }
    }

    /**
     * 查询学生已选课程。
     */
    @Override
    public List<CourseVO> listMyCourses(String studentId) {
        try {
            return selectionDao.listByStudent(studentId);
        } catch (SQLException e) {
            throw new RuntimeException("查询已选课程失败", e);
        }
    }
}
