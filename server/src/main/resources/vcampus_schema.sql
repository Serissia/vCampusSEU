-- 彻底清除旧数据库与残留表
DROP DATABASE IF EXISTS `db_vcampus`;
CREATE DATABASE `db_vcampus` DEFAULT CHARACTER SET utf8mb4;
USE `db_vcampus`;

-- 1. 统一账户与一卡通表 (tbl_user)
CREATE TABLE `tbl_user` (
    `uid` VARCHAR(32) NOT NULL COMMENT '一卡通号/学号/教工号',
    `password` VARCHAR(64) NOT NULL COMMENT '登录密码',
    `role` VARCHAR(32) NOT NULL COMMENT '角色: ADMIN, ACADEMIC_AFFAIRS_TEACHER, LIBRARIAN, STORE_MANAGER, TEACHER, STUDENT',
    `name` VARCHAR(32) NOT NULL COMMENT '真实姓名',
    `balance` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '一卡通虚拟账户余额',
    `status` INT NOT NULL DEFAULT 1 COMMENT '状态: 1正常, 0冻结',
    PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基本信息表';

-- 2. 学生详细信息表 (tbl_student)
CREATE TABLE `tbl_student` (
    `uid` VARCHAR(32) NOT NULL COMMENT '学号',
    `gender` VARCHAR(8) DEFAULT '男' COMMENT '性别',
    `department` VARCHAR(64) DEFAULT NULL COMMENT '院系',
    `major` VARCHAR(64) DEFAULT NULL COMMENT '专业',
    `class_name` VARCHAR(32) DEFAULT NULL COMMENT '班级',
    `phone` VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
    PRIMARY KEY (`uid`),
    CONSTRAINT `fk_student_user` FOREIGN KEY (`uid`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生扩展档案表';

-- 3. 课程信息表 (tbl_course)
CREATE TABLE `tbl_course` (
    `course_id` VARCHAR(32) NOT NULL COMMENT '课程编号',
    `course_name` VARCHAR(64) NOT NULL COMMENT '课程名称',
    `teacher_id` VARCHAR(32) NOT NULL COMMENT '任课教师工号',
    `teacher_name` VARCHAR(32) NOT NULL COMMENT '任课教师姓名',
    `credits` FLOAT NOT NULL DEFAULT 2.0 COMMENT '学分',
    `open_semester` VARCHAR(32) NOT NULL COMMENT '开课学期',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: ACTIVE, DISABLED, PENDING',
    `max_capacity` INT NOT NULL DEFAULT 60 COMMENT '课程总容量',
    `current_num` INT NOT NULL DEFAULT 0 COMMENT '已选人数',
    `time_slot` VARCHAR(512) DEFAULT NULL COMMENT '上课时间，由教务老师统一安排，可包含多个时间段',
    `classroom` VARCHAR(64) NOT NULL COMMENT '上课地点',
    `start_week` INT NOT NULL DEFAULT 0 COMMENT '起始周次，0 表示待安排',
    `end_week` INT NOT NULL DEFAULT 0 COMMENT '结束周次，0 表示待安排',
    PRIMARY KEY (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程信息表';

-- 3.1 课程成绩组成表（教师自定义成绩项及权重）
CREATE TABLE `tbl_course_score_component` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '组成项ID',
    `course_id` VARCHAR(32) NOT NULL COMMENT '课程编号',
    `component_name` VARCHAR(64) NOT NULL COMMENT '成绩组成名称',
    `weight` DECIMAL(5,4) NOT NULL COMMENT '权重',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_component` (`course_id`, `component_name`),
    CONSTRAINT `fk_csc_course` FOREIGN KEY (`course_id`) REFERENCES `tbl_course`(`course_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程成绩组成表';

-- 4. 选课记录与成绩表 (tbl_course_select)
CREATE TABLE `tbl_course_select` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '记录自增ID',
    `student_id` VARCHAR(32) NOT NULL COMMENT '学生学号',
    `course_id` VARCHAR(32) NOT NULL COMMENT '课程编号',
    `select_time` DATETIME DEFAULT NULL COMMENT '选课时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'SELECTED' COMMENT '选课状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_course` (`student_id`, `course_id`),
    CONSTRAINT `fk_cs_student` FOREIGN KEY (`student_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_cs_course` FOREIGN KEY (`course_id`) REFERENCES `tbl_course`(`course_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课与成绩记录表';

-- 4.1 课程成绩表
CREATE TABLE `tbl_grade` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '成绩记录自增ID',
    `student_id` VARCHAR(32) NOT NULL COMMENT '学生学号',
    `course_id` VARCHAR(32) NOT NULL COMMENT '课程编号',
    `course_name` VARCHAR(64) NOT NULL COMMENT '课程名称快照',
    `final_score` DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '最终成绩',
    `gpa` DECIMAL(4, 2) NOT NULL DEFAULT 0 COMMENT '绩点',
    `status` VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT '成绩状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_grade_student_course` (`student_id`, `course_id`),
    CONSTRAINT `fk_grade_student` FOREIGN KEY (`student_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_grade_course` FOREIGN KEY (`course_id`) REFERENCES `tbl_course`(`course_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程成绩表';

-- 4.2 课程成绩单项得分表
CREATE TABLE `tbl_grade_score` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '单项成绩ID',
    `grade_id` INT NOT NULL COMMENT '成绩记录ID',
    `component_name` VARCHAR(64) NOT NULL COMMENT '成绩组成名称',
    `score` DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '单项得分',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_grade_component` (`grade_id`, `component_name`),
    CONSTRAINT `fk_gs_grade` FOREIGN KEY (`grade_id`) REFERENCES `tbl_grade`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程成绩单项得分表';

-- 5. 图书馆藏表 (tbl_book)
CREATE TABLE `tbl_book` (
    `isbn` VARCHAR(32) NOT NULL COMMENT 'ISBN编号',
    `title` VARCHAR(128) NOT NULL COMMENT '图书名称',
    `author` VARCHAR(64) NOT NULL COMMENT '作者',
    `publisher` VARCHAR(64) DEFAULT NULL COMMENT '出版社',
    `location` VARCHAR(64) DEFAULT NULL COMMENT '存放位置/书架',
    `resource_file` VARCHAR(255) DEFAULT NULL COMMENT '电子资源文件名（服务器本地存储索引），为空表示未录入',
    `total_num` INT NOT NULL DEFAULT 5 COMMENT '馆藏总数',
    `current_num` INT NOT NULL DEFAULT 5 COMMENT '当前可借余量',
    PRIMARY KEY (`isbn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书馆藏信息表';

-- 6. 图书借还记录表 (tbl_borrow_record)
CREATE TABLE `tbl_borrow_record` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '记录ID',
    `student_id` VARCHAR(32) NOT NULL COMMENT '借阅学生学号',
    `isbn` VARCHAR(32) NOT NULL COMMENT '图书ISBN',
    `borrow_date` VARCHAR(32) NOT NULL COMMENT '借出日期 (YYYY-MM-DD)',
    `due_date` VARCHAR(32) NOT NULL COMMENT '应还日期 (YYYY-MM-DD)',
    `return_date` VARCHAR(32) DEFAULT NULL COMMENT '归还日期',
    `status` VARCHAR(16) NOT NULL DEFAULT 'BORROWED' COMMENT '状态: BORROWED, RETURNED',
    `renew_count` INT NOT NULL DEFAULT 0 COMMENT '续借次数',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_br_student` FOREIGN KEY (`student_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_br_book` FOREIGN KEY (`isbn`) REFERENCES `tbl_book`(`isbn`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='借阅记录表';

-- 7. 商店商品表 (tbl_goods)
CREATE TABLE `tbl_goods` (
    `goods_id` VARCHAR(32) NOT NULL COMMENT '商品编码',
    `goods_name` VARCHAR(64) NOT NULL COMMENT '商品名称',
    `price` DECIMAL(10, 2) NOT NULL COMMENT '售价',
    `stock` INT NOT NULL DEFAULT 100 COMMENT '当前库存',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '商品描述',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ON_SHELF' COMMENT '状态: ON_SHELF 上架, OFF_SHELF 已下架',
    `image_path` VARCHAR(255) DEFAULT NULL COMMENT '商品图片文件名（服务器本地存储索引），为空表示暂无图片',
    PRIMARY KEY (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商店商品表';

-- 8. 商店消费订单表 (tbl_order)
CREATE TABLE `tbl_order` (
    `order_id` VARCHAR(64) NOT NULL COMMENT '订单流水号',
    `student_id` VARCHAR(32) NOT NULL COMMENT '购买人学号',
    `goods_id` VARCHAR(32) NOT NULL COMMENT '商品编码',
    `goods_name` VARCHAR(64) NOT NULL COMMENT '商品名称快照',
    `count` INT NOT NULL COMMENT '购买数量',
    `total_price` DECIMAL(10, 2) NOT NULL COMMENT '交易总金额',
    `order_time` VARCHAR(32) NOT NULL COMMENT '下单时间',
    PRIMARY KEY (`order_id`),
    CONSTRAINT `fk_order_student` FOREIGN KEY (`student_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_order_goods` FOREIGN KEY (`goods_id`) REFERENCES `tbl_goods`(`goods_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费订单记录表';

-- 9. 超市购物车表 (tbl_cart)
CREATE TABLE `tbl_cart` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '记录自增ID',
    `student_id` VARCHAR(32) NOT NULL COMMENT '购物人一卡通号',
    `goods_id` VARCHAR(32) NOT NULL COMMENT '商品编码',
    `count` INT NOT NULL DEFAULT 1 COMMENT '数量',
    `add_time` VARCHAR(32) NOT NULL COMMENT '加入时间 (yyyy-MM-dd HH:mm:ss)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_user_goods` (`student_id`, `goods_id`),
    CONSTRAINT `fk_cart_user` FOREIGN KEY (`student_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE,
    CONSTRAINT `fk_cart_goods` FOREIGN KEY (`goods_id`) REFERENCES `tbl_goods`(`goods_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超市购物车表';

-- ==========================================
-- 插入初始模拟测试数据
-- ==========================================

INSERT INTO `tbl_user` (`uid`, `password`, `role`, `name`, `balance`) VALUES
('admin', '123456', 'ADMIN', '系统管理员', 9999.00),
('213000001', '123456', 'STUDENT', '张三', 500.00),
('100001', '123456', 'TEACHER', '李教授', 1000.00),
('300001', '123456', 'LIBRARIAN', '图书管理员', 1000.00),
('jwc_test', '123456', 'ACADEMIC_AFFAIRS_TEACHER', '测试教务老师', 1000.00);

INSERT INTO `tbl_user` (`uid`, `password`, `role`, `name`, `balance`, `status`) VALUES
('200001', '123456', 'SELLER', '高老板', 0.00, 1);

INSERT INTO `tbl_student` (`uid`, `gender`, `department`, `major`, `class_name`, `phone`) VALUES
('213000001', '男', '计算机科学与工程学院', '软件工程', '2101班', '13800000000');

INSERT INTO `tbl_course` (`course_id`, `course_name`, `teacher_id`, `teacher_name`, `credits`, `open_semester`, `status`, `max_capacity`, `current_num`, `time_slot`, `classroom`, `start_week`, `end_week`) VALUES
('CS101', 'Java程序设计', '100001', '李教授', 3.0, '2026-2027-1', 'ACTIVE', 50, 0, '周一 第1-2节', '九龙湖计算机楼101', 1, 4),
('CS102', '数据结构与算法', '100001', '李教授', 4.0, '2026-2027-1', 'ACTIVE', 40, 0, '周三 第3-4节', '九龙湖计算机楼203', 1, 4);

INSERT INTO `tbl_course_score_component` (`course_id`, `component_name`, `weight`) VALUES
('CS101', '平时成绩', 0.400),
('CS101', '期末成绩', 0.600),
('CS102', '平时成绩', 0.300),
('CS102', '实验成绩', 0.200),
('CS102', '期末成绩', 0.500);

INSERT INTO `tbl_book` (`isbn`, `title`, `author`, `publisher`, `location`, `resource_file`, `total_num`, `current_num`) VALUES
('9787111213826', 'Java编程思想', 'Bruce Eckel', '机械工业出版社', '九龙湖馆三楼 TP312 区', NULL, 10, 10),
('9787115546081', '算法导论', 'Thomas H. Cormen', '人民邮电出版社', '九龙湖馆三楼 TP301 区', NULL, 5, 5),
('9787302520322', '计算机网络（第7版）', '谢希仁', '电子工业出版社', '九龙湖馆四楼 TN 区', NULL, 6, 6),
('9787115426799', '深入理解计算机系统', 'Randal E. Bryant', '机械工业出版社', '九龙湖馆三楼 TP303 区', NULL, 4, 4);

INSERT INTO `tbl_goods` (`goods_id`, `goods_name`, `price`, `stock`, `description`) VALUES
('G001', '东大纪念笔记本', 15.00, 100, '精装校徽文创笔记本'),
('G002', '晨光中性笔(黑)', 2.50, 200, '0.5mm 顺滑签字笔'),
('G003', '校园咖啡兑换券', 12.00, 50, '校内咖啡厅通用券');
