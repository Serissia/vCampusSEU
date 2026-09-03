package com.vcampus.client.controller;

import com.vcampus.client.util.ScrollSpeedUtil;
import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.vo.CourseVO;
import com.vcampus.common.vo.CourseTimeSlotVO;
import com.vcampus.common.vo.GradeScoreVO;
import com.vcampus.common.vo.GradeVO;
import com.vcampus.common.vo.ScoreComponentVO;
import com.vcampus.common.vo.UserVO;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
    private TableView<Object> dataTable;

    private String moduleKey;
    private UserVO currentUser;
    private AcademicController academicController;

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
            case "ACADEMIC_TEACHER":
                configureTeacherCourse();
                break;
            case "ACADEMIC_GRADE_SUBMIT":
                configureGradeSubmit();
                break;
            case "ACADEMIC_MANAGE":
                configureAcademicManage();
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
        sectionLabel.setText("课程列表");
        buildCourseColumns();

        TextField keywordField = new TextField();
        keywordField.setPromptText("课程关键字");
        keywordField.setPrefWidth(180);
        keywordField.getStyleClass().add("modern-input-field");

        Button queryBtn = button("查询课程", "btn-primary-action");
        queryBtn.setOnAction(e -> fetchCourses(() -> academicController.queryCourses(keywordField.getText().trim())));

        Button allBtn = button("全部课程", "btn-recharge-preset");
        allBtn.setOnAction(e -> fetchCourses(() -> academicController.listAllCourses()));

        Button myBtn = button("我的已选课程", "btn-recharge-preset");
        myBtn.setOnAction(e -> fetchCourses(() -> academicController.listMyCourses()));

        Button selectBtn = button("选课", "btn-primary-action");
        selectBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先选择课程");
                return;
            }
            runAction("选课", () -> academicController.selectCourse(course.getCourseCode()),
                    () -> fetchCourses(() -> academicController.listAllCourses()));
        });

        Button dropBtn = button("退课", "lib-btn-danger");
        dropBtn.setOnAction(e -> {
            CourseVO course = selectedCourse();
            if (course == null) {
                showInfo("请先选择课程");
                return;
            }
            runAction("退课", () -> academicController.dropCourse(course.getCourseCode()),
                    () -> fetchCourses(() -> academicController.listMyCourses()));
        });

        headerControls.getChildren().addAll(keywordField, queryBtn, allBtn, myBtn, selectBtn, dropBtn);
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
        TextField locationField = inputField("教室", 120);
        ComboBox<String> semesterYearBox = createAcademicYearComboBox();
        ComboBox<Integer> semesterNoBox = createSemesterComboBox();
        semesterYearBox.setPromptText("选择学年");
        semesterNoBox.setPromptText("学期");
        TextField componentsField = inputField("成绩组成", 300);
        componentsField.setPromptText("平时成绩:0.3,实验成绩:0.2,期末成绩:0.5");

        Label hint = new Label(
                "填写说明：课程代码和名称请勿使用空格或特殊符号；"
                        + "学分为数字；容量为整数；学年和学期请从下拉框选择；"
                        + "上课时间无需填写，由教务老师审核后统一安排；"
                        + "成绩组成格式为“名称:权重”，多个组成用英文逗号分隔，"
                        + "所有组成权重之和必须等于 1。");
        hint.getStyleClass().add("academic-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);

        Button addBtn = button("提交课程审批", "btn-primary-action");
        addBtn.setOnAction(e -> submitCourse(
                codeField, nameField, creditField, capacityField,
                locationField, semesterYearBox, semesterNoBox, componentsField));

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
                formRow(labeledField("教室", locationField),
                        labeledField("学年", semesterYearBox),
                        labeledField("学期", semesterNoBox)),
                formRow(labeledField("成绩组成", componentsField), addBtn));
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

        dataTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
                rebuildScoreInputs(scoreInputs));

        Button submitBtn = button("提交成绩", "btn-primary-action");
        submitBtn.setOnAction(e -> submitGrade(studentIdField, scoreInputs));

        headerControls.getChildren().add(loadBtn);
        formCard.setVisible(true);
        formCard.setManaged(true);
        formContent.getChildren().addAll(
                formRow(labeledField("学生学号", studentIdField)),
                scoreInputs,
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
            scheduleRows.getChildren().add(new ScheduleRow(defaultSlot, maxWeek).getNode());
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

        dataTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            CourseVO course = selectedCourse();
            scheduleRows.getChildren().clear();
            if (course == null) {
                selectedCourseLabel.setText("尚未选择课程");
                return;
            }
            selectedCourseLabel.setText(course.getCourseCode() + " - " + course.getCourseName());
            int maxWeek = buildWeekOptions(course.getSemester()).size();
            List<CourseTimeSlotVO> slots = course.getTimeSlots();
            if (slots == null || slots.isEmpty()) {
                scheduleRows.getChildren().add(new ScheduleRow(null, maxWeek).getNode());
            } else {
                for (CourseTimeSlotVO slot : slots) {
                    scheduleRows.getChildren().add(new ScheduleRow(slot, maxWeek).getNode());
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
                              TextField capacityField, TextField locationField,
                              ComboBox<String> semesterYearBox,
                              ComboBox<Integer> semesterNoBox,
                              TextField componentsField) {
        try {
            if (empty(codeField) || empty(nameField) || empty(creditField)
                    || empty(capacityField) || empty(componentsField)
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
            course.setLocation(locationField.getText().trim());
            course.setSemester(buildSemester(semesterYearBox.getValue(), semesterNoBox.getValue()));
            course.setScoreComponents(parseComponents(componentsField.getText().trim()));
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
    private void rebuildScoreInputs(VBox scoreInputs) {
        scoreInputs.getChildren().clear();
        CourseVO course = selectedCourse();
        if (course == null || course.getScoreComponents() == null || course.getScoreComponents().isEmpty()) {
            Label emptyLabel = new Label("该课程暂无可登记的成绩组成");
            emptyLabel.getStyleClass().add("lib-subtitle");
            scoreInputs.getChildren().add(emptyLabel);
            return;
        }

        for (ScoreComponentVO component : course.getScoreComponents()) {
            Label label = new Label(component.getComponentName() + " (" + component.getWeight() + ")");
            label.getStyleClass().add("lib-form-label");
            label.setMinWidth(130);

            TextField scoreField = new TextField();
            scoreField.setUserData(component.getComponentName());
            scoreField.setPromptText("请输入" + component.getComponentName() + "成绩");
            scoreField.getStyleClass().add("modern-input-field");
            HBox.setHgrow(scoreField, Priority.ALWAYS);

            HBox row = new HBox(8, label, scoreField);
            row.setAlignment(Pos.CENTER_LEFT);
            scoreInputs.getChildren().add(row);
        }
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
                String name = (String) scoreField.getUserData();
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
                data -> ((CourseVO) data.getValue()).getCourseCode());
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
        private final HBox node;

        ScheduleRow(CourseTimeSlotVO slot, int maxWeek) {
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

            if (slot != null) {
                startWeekBox.setValue(Math.min(slot.getStartWeek(), maxWeek));
                endWeekBox.setValue(Math.min(slot.getEndWeek(), maxWeek));
                dayBox.setValue(slot.getDay());
                startPeriodBox.setValue(slot.getStartPeriod());
                endPeriodBox.setValue(slot.getEndPeriod());
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

        CourseTimeSlotVO toSlot() {
            return new CourseTimeSlotVO(
                    startWeekBox.getValue(),
                    endWeekBox.getValue(),
                    dayBox.getValue(),
                    startPeriodBox.getValue(),
                    endPeriodBox.getValue());
        }

        /**
         * 返回当前行已完整填写的时间段快照，用于新增下一行时复用。
         */
        CourseTimeSlotVO snapshot() {
            return isComplete() ? toSlot() : null;
        }
    }
}
