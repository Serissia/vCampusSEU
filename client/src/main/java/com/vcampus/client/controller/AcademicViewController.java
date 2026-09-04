package com.vcampus.client.controller;

import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.CourseReviewVO;
import com.vcampus.common.vo.CourseTimeSlotVO;
import com.vcampus.common.vo.GradeScoreVO;
import com.vcampus.common.vo.GradeVO;
import com.vcampus.common.vo.ScoreComponentVO;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.time.Year;

/**
 * 教务模块 FXML 控制器，统一使用图书管理系统 UI 风格。
 *
 * <p>所有 Socket 请求均在后台线程池执行，并通过 {@link Platform#runLater(Runnable)}
 * 回到 JavaFX Application 线程刷新界面，避免阻塞 UI。</p>
 */
public class AcademicViewController {

    private static final ExecutorService THREAD_POOL = new ThreadPoolExecutor(
            2,
            4,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Academic-Thread-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @FXML
    private ScrollPane rootScrollPane;
    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Label sectionLabel;
    @FXML
    private FlowPane headerControls;
    @FXML
    private VBox formCard;
    @FXML
    private VBox formContent;
    @FXML
    private VBox dataCard;
    @FXML
    private TableView<Object> dataTable;

    private String moduleKey;
    private UserVO currentUser;
    private AcademicController academicController;
    private TilePane courseGrid;
    private List<CourseVO> allCourses = new ArrayList<>();
    private List<CourseVO> myCourses = new ArrayList<>();
    private boolean onlyMy;

    @FXML
    private void initialize() {
        if (rootScrollPane != null) {
            ScrollSpeedUtil.applyCustomScrollSpeed(rootScrollPane);
        }
    }

    /**
     * 由主界面注入当前模块键、登录用户和教务通信控制器。
     */
    public void initData(String moduleKey, UserVO user, AcademicController controller) {
        this.moduleKey = moduleKey;
        this.currentUser = user;
        this.academicController = controller;
        configureView();
    }

    /**
     * 根据模块键构建对应页面。
     */
    private void configureView() {
        dataTable.getColumns().clear();
        headerControls.getChildren().clear();
        formContent.getChildren().clear();
        formCard.setVisible(false);
        formCard.setManaged(false);

        switch (moduleKey) {
            case "ACADEMIC_SELECT":
                configureStudentSelect();
                break;
            case "ACADEMIC_GRADE":
                configureStudentGrade();
                break;
            case "ACADEMIC_TIMETABLE":
                configureTimetable("我的课表", "周一至周日可视化课表，点击课程卡片查看详细信息",
                        () -> academicController.listMyCourses());
                break;
            case "ACADEMIC_REVIEW":
                configureCourseReview();
                break;
            case "ACADEMIC_TEACHER_TIMETABLE":
                configureTimetable("授课课表", "查看自己教授的课程时间安排",
                        () -> academicController.queryByTeacher(currentUser.getUid()));
                break;
            case "ACADEMIC_TEACHER_REVIEW":
                configureTeacherCourseReview();
                break;
            case "ACADEMIC_TEACHER":
                configureTeacherCourse();
                break;
            case "ACADEMIC_GRADE_SUBMIT":
                configureGradeSubmit();
                break;
            case "ACADEMIC_MANAGE":
                configureAcademicManage();
                break;
            case "ACADEMIC_ADJUST":
                configureCourseAdjust();
                break;
            case "ACADEMIC_APPROVE":
                configureCourseApprove();
                break;
            default:
                titleLabel.setText("教务管理");
                subtitleLabel.setText("未知教务模块");
                sectionLabel.setText("数据列表");
                break;
        }
    }

    /**
     * 学生选课中心。
     */
    private void configureStudentSelect() {
        titleLabel.setText("学生选课中心");
        subtitleLabel.setText("查询课程并完成选课、退课操作");
        sectionLabel.setText("全部课程");
        dataCard.setVisible(true);
        dataCard.setManaged(true);
        dataTable.setVisible(false);
        dataTable.setManaged(false);

        if (courseGrid == null) {
            courseGrid = new TilePane(14, 14);
            courseGrid.setPrefColumns(3);
            dataCard.getChildren().add(courseGrid);
        }

        ComboBox<String> natureBox = new ComboBox<>(
                FXCollections.observableArrayList("全部", "必修", "选修"));
        natureBox.setValue("全部");
        natureBox.getStyleClass().add("academic-combo");
        natureBox.setPrefWidth(100);
        natureBox.setPromptText("课程性质");

        ComboBox<String> fullBox = new ComboBox<>(
                FXCollections.observableArrayList("全部", "未满", "已满"));
        fullBox.setValue("全部");
        fullBox.getStyleClass().add("academic-combo");
        fullBox.setPrefWidth(100);
        fullBox.setPromptText("是否已满");

        ComboBox<String> conflictBox = new ComboBox<>(
                FXCollections.observableArrayList("全部", "冲突", "不冲突"));
        conflictBox.setValue("全部");
        conflictBox.getStyleClass().add("academic-combo");
        conflictBox.setPrefWidth(100);
        conflictBox.setPromptText("是否冲突");

        TextField keywordField = new TextField();
        keywordField.setPromptText("请输入搜索关键词");
        keywordField.getStyleClass().add("modern-input-field");
        keywordField.setPrefWidth(220);
        keywordField.setOnAction(e -> applyFiltersAndRender(natureBox, fullBox, conflictBox, keywordField));

        Button searchBtn = button("搜索", "btn-primary-action");
        searchBtn.setOnAction(e -> applyFiltersAndRender(natureBox, fullBox, conflictBox, keywordField));

        Button myBtn = button("我的已选课程", "btn-recharge-preset");
        myBtn.setOnAction(e -> {
            onlyMy = !onlyMy;
            myBtn.getStyleClass().removeAll("btn-primary-action", "btn-recharge-preset");
            myBtn.getStyleClass().add(onlyMy ? "btn-primary-action" : "btn-recharge-preset");
            applyFiltersAndRender(natureBox, fullBox, conflictBox, keywordField);
        });

        headerControls.getChildren().addAll(
                natureBox, fullBox, conflictBox, keywordField, searchBtn, myBtn);

        loadStudentCourses();
    }

    /**
     * 异步加载全部课程与当前学生已选课程，用于卡片网格展示、冲突和已选标识。
     */
    private void loadStudentCourses() {
        THREAD_POOL.execute(() -> {
            try {
                List<CourseVO> all = academicController.listAllCourses();
                List<CourseVO> mine = academicController.listMyCourses();
                Platform.runLater(() -> {
                    allCourses = all;
                    myCourses = mine;
                    renderCourseGrid();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("加载课程失败：" + e.getMessage()));
            }
        });
    }

    /**
     * 根据过滤条件重新渲染课程卡片网格。
     */
    private void applyFiltersAndRender(ComboBox<String> natureBox,
                                       ComboBox<String> fullBox,
                                       ComboBox<String> conflictBox,
                                       TextField keywordField) {
        if (courseGrid == null) {
            return;
        }
        courseGrid.getChildren().clear();
        String keyword = keywordField.getText() == null ? "" : keywordField.getText().trim();
        String natureFilter = natureBox.getValue() == null ? "全部" : natureBox.getValue();
        String fullFilter = fullBox.getValue() == null ? "全部" : fullBox.getValue();
        String conflictFilter = conflictBox.getValue() == null ? "全部" : conflictBox.getValue();

        List<CourseVO> filtered = new ArrayList<>();
        for (CourseVO course : allCourses) {
            if (passCourseFilter(course, keyword, natureFilter, fullFilter, conflictFilter)) {
                filtered.add(course);
            }
        }
        if (filtered.isEmpty()) {
            Label empty = new Label("未找到符合条件的课程");
            empty.getStyleClass().add("lib-subtitle");
            courseGrid.getChildren().add(empty);
            return;
        }
        for (CourseVO course : filtered) {
            courseGrid.getChildren().add(createCourseCard(course));
        }
    }

    /**
     * 重新根据当前已选与全部课程渲染卡片。
     */
    private void renderCourseGrid() {
        if (courseGrid == null) {
            return;
        }
        courseGrid.getChildren().clear();
        for (CourseVO course : allCourses) {
            courseGrid.getChildren().add(createCourseCard(course));
        }
        if (allCourses.isEmpty()) {
            Label empty = new Label("暂无课程");
            empty.getStyleClass().add("lib-subtitle");
            courseGrid.getChildren().add(empty);
        }
    }

    private boolean passCourseFilter(CourseVO course, String keyword,
                                     String natureFilter, String fullFilter,
                                     String conflictFilter) {
        if (onlyMy && !isSelected(course)) {
            return false;
        }
        if (!keyword.isEmpty()) {
            String target = (course.getCourseName() == null ? "" : course.getCourseName())
                    + (course.getDisplayCode() == null ? "" : course.getDisplayCode())
                    + (course.getTeacherName() == null ? "" : course.getTeacherName())
                    + (course.getLocation() == null ? "" : course.getLocation());
            if (!target.toLowerCase().contains(keyword.toLowerCase())) {
                return false;
            }
        }
        if ("必修".equals(natureFilter) || "选修".equals(natureFilter)) {
            if (!natureFilter.equals(course.getNature())) {
                return false;
            }
        }
        boolean full = course.getSelectedCount() >= course.getCapacity();
        if ("已满".equals(fullFilter) && !full) {
            return false;
        }
        if ("未满".equals(fullFilter) && full) {
            return false;
        }
        boolean conflict = courseConflictsWithSelection(course);
        if ("冲突".equals(conflictFilter) && !conflict) {
            return false;
        }
        if ("不冲突".equals(conflictFilter) && conflict) {
            return false;
        }
        return true;
    }

    private boolean isSelected(CourseVO course) {
        for (CourseVO mine : myCourses) {
            if (mine.getCourseCode() != null && mine.getCourseCode().equals(course.getCourseCode())) {
                return true;
            }
        }
        return false;
    }

    private boolean courseConflictsWithSelection(CourseVO course) {
        List<CourseTimeSlotVO> slots = effectiveSlots(course);
        for (CourseVO mine : myCourses) {
            if (mine.getCourseCode() != null && mine.getCourseCode().equals(course.getCourseCode())) {
                continue;
            }
            List<CourseTimeSlotVO> mineSlots = effectiveSlots(mine);
            if (slotsOverlap(slots, mineSlots)) {
                return true;
            }
        }
        return false;
    }

    private boolean slotsOverlap(List<CourseTimeSlotVO> a, List<CourseTimeSlotVO> b) {
        for (CourseTimeSlotVO slotA : a) {
            for (CourseTimeSlotVO slotB : b) {
                if (slotOverlap(slotA, slotB)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean slotOverlap(CourseTimeSlotVO a, CourseTimeSlotVO b) {
        if (a.getDay() == null || !a.getDay().equals(b.getDay())) {
            return false;
        }
        if (a.getEndWeek() < b.getStartWeek() || b.getEndWeek() < a.getStartWeek()) {
            return false;
        }
        return a.getStartPeriod() <= b.getEndPeriod() && b.getStartPeriod() <= a.getEndPeriod();
    }

    private List<CourseTimeSlotVO> effectiveSlots(CourseVO course) {
        if (course.getTimeSlots() != null && !course.getTimeSlots().isEmpty()) {
            return course.getTimeSlots();
        }
        if (course.getClassTime() != null && !course.getClassTime().trim().isEmpty()) {
            CourseVO temp = new CourseVO();
            temp.setStartWeek(course.getStartWeek());
            temp.setEndWeek(course.getEndWeek());
            temp.parseScheduleText(course.getClassTime());
            return temp.getTimeSlots();
        }
        return new ArrayList<>();
    }

    /**
     * 构建单个课程卡片。
     */
    private Node createCourseCard(CourseVO course) {
        VBox card = new VBox(8);
        card.setPrefWidth(300);
        card.getStyleClass().add("course-card");

        boolean selected = isSelected(course);
        boolean full = course.getSelectedCount() >= course.getCapacity();
        boolean conflict = courseConflictsWithSelection(course);
        if (selected) {
            card.getStyleClass().add("selected");
        }

        Label nameLabel = new Label(course.getCourseName() == null ? "" : course.getCourseName());
        nameLabel.getStyleClass().add("course-card-title");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        VBox timeBox = new VBox(3);
        List<CourseTimeSlotVO> slots = effectiveSlots(course);
        if (slots.isEmpty()) {
            Label pending = new Label("上课时间待安排");
            pending.getStyleClass().add("course-time");
            timeBox.getChildren().add(pending);
        } else {
            for (CourseTimeSlotVO slot : slots) {
                Label timeLabel = new Label(slot.getStartWeek() + "-" + slot.getEndWeek() + "周 "
                        + slot.getDay() + " 第" + slot.getStartPeriod()
                        + "-" + slot.getEndPeriod() + "节");
                timeLabel.getStyleClass().add("course-time");
                timeBox.getChildren().add(timeLabel);
            }
        }

        Label teacherLabel = new Label((course.getTeacherName() == null ? "" : course.getTeacherName())
                + "  " + (course.getLocation() == null ? "" : course.getLocation()));
        teacherLabel.getStyleClass().add("course-teacher");

        HBox badgeRow = new HBox(6);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        if (conflict) {
            badgeRow.getChildren().add(badge("冲突", "badge-conflict"));
        }
        if (selected) {
            badgeRow.getChildren().add(badge("已选", "badge-selected"));
        }
        badgeRow.getChildren().add(badge(course.getNature() == null ? "选修" : course.getNature(),
                "badge-nature"));

        Label countLabel = new Label("已选 " + course.getSelectedCount() + "/" + course.getCapacity());
        countLabel.getStyleClass().add("course-count");
        badgeRow.getChildren().add(countLabel);

        Button actionBtn;
        if (selected) {
            actionBtn = button("退课", "lib-btn-danger");
            actionBtn.setOnAction(e -> runAction("退课",
                    () -> academicController.dropCourse(course.getCourseCode()),
                    this::loadStudentCourses));
        } else {
            actionBtn = button(full ? "已满" : "选课", "btn-primary-action");
            actionBtn.setDisable(full || conflict);
            if (!full && !conflict) {
                actionBtn.setOnAction(e -> runAction("选课",
                        () -> academicController.selectCourse(course.getCourseCode()),
                        this::loadStudentCourses));
            }
        }

        card.getChildren().addAll(nameLabel, timeBox, teacherLabel, badgeRow, actionBtn);
        return card;
    }

    private Label badge(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("course-badge", styleClass);
        return label;
    }

    /**
     * 学生成绩查询。
     */
    private void configureStudentGrade() {
        titleLabel.setText("我的成绩");
        subtitleLabel.setText("查看当前学生的各科成绩与绩点");
        sectionLabel.setText("成绩列表");
        buildGradeColumns();

        Button refreshBtn = button("刷新成绩", "btn-primary-action");
        refreshBtn.setOnAction(e -> fetchGrades(() -> academicController.queryMyGrades()));
        headerControls.getChildren().add(refreshBtn);
    }

    /**
     * 学生可视化课表，周一至周日，每天 13 节课。
     */
    private void configureTimetable(String title, String subtitle,
                                    Supplier<List<CourseVO>> courseLoader) {
        titleLabel.setText(title);
        subtitleLabel.setText(subtitle);
        sectionLabel.setText("课表");
        dataCard.setVisible(true);
        dataCard.setManaged(true);
        dataTable.setVisible(false);
        dataTable.setManaged(false);

        formCard.setVisible(false);
        formCard.setManaged(false);

        ComboBox<Integer> weekBox = new ComboBox<>();
        weekBox.getStyleClass().add("academic-combo");
        weekBox.setPrefWidth(90);
        weekBox.setPromptText("第几周");
        headerControls.getChildren().add(weekBox);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("timetable-grid");
        grid.setHgap(6);
        grid.setVgap(6);
        grid.setPadding(new Insets(12));

        ColumnConstraints timeCol = new ColumnConstraints(60);
        grid.getColumnConstraints().add(timeCol);
        for (int day = 0; day < 7; day++) {
            ColumnConstraints dayCol = new ColumnConstraints();
            dayCol.setMinWidth(110);
            dayCol.setPrefWidth(110);
            dayCol.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(dayCol);
        }
        RowConstraints headerRow = new RowConstraints(40);
        grid.getRowConstraints().add(headerRow);
        for (int period = 1; period <= 13; period++) {
            RowConstraints row = new RowConstraints();
            row.setMinHeight(88);
            row.setPrefHeight(88);
            grid.getRowConstraints().add(row);
        }

        Label corner = new Label("节次");
        corner.getStyleClass().add("timetable-period-label");
        corner.setAlignment(Pos.CENTER);
        grid.add(corner, 0, 0);

        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int day = 0; day < 7; day++) {
            Label dayLabel = new Label(days[day]);
            dayLabel.getStyleClass().add("timetable-day-header");
            dayLabel.setAlignment(Pos.CENTER);
            grid.add(dayLabel, day + 1, 0);
        }

        String[] times = periodTimes();
        for (int period = 1; period <= 13; period++) {
            Label periodNum = new Label(String.valueOf(period));
            periodNum.getStyleClass().add("timetable-period-num");
            periodNum.setAlignment(Pos.CENTER);
            Label timeLabel = new Label(times[period - 1]);
            timeLabel.getStyleClass().add("timetable-period-time");
            timeLabel.setAlignment(Pos.CENTER);
            VBox timeCell = new VBox(2, periodNum, timeLabel);
            timeCell.setAlignment(Pos.CENTER);
            grid.add(timeCell, 0, period);
        }

        // 叠加虚线网格单元格，作为课表对齐背景
        for (int day = 0; day < 7; day++) {
            for (int period = 1; period <= 13; period++) {
                Region cell = new Region();
                cell.getStyleClass().add("timetable-cell");
                grid.add(cell, day + 1, period);
            }
        }

        dataCard.getChildren().remove(dataTable);
        dataCard.getChildren().add(grid);

        loadStudentTimetable(grid, weekBox, courseLoader);
    }

    private void loadStudentTimetable(GridPane grid, ComboBox<Integer> weekBox,
                                      Supplier<List<CourseVO>> courseLoader) {
        THREAD_POOL.execute(() -> {
            try {
                List<CourseVO> courses = courseLoader.get();
                Platform.runLater(() -> {
                    int maxWeek = computeMaxWeek(courses);
                    List<Integer> weeks = buildWeekNumbers(maxWeek);
                    weekBox.setItems(FXCollections.observableArrayList(weeks));
                    weekBox.setValue(weeks.isEmpty() ? 1 : weeks.get(0));
                    weekBox.valueProperty().addListener((obs, oldValue, newValue) -> {
                        if (newValue != null) {
                            renderStudentTimetable(grid, courses, newValue);
                        }
                    });
                    renderStudentTimetable(grid, courses, weekBox.getValue());
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("加载课表失败：" + e.getMessage()));
            }
        });
    }

    private int computeMaxWeek(List<CourseVO> courses) {
        int maxWeek = 1;
        if (courses != null) {
            for (CourseVO course : courses) {
                for (CourseTimeSlotVO slot : effectiveSlots(course)) {
                    maxWeek = Math.max(maxWeek, slot.getEndWeek());
                }
            }
        }
        return Math.max(1, maxWeek);
    }

    private void renderStudentTimetable(GridPane grid, List<CourseVO> courses, int selectedWeek) {
        // 清除之前可能添加的课程卡片，保留左侧节次/时间标签与表头
        for (javafx.scene.Node node : new ArrayList<>(grid.getChildren())) {
            if (node.getStyleClass().contains("timetable-block")) {
                grid.getChildren().remove(node);
            }
        }
        if (courses == null || courses.isEmpty()) {
            Label empty = new Label("暂无课程，请先选课");
            empty.getStyleClass().add("lib-subtitle");
            grid.add(empty, 1, 1);
            return;
        }
        for (CourseVO course : courses) {
            for (CourseTimeSlotVO slot : effectiveSlots(course)) {
                if (slot == null) {
                    continue;
                }
                if (selectedWeek < slot.getStartWeek() || selectedWeek > slot.getEndWeek()) {
                    continue;
                }
                int dayIndex = dayIndex(slot.getDay());
                int startPeriod = Math.max(1, Math.min(13, slot.getStartPeriod()));
                int endPeriod = Math.max(startPeriod, Math.min(13, slot.getEndPeriod()));
                if (dayIndex < 0) {
                    continue;
                }
                VBox block = createTimetableBlock(course, slot);
                grid.add(block, dayIndex + 1, startPeriod);
                GridPane.setRowSpan(block, endPeriod - startPeriod + 1);
            }
        }
    }

    private VBox createTimetableBlock(CourseVO course, CourseTimeSlotVO slot) {
        VBox block = new VBox(2);
        String colorKey = course.getDisplayCode() == null ? course.getCourseName() : course.getDisplayCode();
        int colorIndex = Math.floorMod((colorKey == null ? "" : colorKey).hashCode(), 7);
        block.getStyleClass().addAll("timetable-block", "course-color-" + colorIndex);
        block.setAlignment(Pos.CENTER);

        Label name = new Label(course.getCourseName() == null ? "" : course.getCourseName());
        name.getStyleClass().add("timetable-block-name");
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setAlignment(Pos.CENTER);
        name.setTextAlignment(TextAlignment.CENTER);

        Label week = new Label(slot.getStartWeek() + "-" + slot.getEndWeek() + "周");
        week.getStyleClass().add("timetable-block-text");
        week.setTextAlignment(TextAlignment.CENTER);

        Label period = new Label("第" + slot.getStartPeriod() + "-" + slot.getEndPeriod() + "节");
        period.getStyleClass().addAll("timetable-block-period");
        period.setTextAlignment(TextAlignment.CENTER);

        String slotLocation = slot.getLocation();
        if (slotLocation == null || slotLocation.trim().isEmpty()) {
            slotLocation = course.getLocation();
        }
        Label room = new Label(slotLocation == null || slotLocation.trim().isEmpty()
                ? "地点待定" : slotLocation);
        room.getStyleClass().add("timetable-block-text");
        room.setTextAlignment(TextAlignment.CENTER);

        Label teacher = new Label(course.getTeacherName() == null ? "" : course.getTeacherName());
        teacher.getStyleClass().add("timetable-block-text");
        teacher.setTextAlignment(TextAlignment.CENTER);

        block.getChildren().addAll(name, week, period, room, teacher);
        block.setOnMouseClicked(e -> showCourseDetail(course, slot));
        return block;
    }

    private void showCourseDetail(CourseVO course, CourseTimeSlotVO slot) {
        StringBuilder info = new StringBuilder();
        info.append("课程名称：").append(nvl(course.getCourseName())).append("\n");
        info.append("课程代码：").append(nvl(course.getDisplayCode())).append("\n");
        info.append("课程性质：").append(nvl(course.getNature())).append("\n");
        info.append("学分：").append(course.getCredit()).append("\n");
        info.append("授课教师：").append(nvl(course.getTeacherName())).append("\n");
        String slotLocation = slot.getLocation();
        if (slotLocation == null || slotLocation.trim().isEmpty()) {
            slotLocation = course.getLocation();
        }
        info.append("上课地点：").append(nvl(slotLocation)).append("\n");
        info.append("开课学期：").append(nvl(course.getSemester())).append("\n");
        info.append("上课周次：").append(slot.getStartWeek()).append("-").append(slot.getEndWeek()).append("周\n");
        info.append("上课时间：").append(slot.getDay()).append(" 第").append(slot.getStartPeriod())
                .append("-").append(slot.getEndPeriod()).append("节\n");
        info.append("已选人数：").append(course.getSelectedCount()).append("/").append(course.getCapacity()).append("\n");
        showInfo(info.toString());
    }

    private int dayIndex(String day) {
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < days.length; i++) {
            if (days[i].equals(day)) {
                return i;
            }
        }
        return -1;
    }

    private String[] periodTimes() {
        return new String[]{
                "08:00-08:45", "08:50-09:35", "09:50-10:35", "10:40-11:25",
                "11:30-12:15", "14:00-14:45", "14:50-15:35", "15:50-16:35",
                "16:40-17:25", "17:30-18:15", "19:00-19:45", "19:50-20:35",
                "20:40-21:25"
        };
    }

    private String nvl(String value) {
        return value == null || value.trim().isEmpty() ? "待定" : value;
    }

    /**
     * 学生课程评价模块：选择课程后评分、评论并查看历史评价。
     */
    private void configureCourseReview() {
        titleLabel.setText("课程评价");
        subtitleLabel.setText("为已选课程评分并发表评论，查看其他同学的评价");
        sectionLabel.setText("课程列表");
        buildCourseColumns();

        Label selectedCourseLabel = new Label("尚未选择课程");
        selectedCourseLabel.getStyleClass().add("lib-subtitle");
        selectedCourseLabel.setWrapText(true);
        selectedCourseLabel.setMaxWidth(Double.MAX_VALUE);

        int[] selectedRating = {0};
        List<Button> starButtons = new ArrayList<>();
        HBox starBox = new HBox(6);
        starBox.setAlignment(Pos.CENTER_LEFT);
        for (int i = 1; i <= 5; i++) {
            final int rating = i;
            Button star = button(rating + "★", "btn-recharge-preset");
            star.setOnAction(e -> {
                selectedRating[0] = rating;
                for (int j = 0; j < starButtons.size(); j++) {
                    starButtons.get(j).getStyleClass().removeAll(
                            "btn-primary-action", "btn-recharge-preset");
                    starButtons.get(j).getStyleClass().add(
                            j < rating ? "btn-primary-action" : "btn-recharge-preset");
                }
            });
            starButtons.add(star);
            starBox.getChildren().add(star);
        }

        TextArea commentArea = new TextArea();
        commentArea.setPromptText("写下你的课程评价");
        commentArea.setWrapText(true);
        commentArea.setPrefRowCount(3);
        commentArea.getStyleClass().add("modern-input-field");

        CheckBox anonymousBox = new CheckBox("匿名评论");
        anonymousBox.getStyleClass().add("lib-form-label");

        VBox reviewsBox = new VBox(8);

        Button submitBtn = button("提交评价", "btn-primary-action");
        submitBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先选择课程");
                return;
            }
            if (selectedRating[0] < 1 || selectedRating[0] > 5) {
                showInfo("请选择 1-5 星评分");
                return;
            }
            CourseReviewVO review = new CourseReviewVO();
            review.setStudentId(currentUser.getUid());
            review.setCourseId(course.getCourseCode());
            review.setCourseName(course.getCourseName());
            review.setRating(selectedRating[0]);
            review.setComment(commentArea.getText() == null ? "" : commentArea.getText().trim());
            review.setAnonymous(anonymousBox.isSelected());
            runAction("提交课程评价", () -> academicController.submitReview(review),
                    () -> loadCourseReviews(course.getCourseCode(), reviewsBox));
        });

        dataTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                selectedCourseLabel.setText("尚未选择课程");
                reviewsBox.getChildren().clear();
                return;
            }
            selectedCourseLabel.setText(course.getDisplayCode() + " - " + course.getCourseName());
            loadCourseReviews(course.getCourseCode(), reviewsBox);
        });

        Button loadBtn = button("加载全部课程", "btn-recharge-preset");
        loadBtn.setOnAction(e -> fetchCourses(() -> academicController.listAllCourses()));
        headerControls.getChildren().add(loadBtn);

        Label formTitle = new Label("发表评价");
        formTitle.getStyleClass().add("lib-section-title");
        Label listTitle = new Label("历史评价");
        listTitle.getStyleClass().add("lib-section-title");

        formCard.setVisible(true);
        formCard.setManaged(true);
        formContent.getChildren().addAll(
                selectedCourseLabel,
                formRow(labeledField("评分", starBox), submitBtn),
                labeledField("评论", commentArea),
                anonymousBox,
                listTitle,
                reviewsBox);

        fetchCourses(() -> academicController.listAllCourses());
    }

    /**
     * 异步加载课程评价。
     */
    private void loadCourseReviews(String courseCode, VBox reviewsBox) {
        THREAD_POOL.execute(() -> {
            try {
                List<CourseReviewVO> reviews = academicController.listReviews(courseCode);
                Platform.runLater(() -> renderCourseReviews(reviewsBox, reviews));
            } catch (Exception e) {
                Platform.runLater(() -> showError("加载评价失败：" + e.getMessage()));
            }
        });
    }

    /**
     * 渲染课程评价列表与总评分。
     */
    private void renderCourseReviews(VBox reviewsBox, List<CourseReviewVO> reviews) {
        reviewsBox.getChildren().clear();
        if (reviews == null || reviews.isEmpty()) {
            Label empty = new Label("暂无评价");
            empty.getStyleClass().add("lib-subtitle");
            reviewsBox.getChildren().add(empty);
            return;
        }
        int total = 0;
        for (CourseReviewVO review : reviews) {
            total += review.getRating();
        }
        double avg = total * 1.0 / reviews.size();
        Label summary = new Label("总评分：" + String.format("%.1f", avg)
                + " / 5（" + reviews.size() + " 人评分）");
        summary.getStyleClass().add("review-summary");
        reviewsBox.getChildren().add(summary);

        for (CourseReviewVO review : reviews) {
            VBox item = new VBox(3);
            item.getStyleClass().add("review-item");
            String reviewerName = review.isAnonymous() ? "匿名学生" : nvl(review.getStudentName());
            Label head = new Label(reviewerName
                    + "  评分：" + review.getRating() + " 星");
            head.getStyleClass().add("review-head");
            Label comment = new Label(review.getComment() == null ? "" : review.getComment());
            comment.setWrapText(true);
            comment.getStyleClass().add("review-comment");
            Label time = new Label(review.getReviewTime() == null ? "" : review.getReviewTime());
            time.getStyleClass().add("review-time");
            HBox headRow = new HBox(8, head);
            headRow.setAlignment(Pos.CENTER_LEFT);
            if (currentUser != null && currentUser.getUid().equals(review.getStudentId())) {
                Button deleteBtn = button("删除", "lib-btn-danger");
                deleteBtn.setOnAction(e -> runAction("删除评价",
                        () -> academicController.deleteReview(review.getCourseId()),
                        () -> loadCourseReviews(review.getCourseId(), reviewsBox)));
                headRow.getChildren().add(deleteBtn);
            }
            item.getChildren().addAll(headRow, comment, time);
            reviewsBox.getChildren().add(item);
        }
    }

    /**
     * 教师查看自己所教授课程的学生评分与评论。
     */
    private void configureTeacherCourseReview() {
        titleLabel.setText("课程评分");
        subtitleLabel.setText("查看自己教授课程的学生评分与评论");
        sectionLabel.setText("我的课程");
        buildCourseColumns();

        Label selectedCourseLabel = new Label("尚未选择课程");
        selectedCourseLabel.getStyleClass().add("lib-subtitle");
        selectedCourseLabel.setWrapText(true);
        selectedCourseLabel.setMaxWidth(Double.MAX_VALUE);
        VBox reviewsBox = new VBox(8);

        Button loadBtn = button("加载我的课程", "btn-primary-action");
        loadBtn.setOnAction(e ->
                fetchCourses(() -> academicController.queryByTeacher(currentUser.getUid())));
        headerControls.getChildren().add(loadBtn);

        dataTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                selectedCourseLabel.setText("尚未选择课程");
                reviewsBox.getChildren().clear();
                return;
            }
            selectedCourseLabel.setText(course.getDisplayCode() + " - " + course.getCourseName());
            loadCourseReviews(course.getCourseCode(), reviewsBox);
        });

        Label listTitle = new Label("课程评价");
        listTitle.getStyleClass().add("lib-section-title");
        formCard.setVisible(true);
        formCard.setManaged(true);
        formContent.getChildren().addAll(selectedCourseLabel, listTitle, reviewsBox);

        fetchCourses(() -> academicController.queryByTeacher(currentUser.getUid()));
    }

    /**
     * 教师课程管理。
     */
    private void configureTeacherCourse() {
        titleLabel.setText("课程管理");
        subtitleLabel.setText("提交课程审批、查询本人课程并停开课程");
        sectionLabel.setText("我的课程");
        buildCourseColumns();

        TextField codeField = inputField("课程代码", 120);
        TextField nameField = inputField("课程名称", 140);
        TextField creditField = inputField("学分", 80);
        TextField capacityField = inputField("容量", 80);
        ComboBox<String> natureBox = new ComboBox<>(FXCollections.observableArrayList("必修", "选修"));
        natureBox.setPrefWidth(100);
        natureBox.getStyleClass().add("academic-combo");
        natureBox.setPromptText("课程性质");
        natureBox.setValue("选修");
        ComboBox<String> semesterYearBox = createAcademicYearComboBox();
        ComboBox<Integer> semesterNoBox = createSemesterComboBox();
        semesterYearBox.setPromptText("选择学年");
        semesterNoBox.setPromptText("学期");
        VBox componentRows = new VBox(8);
        Label componentTotalLabel = new Label("当前比例合计：0.0");
        componentTotalLabel.getStyleClass().add("lib-msg-label");
        Button addComponentBtn = button("新建组成", "btn-recharge-preset");
        addComponentBtn.setOnAction(e -> addComponentRow(componentRows, componentTotalLabel));

        Label hint = new Label(
                "填写说明：课程代码和名称请勿使用空格或特殊符号；"
                        + "学分为数字；容量为整数；学年和学期请从下拉框选择；"
                        + "上课时间无需填写，由教务老师审核后统一安排；"
                        + "请点击“新建组成”添加成绩类别与组成比例，所有比例之和必须等于 1。");
        hint.getStyleClass().add("academic-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);

        Button addBtn = button("提交课程审批", "btn-primary-action");
        addBtn.setOnAction(e -> {
            List<ScoreComponentVO> components = parseComponentRows(componentRows, componentTotalLabel);
            if (components == null) {
                return;
            }
            submitCourse(codeField, nameField, creditField, capacityField,
                    natureBox, semesterYearBox, semesterNoBox, components);
        });

        Button queryBtn = button("查询我的课程", "btn-recharge-preset");
        queryBtn.setOnAction(e -> fetchCourses(() -> academicController.queryByTeacher(currentUser.getUid())));

        Button disableBtn = button("停开选中课程", "lib-btn-danger");
        disableBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先选择课程");
                return;
            }
            runAction("停开课程", () -> academicController.disableCourse(course.getCourseCode()),
                    () -> fetchCourses(() -> academicController.queryByTeacher(currentUser.getUid())));
        });

        headerControls.getChildren().addAll(queryBtn, disableBtn);
        formCard.setVisible(true);
        formCard.setManaged(true);

        formContent.getChildren().addAll(
                hint,
                formRow(labeledField("课程代码", codeField), labeledField("课程名称", nameField),
                        labeledField("学分", creditField), labeledField("容量", capacityField)),
                formRow(labeledField("性质", natureBox),
                        labeledField("学年", semesterYearBox),
                        labeledField("学期", semesterNoBox)),
                formRow(addComponentBtn, componentTotalLabel),
                componentRows,
                addBtn);

        addComponentRow(componentRows, componentTotalLabel);
    }

    /**
     * 教师成绩登记。
     */
    private void configureGradeSubmit() {
        titleLabel.setText("成绩登记");
        subtitleLabel.setText("选择课程后，按该课程已审批的成绩组成录入学生成绩");
        sectionLabel.setText("我的课程");
        buildCourseColumns();

        Button loadBtn = button("加载我的课程", "btn-primary-action");
        loadBtn.setOnAction(e -> fetchCourses(() -> academicController.queryByTeacher(currentUser.getUid())));

        TextField studentIdField = inputField("学生学号", 160);
        VBox scoreInputs = new VBox(6);
        Label calcLabel = new Label("实时计算：等待输入");
        calcLabel.getStyleClass().add("academic-hint");
        calcLabel.setWrapText(true);
        calcLabel.setMaxWidth(Double.MAX_VALUE);

        dataTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
                rebuildScoreInputs(scoreInputs, calcLabel));

        Button submitBtn = button("提交成绩", "btn-primary-action");
        submitBtn.setOnAction(e -> submitGrade(studentIdField, scoreInputs));

        headerControls.getChildren().add(loadBtn);
        formCard.setVisible(true);
        formCard.setManaged(true);
        formContent.getChildren().addAll(
                formRow(labeledField("学生学号", studentIdField)),
                scoreInputs,
                calcLabel,
                submitBtn);

        fetchCourses(() -> academicController.queryByTeacher(currentUser.getUid()));
    }

    /**
     * 教务老师全校课表与课程查询。
     */
    private void configureAcademicManage() {
        titleLabel.setText("全校课表与课程管理");
        subtitleLabel.setText("查询全校课程，并管理课程多个上课时间段");
        sectionLabel.setText("全校课程");
        buildCourseColumns();

        TextField teacherField = inputField("教师工号", 140);
        ComboBox<String> queryYearBox = createAcademicYearComboBox();
        ComboBox<Integer> querySemesterBox = createSemesterComboBox();
        queryYearBox.setPromptText("选择学年");
        querySemesterBox.setPromptText("学期");

        Label selectedCourseLabel = new Label("尚未选择课程");
        selectedCourseLabel.getStyleClass().add("lib-subtitle");
        selectedCourseLabel.setWrapText(true);
        selectedCourseLabel.setMaxWidth(Double.MAX_VALUE);
        VBox scheduleRows = new VBox(8);
        TextField locationField = inputField("上课地点", 260);
        locationField.setPromptText("例如：九龙湖计算机楼101");

        Button allBtn = button("全部课程", "btn-recharge-preset");
        allBtn.setOnAction(e -> fetchCourses(() -> academicController.listAllCourses()));

        Button teacherBtn = button("按教师查询", "btn-primary-action");
        teacherBtn.setOnAction(e -> fetchCourses(() -> academicController.queryByTeacher(teacherField.getText().trim())));

        Button semesterBtn = button("按学期查询", "btn-primary-action");
        semesterBtn.setOnAction(e -> {
            if (queryYearBox.getValue() == null || querySemesterBox.getValue() == null) {
                showInfo("请选择学年和学期");
                return;
            }
            String semester = buildSemester(queryYearBox.getValue(), querySemesterBox.getValue());
            fetchCourses(() -> academicController.queryBySemester(semester));
        });

        Button deleteBtn = button("删除选中课程", "lib-btn-danger");
        deleteBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先在下方课程列表中选择一门课程");
                return;
            }
            if (!confirm("删除课程", "确定删除课程“" + course.getCourseName() + "”吗？"
                    + "删除后相关选课与成绩记录也会一并删除。")) {
                return;
            }
            runAction("删除课程",
                    () -> academicController.deleteCourse(course.getCourseCode()),
                    () -> fetchCourses(() -> academicController.listAllCourses()));
        });

        Button addSlotBtn = button("添加时间段", "btn-recharge-preset");
        addSlotBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先在下方课程列表中选择一门课程");
                return;
            }
            int maxWeek = buildWeekOptions(course.getSemester()).size();
            CourseTimeSlotVO defaultSlot = null;
            if (!scheduleRows.getChildren().isEmpty()) {
                javafx.scene.Node lastNode = scheduleRows.getChildren()
                        .get(scheduleRows.getChildren().size() - 1);
                if (lastNode instanceof HBox && lastNode.getUserData() instanceof ScheduleRow) {
                    defaultSlot = ((ScheduleRow) lastNode.getUserData()).snapshot();
                }
            }
            scheduleRows.getChildren().add(
                    new ScheduleRow(defaultSlot, maxWeek, course.getLocation()).getNode());
        });

        Button saveScheduleBtn = button("保存全部时间段", "btn-primary-action");
        saveScheduleBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先在下方课程列表中选择一门课程");
                return;
            }
            saveSchedule(course, scheduleRows);
        });

        Button saveLocationBtn = button("保存上课地点", "btn-primary-action");
        saveLocationBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先在下方课程列表中选择一门课程");
                return;
            }
            if (locationField.getText().trim().isEmpty()) {
                showInfo("请填写上课地点");
                return;
            }
            runAction("保存上课地点",
                    () -> academicController.scheduleCourseLocation(
                            course.getCourseCode(), locationField.getText().trim()),
                    () -> fetchCourses(() -> academicController.listAllCourses()));
        });

        dataTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            CourseVO course = selectedCourse();
            scheduleRows.getChildren().clear();
            if (course == null) {
                selectedCourseLabel.setText("尚未选择课程");
                locationField.setText("");
                return;
            }
            selectedCourseLabel.setText(course.getDisplayCode() + " - " + course.getCourseName());
            locationField.setText(course.getLocation() == null ? "" : course.getLocation());
            int maxWeek = buildWeekOptions(course.getSemester()).size();
            List<CourseTimeSlotVO> slots = course.getTimeSlots();
            if (slots == null || slots.isEmpty()) {
                scheduleRows.getChildren().add(
                        new ScheduleRow(null, maxWeek, course.getLocation()).getNode());
            } else {
                for (CourseTimeSlotVO slot : slots) {
                    scheduleRows.getChildren().add(
                            new ScheduleRow(slot, maxWeek, course.getLocation()).getNode());
                }
            }
        });

        headerControls.getChildren().addAll(
                teacherField, queryYearBox, querySemesterBox, allBtn, teacherBtn, semesterBtn, deleteBtn);

        Label scheduleHint = new Label("一个课程可以添加多个上课时间段。"
                + "每个时间段包含起始周、结束周、星期、起始节次和结束节次。"
                + "第 1 学期可选 1-4 周，第 2、3 学期可选 1-18 周。");
        scheduleHint.getStyleClass().add("academic-hint");
        scheduleHint.setWrapText(true);
        scheduleHint.setMaxWidth(Double.MAX_VALUE);

        Label selectedTitle = new Label("已选课程");
        selectedTitle.getStyleClass().add("lib-form-label");
        VBox selectedCourseBox = new VBox(4, selectedTitle, selectedCourseLabel);

        formCard.setVisible(true);
        formCard.setManaged(true);
        formContent.getChildren().addAll(
                scheduleHint,
                selectedCourseBox,
                formRow(labeledField("上课地点", locationField), saveLocationBtn),
                scheduleRows,
                formRow(addSlotBtn, saveScheduleBtn));
    }

    /**
     * 收集课程管理页中的所有时间段并保存。
     */
    private void saveSchedule(CourseVO course, VBox scheduleRows) {
        List<CourseTimeSlotVO> slots = new ArrayList<>();
        int minWeek = Integer.MAX_VALUE;
        int maxWeek = 0;

        for (javafx.scene.Node node : scheduleRows.getChildren()) {
            if (!(node instanceof HBox)) {
                continue;
            }
            ScheduleRow row = (ScheduleRow) node.getUserData();
            if (row == null || !row.isComplete()) {
                showInfo("请完整填写每个时间段");
                return;
            }
            if (!row.hasValidRange()) {
                showInfo("结束周不能早于起始周，结束节次不能早于起始节次");
                return;
            }
            CourseTimeSlotVO slot = row.toSlot();
            slots.add(slot);
            minWeek = Math.min(minWeek, slot.getStartWeek());
            maxWeek = Math.max(maxWeek, slot.getEndWeek());
        }

        if (slots.isEmpty()) {
            showInfo("请至少添加一个上课时间段");
            return;
        }

        CourseVO temp = new CourseVO();
        temp.setTimeSlots(slots);
        String scheduleText = temp.toScheduleText();
        final int savedMinWeek = minWeek;
        final int savedMaxWeek = maxWeek;

        runAction("保存课程时间", () -> {
            ResponseCode timeCode = academicController.scheduleCourseTime(course.getCourseCode(), scheduleText);
            if (timeCode != ResponseCode.SUCCESS) {
                return timeCode;
            }
            return academicController.scheduleCourseWeeks(course.getCourseCode(), savedMinWeek, savedMaxWeek);
        }, () -> fetchCourses(() -> academicController.listAllCourses()));
    }

    /**
     * 教务老师调课管理：查看调整前时间段，编辑调整后时间段并同步地点。
     */
    private void configureCourseAdjust() {
        titleLabel.setText("调课管理");
        subtitleLabel.setText("选中课程后，查看调整前时间段，编辑调整后时间段与上课地点");
        sectionLabel.setText("课程列表");
        buildCourseColumns();

        TextField teacherField = inputField("教师工号", 140);
        VBox beforeBox = new VBox(4);
        VBox afterRows = new VBox(6);

        Button allBtn = button("全部课程", "btn-recharge-preset");
        allBtn.setOnAction(e -> fetchCourses(() -> academicController.listAllCourses()));

        Button teacherBtn = button("按教师查询", "btn-primary-action");
        teacherBtn.setOnAction(e ->
                fetchCourses(() -> academicController.queryByTeacher(teacherField.getText().trim())));

        Button addAfterBtn = button("添加调整后时间段", "btn-recharge-preset");
        addAfterBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先选择课程");
                return;
            }
            int maxWeek = buildWeekOptions(course.getSemester()).size();
            afterRows.getChildren().add(
                    new ScheduleRow(null, maxWeek, course.getLocation()).getNode());
        });

        Button saveBtn = button("保存调课", "btn-primary-action");
        saveBtn.setOnAction(e -> saveAdjustment(selectedCourse(), afterRows));

        dataTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            CourseVO course = selectedCourse();
            beforeBox.getChildren().clear();
            afterRows.getChildren().clear();
            if (course == null) {
                return;
            }
            for (CourseTimeSlotVO slot : effectiveSlots(course)) {
                Label slotLabel = new Label(slot.getStartWeek() + "-" + slot.getEndWeek() + "周 "
                        + slot.getDay() + " 第" + slot.getStartPeriod()
                        + "-" + slot.getEndPeriod() + "节");
                slotLabel.getStyleClass().add("course-time");
                beforeBox.getChildren().add(slotLabel);
            }
            int maxWeek = buildWeekOptions(course.getSemester()).size();
            List<CourseTimeSlotVO> slots = course.getTimeSlots();
            if (slots == null || slots.isEmpty()) {
                afterRows.getChildren().add(
                        new ScheduleRow(null, maxWeek, course.getLocation()).getNode());
            } else {
                for (CourseTimeSlotVO slot : slots) {
                    afterRows.getChildren().add(
                            new ScheduleRow(slot, maxWeek, course.getLocation()).getNode());
                }
            }
        });

        headerControls.getChildren().addAll(teacherField, allBtn, teacherBtn);

        Label beforeTitle = new Label("调整前");
        beforeTitle.getStyleClass().add("lib-section-title");
        Label afterTitle = new Label("调整后");
        afterTitle.getStyleClass().add("lib-section-title");

        formCard.setVisible(true);
        formCard.setManaged(true);
        formContent.getChildren().addAll(
                beforeTitle,
                beforeBox,
                afterTitle,
                afterRows,
                formRow(addAfterBtn, saveBtn));

        fetchCourses(() -> academicController.listAllCourses());
    }

    /**
     * 保存调课：更新调整后时间段和上课地点。
     */
    private void saveAdjustment(CourseVO course, VBox afterRows) {
        if (course == null) {
            showInfo("请先选择课程");
            return;
        }
        List<CourseTimeSlotVO> slots = collectScheduleRows(afterRows);
        if (slots == null) {
            return;
        }
        if (slots.isEmpty()) {
            showInfo("请至少保留一个上课时间段");
            return;
        }
        CourseVO temp = new CourseVO();
        temp.setTimeSlots(slots);
        String scheduleText = temp.toScheduleText();
        int minWeek = Integer.MAX_VALUE;
        int maxWeek = 0;
        for (CourseTimeSlotVO slot : slots) {
            minWeek = Math.min(minWeek, slot.getStartWeek());
            maxWeek = Math.max(maxWeek, slot.getEndWeek());
        }
        final int savedMinWeek = minWeek;
        final int savedMaxWeek = maxWeek;
        final String firstLocation = slots.get(0).getLocation();

        runAction("保存调课", () -> {
            ResponseCode timeCode = academicController.scheduleCourseTime(course.getCourseCode(), scheduleText);
            if (timeCode != ResponseCode.SUCCESS) {
                return timeCode;
            }
            ResponseCode weekCode = academicController.scheduleCourseWeeks(
                    course.getCourseCode(), savedMinWeek, savedMaxWeek);
            if (weekCode != ResponseCode.SUCCESS) {
                return weekCode;
            }
            return academicController.scheduleCourseLocation(course.getCourseCode(), firstLocation);
        }, () -> fetchCourses(() -> academicController.listAllCourses()));
    }

    /**
     * 收集并校验调课时间段行，成功时返回列表，失败时返回 null。
     */
    private List<CourseTimeSlotVO> collectScheduleRows(VBox rows) {
        List<CourseTimeSlotVO> result = new ArrayList<>();
        for (javafx.scene.Node node : rows.getChildren()) {
            if (!(node instanceof HBox)) {
                continue;
            }
            ScheduleRow row = (ScheduleRow) node.getUserData();
            if (row == null) {
                continue;
            }
            if (!row.isComplete() || !row.hasValidRange()
                    || row.getLocation().trim().isEmpty()) {
                showInfo("请完整填写每个调整后时间段，并保证结束周/节不早于起始周/节");
                return null;
            }
            result.add(row.toSlot());
        }
        return result;
    }

    /**
     * 教务老师开课审批。
     */
    private void configureCourseApprove() {
        titleLabel.setText("开课审批");
        subtitleLabel.setText("审核教师提交的开课申请与成绩组成占比");
        sectionLabel.setText("待审核课程");
        buildCourseColumns();

        Button refreshBtn = button("刷新待审核课程", "btn-recharge-preset");
        refreshBtn.setOnAction(e -> fetchCourses(() -> academicController.listPendingCourses()));

        Button approveBtn = button("审核通过", "btn-primary-action");
        approveBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先选择课程");
                return;
            }
            runAction("开课审批", () -> academicController.approveCourse(course.getCourseCode()),
                    () -> fetchCourses(() -> academicController.listPendingCourses()));
        });

        Button rejectBtn = button("驳回课程", "lib-btn-danger");
        rejectBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先选择课程");
                return;
            }
            runAction("开课审批", () -> academicController.rejectCourse(course.getCourseCode()),
                    () -> fetchCourses(() -> academicController.listPendingCourses()));
        });

        headerControls.getChildren().addAll(refreshBtn, approveBtn, rejectBtn);
    }

    /**
     * 提交课程审批，解析表单后在后台请求服务端。
     */
    private void submitCourse(TextField codeField, TextField nameField, TextField creditField,
                              TextField capacityField,
                              ComboBox<String> natureBox,
                              ComboBox<String> semesterYearBox,
                              ComboBox<Integer> semesterNoBox,
                              List<ScoreComponentVO> components) {
        try {
            if (empty(codeField) || empty(nameField) || empty(creditField)
                    || empty(capacityField)
                    || semesterYearBox.getValue() == null
                    || semesterNoBox.getValue() == null) {
                showInfo("请完整填写课程代码、名称、学分、容量、开课学期和成绩组成");
                return;
            }

            CourseVO course = new CourseVO();
            course.setCourseCode(codeField.getText().trim());
            course.setCourseName(nameField.getText().trim());
            course.setCredit(Double.parseDouble(creditField.getText().trim()));
            course.setCapacity(Integer.parseInt(capacityField.getText().trim()));
            course.setTeacherId(currentUser.getUid());
            course.setTeacherName(currentUser.getName());
            course.setClassTime("");
            course.setLocation("");
            course.setNature(natureBox.getValue() == null ? "选修" : natureBox.getValue());
            course.setSemester(buildSemester(semesterYearBox.getValue(), semesterNoBox.getValue()));
            course.setScoreComponents(components);
            if (course.getScoreComponents().isEmpty()) {
                showInfo("成绩组成格式错误，示例：平时成绩:0.3,实验成绩:0.2,期末成绩:0.5");
                return;
            }

            runAction("提交课程", () -> academicController.addCourse(course),
                    () -> fetchCourses(() -> academicController.queryByTeacher(currentUser.getUid())));
        } catch (Exception ex) {
            showInfo("课程信息格式错误：" + ex.getMessage());
        }
    }

    /**
     * 根据选中课程动态生成成绩组成输入行。
     */
    private void rebuildScoreInputs(VBox scoreInputs, Label calcLabel) {
        scoreInputs.getChildren().clear();
        CourseVO course = selectedCourse();
        if (course == null || course.getScoreComponents() == null || course.getScoreComponents().isEmpty()) {
            Label emptyLabel = new Label("该课程暂无可登记的成绩组成");
            emptyLabel.getStyleClass().add("lib-subtitle");
            scoreInputs.getChildren().add(emptyLabel);
            calcLabel.setText("实时计算：等待输入");
            return;
        }

        for (ScoreComponentVO component : course.getScoreComponents()) {
            Label label = new Label(component.getComponentName() + " (" + component.getWeight() + ")");
            label.getStyleClass().add("lib-form-label");
            label.setMinWidth(130);

            TextField scoreField = new TextField();
            scoreField.setUserData(component);
            scoreField.setPromptText("请输入" + component.getComponentName() + "成绩");
            scoreField.getStyleClass().add("modern-input-field");
            scoreField.textProperty().addListener((obs, oldValue, newValue) ->
                    updateLiveGrade(scoreInputs, calcLabel));
            HBox.setHgrow(scoreField, Priority.ALWAYS);

            HBox row = new HBox(8, label, scoreField);
            row.setAlignment(Pos.CENTER_LEFT);
            scoreInputs.getChildren().add(row);
        }
        updateLiveGrade(scoreInputs, calcLabel);
    }

    /**
     * 提交学生成绩。
     */
    private void updateLiveGrade(VBox scoreInputs, Label calcLabel) {
        double total = 0.0;
        boolean complete = true;
        StringBuilder formula = new StringBuilder();

        for (var node : scoreInputs.getChildren()) {
            if (!(node instanceof HBox)) {
                continue;
            }
            HBox row = (HBox) node;
            if (row.getChildren().size() < 2 || !(row.getChildren().get(1) instanceof TextField)) {
                continue;
            }
            TextField scoreField = (TextField) row.getChildren().get(1);
            if (!(scoreField.getUserData() instanceof ScoreComponentVO)) {
                continue;
            }
            ScoreComponentVO component = (ScoreComponentVO) scoreField.getUserData();
            String text = scoreField.getText() == null ? "" : scoreField.getText().trim();
            if (text.isEmpty()) {
                complete = false;
                continue;
            }
            try {
                double score = Double.parseDouble(text);
                double weight = component.getWeight();
                total += score * weight;
                if (formula.length() > 0) {
                    formula.append(" + ");
                }
                formula.append(score).append("*").append(weight);
            } catch (NumberFormatException e) {
                complete = false;
            }
        }

        if (!complete || formula.length() == 0) {
            calcLabel.setText("实时计算：请完整输入各项成绩（可包含小数）");
            return;
        }

        long roundedFinal = Math.round(total);
        calcLabel.setText("实时计算：" + formula + " = " + roundedFinal);
    }

    /**
     * 提交学生成绩。
     */
    private void submitGrade(TextField studentIdField, VBox scoreInputs) {
        CourseVO course = selectedCourse();
        if (course == null || studentIdField.getText().trim().isEmpty()) {
            showInfo("请选择课程并输入学生学号");
            return;
        }

        GradeVO grade = new GradeVO();
        grade.setStudentId(studentIdField.getText().trim());
        grade.setCourseCode(course.getCourseCode());
        grade.setCourseName(course.getCourseName());

        List<GradeScoreVO> scores = new ArrayList<>();
        try {
            for (var node : scoreInputs.getChildren()) {
                if (!(node instanceof HBox)) {
                    continue;
                }
                HBox row = (HBox) node;
                if (row.getChildren().size() < 2 || !(row.getChildren().get(1) instanceof TextField)) {
                    continue;
                }
                TextField scoreField = (TextField) row.getChildren().get(1);
                if (!(scoreField.getUserData() instanceof ScoreComponentVO)) {
                    continue;
                }
                String name = ((ScoreComponentVO) scoreField.getUserData()).getComponentName();
                double score = Double.parseDouble(scoreField.getText().trim());
                scores.add(new GradeScoreVO(name, score));
            }
        } catch (Exception ex) {
            showInfo("成绩格式错误：" + ex.getMessage());
            return;
        }

        grade.setComponentScores(scores);
        runAction("成绩登记", () -> academicController.submitGrade(grade), null);
    }

    /**
     * 构造课程表列。
     */
    private void buildCourseColumns() {
        TableColumn<Object, String> codeCol = column("课程代码",
                data -> ((CourseVO) data.getValue()).getDisplayCode());
        TableColumn<Object, String> nameCol = column("课程名称",
                data -> ((CourseVO) data.getValue()).getCourseName());
        TableColumn<Object, String> creditCol = column("学分",
                data -> String.valueOf(((CourseVO) data.getValue()).getCredit()));
        TableColumn<Object, String> semesterCol = column("开课学期",
                data -> ((CourseVO) data.getValue()).getSemester());
        TableColumn<Object, String> teacherCol = column("任课教师",
                data -> ((CourseVO) data.getValue()).getTeacherName());
        TableColumn<Object, String> capacityCol = column("容量",
                data -> ((CourseVO) data.getValue()).getSelectedCount() + "/"
                        + ((CourseVO) data.getValue()).getCapacity());
        TableColumn<Object, String> timeCol = column("上课时间",
                data -> displayClassTime(((CourseVO) data.getValue()).getClassTime()));
        TableColumn<Object, String> weekCol = column("周次",
                data -> displayWeeks(((CourseVO) data.getValue()).getStartWeek(),
                        ((CourseVO) data.getValue()).getEndWeek()));
        TableColumn<Object, String> locationCol = column("教室",
                data -> ((CourseVO) data.getValue()).getLocation());
        TableColumn<Object, String> statusCol = column("状态",
                data -> ((CourseVO) data.getValue()).getStatus());
        TableColumn<Object, String> componentCol = column("成绩组成",
                data -> formatComponents(((CourseVO) data.getValue()).getScoreComponents()));

        dataTable.getColumns().addAll(codeCol, nameCol, creditCol, semesterCol, teacherCol,
                capacityCol, timeCol, weekCol, locationCol, statusCol, componentCol);
        dataTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    /**
     * 构造成绩表列。
     */
    private void buildGradeColumns() {
        TableColumn<Object, String> codeCol = column("课程代码",
                data -> ((GradeVO) data.getValue()).getCourseCode());
        TableColumn<Object, String> nameCol = column("课程名称",
                data -> ((GradeVO) data.getValue()).getCourseName());
        TableColumn<Object, String> componentCol = column("各组成得分",
                data -> formatGradeScores(((GradeVO) data.getValue()).getComponentScores()));
        TableColumn<Object, String> finalCol = column("最终成绩",
                data -> String.valueOf(((GradeVO) data.getValue()).getFinalScore()));
        TableColumn<Object, String> gpaCol = column("绩点",
                data -> String.valueOf(((GradeVO) data.getValue()).getGpa()));

        dataTable.getColumns().addAll(codeCol, nameCol, componentCol, finalCol, gpaCol);
        dataTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    /**
     * 异步加载课程列表。
     */
    private void fetchCourses(Supplier<List<CourseVO>> supplier) {
        THREAD_POOL.execute(() -> {
            try {
                List<CourseVO> courses = supplier.get();
                Platform.runLater(() -> {
                    dataTable.getItems().clear();
                    dataTable.getItems().addAll(courses);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("请求失败：" + e.getMessage()));
            }
        });
    }

    /**
     * 异步加载成绩列表。
     */
    private void fetchGrades(Supplier<List<GradeVO>> supplier) {
        THREAD_POOL.execute(() -> {
            try {
                List<GradeVO> grades = supplier.get();
                Platform.runLater(() -> {
                    dataTable.getItems().clear();
                    dataTable.getItems().addAll(grades);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("请求失败：" + e.getMessage()));
            }
        });
    }

    /**
     * 异步执行写操作，成功后返回主线程刷新。
     */
    private void runAction(String action, Supplier<ResponseCode> actionSupplier, Runnable onSuccess) {
        THREAD_POOL.execute(() -> {
            try {
                ResponseCode code = actionSupplier.get();
                Platform.runLater(() -> {
                    showResult(action, code);
                    if (code == ResponseCode.SUCCESS && onSuccess != null) {
                        onSuccess.run();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError(action + "失败：" + e.getMessage()));
            }
        });
    }

    private CourseVO selectedCourse() {
        Object item = dataTable.getSelectionModel().getSelectedItem();
        return item instanceof CourseVO ? (CourseVO) item : null;
    }

    private TableColumn<Object, String> column(String title,
                                               javafx.util.Callback<TableColumn.CellDataFeatures<Object, String>, String> valueFactory) {
        TableColumn<Object, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new ReadOnlyStringWrapper(valueFactory.call(data)));
        return col;
    }

    private TextField inputField(String prompt, double width) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(width);
        field.getStyleClass().add("modern-input-field");
        return field;
    }

    private VBox labeledField(String label, javafx.scene.Node field) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("lib-form-label");
        VBox box = new VBox(4, labelNode, field);
        return box;
    }

    private FlowPane formRow(javafx.scene.Node... nodes) {
        return new FlowPane(8, 8, nodes);
    }

    private Button button(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        return button;
    }

    /**
     * 创建学年选择框，默认展示当前学年附近的可选学年。
     */
    private ComboBox<String> createAcademicYearComboBox() {
        int currentYear = Year.now().getValue();
        List<String> academicYears = new ArrayList<>();
        for (int offset = -1; offset <= 3; offset++) {
            int startYear = currentYear + offset;
            academicYears.add(startYear + "-" + (startYear + 1));
        }
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(academicYears));
        box.setPrefWidth(130);
        box.getStyleClass().add("academic-combo");
        box.setValue(currentYear + "-" + (currentYear + 1));
        return box;
    }

    /**
     * 创建学期选择框，一学年固定为 3 个学期。
     */
    private ComboBox<Integer> createSemesterComboBox() {
        ComboBox<Integer> box = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3));
        box.setPrefWidth(90);
        box.getStyleClass().add("academic-combo");
        return box;
    }

    /**
     * 创建周次选择框。
     */
    private ComboBox<Integer> createWeekComboBox() {
        ComboBox<Integer> box = new ComboBox<>();
        box.setPrefWidth(100);
        box.getStyleClass().add("academic-combo");
        return box;
    }

    /**
     * 根据学期生成周次列表。第 1 学期 1-4 周，第 2、3 学期 1-18 周。
     */
    private List<Integer> buildWeekOptions(String semester) {
        int maxWeek = parseSemesterNo(semester) == 1 ? 4 : 18;
        return buildWeekNumbers(maxWeek);
    }

    /**
     * 生成从 1 到最大周数的周次列表。
     */
    private List<Integer> buildWeekNumbers(int maxWeek) {
        List<Integer> weeks = new ArrayList<>();
        for (int week = 1; week <= maxWeek; week++) {
            weeks.add(week);
        }
        return weeks;
    }

    /**
     * 从学期字符串中解析学期序号。
     */
    private int parseSemesterNo(String semester) {
        if (semester == null || semester.trim().isEmpty()) {
            return 0;
        }
        String[] numbers = semester.replaceAll("[^0-9]+", " ").trim().split("\\s+");
        if (numbers.length >= 3) {
            try {
                return Integer.parseInt(numbers[2]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 创建星期选择框，一周固定为周一到周日。
     */
    private ComboBox<String> createDayComboBox() {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(
                "周一", "周二", "周三", "周四", "周五", "周六", "周日"));
        box.setPrefWidth(110);
        box.getStyleClass().add("academic-combo");
        return box;
    }

    /**
     * 创建节次选择框，一天固定为 13 节课。
     */
    private ComboBox<Integer> createPeriodComboBox() {
        List<Integer> periods = new ArrayList<>();
        for (int period = 1; period <= 13; period++) {
            periods.add(period);
        }
        ComboBox<Integer> box = new ComboBox<>(FXCollections.observableArrayList(periods));
        box.setPrefWidth(110);
        box.getStyleClass().add("academic-combo");
        return box;
    }

    /**
     * 根据星期与起止节次生成统一格式的上课时间字符串。
     */
    private String buildClassTime(String day, int startPeriod, int endPeriod) {
        return day + " 第" + startPeriod + "-" + endPeriod + "节";
    }

    /**
     * 根据学年与学期序号生成数据库学期字段，例如 2026-2027-1。
     */
    private String buildSemester(String academicYear, int semesterNo) {
        return academicYear + "-" + semesterNo;
    }

    /**
     * 清空课程时间选择框。
     */
    private void clearCourseTimeSelectors(ComboBox<String> dayBox,
                                          ComboBox<Integer> startPeriodBox,
                                          ComboBox<Integer> endPeriodBox) {
        dayBox.setValue(null);
        startPeriodBox.setValue(null);
        endPeriodBox.setValue(null);
    }

    /**
     * 将已有课程时间回填到星期与起止节次选择框中。
     *
     * <p>兼容“周一 1-2节”“周一 第1-3节”等已有字符串格式。</p>
     */
    private void applyClassTimeToSelectors(String classTime,
                                           ComboBox<String> dayBox,
                                           ComboBox<Integer> startPeriodBox,
                                           ComboBox<Integer> endPeriodBox) {
        clearCourseTimeSelectors(dayBox, startPeriodBox, endPeriodBox);
        if (classTime == null || classTime.trim().isEmpty()) {
            return;
        }

        String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        String selectedDay = null;
        for (String day : dayNames) {
            if (classTime.startsWith(day)) {
                selectedDay = day;
                break;
            }
        }
        dayBox.setValue(selectedDay);

        String[] numbers = classTime.replaceAll("[^0-9]+", " ").trim().split("\\s+");
        try {
            if (numbers.length >= 2) {
                int start = Integer.parseInt(numbers[0]);
                int end = Integer.parseInt(numbers[1]);
                startPeriodBox.setValue(clampPeriod(start));
                endPeriodBox.setValue(clampPeriod(end));
            } else if (numbers.length == 1) {
                int period = clampPeriod(Integer.parseInt(numbers[0]));
                startPeriodBox.setValue(period);
                endPeriodBox.setValue(period);
            }
        } catch (NumberFormatException ignored) {
            // 旧数据格式无法解析时保留空选择，等待教务老师重新选择。
        }
    }

    /**
     * 将节次限制在 1 到 13 之间。
     */
    private int clampPeriod(int period) {
        return Math.max(1, Math.min(13, period));
    }

    private boolean empty(TextField field) {
        return field.getText() == null || field.getText().trim().isEmpty();
    }

    /**
     * 添加一行成绩组成输入。
     */
    private void addComponentRow(VBox rows, Label totalLabel) {
        TextField nameField = inputField("成绩类别", 150);
        TextField weightField = inputField("组成比例", 100);
        Button removeBtn = button("删除", "lib-btn-danger");

        nameField.textProperty().addListener((obs, oldValue, newValue) ->
                updateComponentTotal(rows, totalLabel));
        weightField.textProperty().addListener((obs, oldValue, newValue) ->
                updateComponentTotal(rows, totalLabel));

        HBox row = new HBox(8, nameField, weightField, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        removeBtn.setOnAction(e -> {
            rows.getChildren().remove(row);
            updateComponentTotal(rows, totalLabel);
        });

        rows.getChildren().add(row);
        updateComponentTotal(rows, totalLabel);
    }

    /**
     * 从动态成绩组成行中解析出合法的成绩组成列表。
     */
    private List<ScoreComponentVO> parseComponentRows(VBox rows, Label totalLabel) {
        List<ScoreComponentVO> result = new ArrayList<>();
        double total = 0.0;
        for (javafx.scene.Node node : rows.getChildren()) {
            if (!(node instanceof HBox)) {
                continue;
            }
            HBox row = (HBox) node;
            if (row.getChildren().size() < 2
                    || !(row.getChildren().get(0) instanceof TextField)
                    || !(row.getChildren().get(1) instanceof TextField)) {
                continue;
            }
            TextField nameField = (TextField) row.getChildren().get(0);
            TextField weightField = (TextField) row.getChildren().get(1);
            if (nameField.getText().trim().isEmpty() || weightField.getText().trim().isEmpty()) {
                showInfo("请完整填写每个成绩类别的名称和比例");
                return null;
            }
            try {
                double weight = Double.parseDouble(weightField.getText().trim());
                if (weight <= 0) {
                    showInfo("组成比例必须大于 0");
                    return null;
                }
                result.add(new ScoreComponentVO(nameField.getText().trim(), weight));
                total += weight;
            } catch (NumberFormatException e) {
                showInfo("组成比例必须是数字，例如 0.3");
                return null;
            }
        }

        if (result.isEmpty()) {
            showInfo("请至少添加一个成绩组成");
            return null;
        }
        if (Math.abs(total - 1.0) >= 0.0001) {
            showInfo("成绩组成比例之和必须等于 1");
            return null;
        }
        updateComponentTotal(rows, totalLabel);
        return result;
    }

    /**
     * 实时刷新成绩组成比例合计。
     */
    private void updateComponentTotal(VBox rows, Label totalLabel) {
        double total = 0.0;
        boolean valid = true;
        for (javafx.scene.Node node : rows.getChildren()) {
            if (!(node instanceof HBox)) {
                continue;
            }
            HBox row = (HBox) node;
            if (row.getChildren().size() < 2
                    || !(row.getChildren().get(1) instanceof TextField)) {
                continue;
            }
            TextField weightField = (TextField) row.getChildren().get(1);
            String text = weightField.getText() == null ? "" : weightField.getText().trim();
            if (text.isEmpty()) {
                valid = false;
                continue;
            }
            try {
                total += Double.parseDouble(text);
            } catch (NumberFormatException e) {
                valid = false;
            }
        }
        boolean equalsOne = Math.abs(total - 1.0) < 0.0001;
        totalLabel.setText("当前比例合计：" + total);
        totalLabel.getStyleClass().removeAll("error", "success");
        totalLabel.getStyleClass().add(valid && equalsOne ? "success" : "error");
    }

    private List<ScoreComponentVO> parseComponents(String text) {
        List<ScoreComponentVO> result = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                result.add(new ScoreComponentVO(kv[0].trim(), Double.parseDouble(kv[1].trim())));
            }
        }
        return result;
    }

    private String formatComponents(List<ScoreComponentVO> components) {
        if (components == null || components.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (ScoreComponentVO component : components) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(component.getComponentName()).append(":").append(component.getWeight());
        }
        return sb.toString();
    }

    private String formatGradeScores(List<GradeScoreVO> scores) {
        if (scores == null || scores.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (GradeScoreVO score : scores) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(score.getComponentName()).append(":").append(score.getScore());
        }
        return sb.toString();
    }

    private String displayClassTime(String classTime) {
        return classTime == null || classTime.trim().isEmpty() ? "待安排" : classTime.trim();
    }

    private String displayWeeks(int startWeek, int endWeek) {
        if (startWeek <= 0 || endWeek <= 0) {
            return "待安排";
        }
        return startWeek + "-" + endWeek + "周";
    }

    private void showResult(String action, ResponseCode code) {
        if (code == ResponseCode.SUCCESS) {
            showInfo(action + "成功");
        } else {
            showInfo(action + "失败：" + code);
        }
    }

    private void showInfo(String message) {
        showAlert("教务系统", message, Alert.AlertType.INFORMATION);
    }

    private boolean confirm(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        return alert.showAndWait().filter(buttonType -> buttonType == ButtonType.OK).isPresent();
    }

    private void showError(String message) {
        showAlert("教务系统", message, Alert.AlertType.ERROR);
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * 课程管理页中的一个可编辑上课时间段行。
     */
    private class ScheduleRow {
        private final ComboBox<Integer> startWeekBox;
        private final ComboBox<Integer> endWeekBox;
        private final ComboBox<String> dayBox;
        private final ComboBox<Integer> startPeriodBox;
        private final ComboBox<Integer> endPeriodBox;
        private final TextField locationField;
        private final HBox node;

        ScheduleRow(CourseTimeSlotVO slot, int maxWeek) {
            this(slot, maxWeek, null);
        }

        ScheduleRow(CourseTimeSlotVO slot, int maxWeek, String defaultLocation) {
            List<Integer> weekOptions = buildWeekNumbers(maxWeek);

            startWeekBox = createWeekComboBox();
            startWeekBox.setItems(FXCollections.observableArrayList(weekOptions));
            startWeekBox.setPromptText("起始周");

            endWeekBox = createWeekComboBox();
            endWeekBox.setItems(FXCollections.observableArrayList(weekOptions));
            endWeekBox.setPromptText("结束周");

            dayBox = createDayComboBox();
            dayBox.setPromptText("星期");

            startPeriodBox = createPeriodComboBox();
            startPeriodBox.setPromptText("起始节");

            endPeriodBox = createPeriodComboBox();
            endPeriodBox.setPromptText("结束节");

            locationField = new TextField();
            locationField.setPromptText("上课地点");
            locationField.getStyleClass().add("modern-input-field");
            locationField.setPrefWidth(160);

            if (slot != null) {
                startWeekBox.setValue(Math.min(slot.getStartWeek(), maxWeek));
                endWeekBox.setValue(Math.min(slot.getEndWeek(), maxWeek));
                dayBox.setValue(slot.getDay());
                startPeriodBox.setValue(slot.getStartPeriod());
                endPeriodBox.setValue(slot.getEndPeriod());
                locationField.setText(slot.getLocation() == null ? "" : slot.getLocation());
            } else if (defaultLocation != null) {
                locationField.setText(defaultLocation);
            }

            node = new HBox(8);
            Button removeBtn = button("移除", "lib-btn-danger");
            removeBtn.setOnAction(e -> {
                if (node.getParent() instanceof VBox) {
                    ((VBox) node.getParent()).getChildren().remove(node);
                }
            });
            node.getChildren().addAll(
                    labeledField("起始周", startWeekBox),
                    labeledField("结束周", endWeekBox),
                    labeledField("星期", dayBox),
                    labeledField("起始节", startPeriodBox),
                    labeledField("结束节", endPeriodBox),
                    labeledField("上课地点", locationField),
                    removeBtn);
            node.setUserData(this);
        }

        HBox getNode() {
            return node;
        }

        boolean isComplete() {
            return startWeekBox.getValue() != null
                    && endWeekBox.getValue() != null
                    && dayBox.getValue() != null
                    && startPeriodBox.getValue() != null
                    && endPeriodBox.getValue() != null;
        }

        boolean hasValidRange() {
            return endWeekBox.getValue() >= startWeekBox.getValue()
                    && endPeriodBox.getValue() >= startPeriodBox.getValue();
        }

        int getStartWeek() {
            return startWeekBox.getValue();
        }

        int getEndWeek() {
            return endWeekBox.getValue();
        }

        String getLocation() {
            return locationField.getText() == null ? "" : locationField.getText().trim();
        }

        CourseTimeSlotVO toSlot() {
            return new CourseTimeSlotVO(
                    startWeekBox.getValue(),
                    endWeekBox.getValue(),
                    dayBox.getValue(),
                    startPeriodBox.getValue(),
                    endPeriodBox.getValue(),
                    locationField.getText() == null ? "" : locationField.getText().trim());
        }

        /**
         * 返回当前行已完整填写的时间段快照，用于新增下一行时复用。
         */
        CourseTimeSlotVO snapshot() {
            return isComplete() ? toSlot() : null;
        }
    }
}
