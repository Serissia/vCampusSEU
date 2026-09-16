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
    `display_code` VARCHAR(32) NOT NULL COMMENT '课程展示代码，可与 course_id 相同或保持原始课程代码',
    `course_nature` VARCHAR(16) NOT NULL DEFAULT '选修' COMMENT '课程性质：必修/选修',
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

-- 4.3 课程评价表
CREATE TABLE `tbl_course_review` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '评价ID',
    `student_id` VARCHAR(32) NOT NULL COMMENT '评价学生账号',
    `course_id` VARCHAR(32) NOT NULL COMMENT '课程内部ID',
    `rating` INT NOT NULL COMMENT '评分 1-5',
    `comment` VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
    `anonymous` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否匿名：1 匿名，0 实名',
    `review_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_student_course` (`student_id`, `course_id`),
    CONSTRAINT `fk_review_student` FOREIGN KEY (`student_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_course` FOREIGN KEY (`course_id`) REFERENCES `tbl_course`(`course_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程评价表';

-- 5. 图书馆藏表 (tbl_book)
CREATE TABLE `tbl_book` (
    `isbn` VARCHAR(32) NOT NULL COMMENT 'ISBN编号',
    `title` VARCHAR(128) NOT NULL COMMENT '图书名称',
    `author` VARCHAR(64) NOT NULL COMMENT '作者',
    `publisher` VARCHAR(64) DEFAULT NULL COMMENT '出版社',
    `location` VARCHAR(64) DEFAULT NULL COMMENT '存放位置/书架',
    `resource_file` VARCHAR(255) DEFAULT NULL COMMENT '电子资源文件名（服务器本地存储索引），为空表示未录入',
    `type` VARCHAR(16) NOT NULL DEFAULT 'PHYSICAL' COMMENT '图书类型: PHYSICAL 实体书 / EBOOK 纯电子书',
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

-- 6.1 图书馆纯电子书投稿表 (tbl_ebook_submission)
CREATE TABLE `tbl_ebook_submission` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '投稿ID',
    `uploader_uid` VARCHAR(32) NOT NULL COMMENT '上传人学号/工号',
    `title` VARCHAR(128) NOT NULL COMMENT '书名',
    `author` VARCHAR(64) NOT NULL COMMENT '作者',
    `publisher` VARCHAR(64) NOT NULL COMMENT '出版社',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '简介',
    `resource_file` VARCHAR(255) NOT NULL COMMENT '电子资源文件名',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / APPROVED / REJECTED',
    `reviewer` VARCHAR(32) DEFAULT NULL COMMENT '审核人',
    `review_comment` VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
    `created_time` VARCHAR(32) NOT NULL COMMENT '提交时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_ebk_uploader` FOREIGN KEY (`uploader_uid`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图书馆纯电子书投稿表';

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

-- 9.1 校园二手市场商品表 (tbl_second_hand)
CREATE TABLE `tbl_second_hand` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '商品ID',
    `seller_id` VARCHAR(32) NOT NULL COMMENT '卖家一卡通号',
    `seller_name` VARCHAR(32) NOT NULL COMMENT '卖家姓名快照',
    `title` VARCHAR(64) NOT NULL COMMENT '商品标题',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '商品描述',
    `price` DECIMAL(10, 2) NOT NULL COMMENT '定价',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING 待审核, ON_SALE 在售, SOLD 已售/已下架, REJECTED 审核拒绝',
    `created_time` VARCHAR(32) NOT NULL COMMENT '发布时间 (yyyy-MM-dd HH:mm:ss)',
    `image_path` VARCHAR(255) DEFAULT NULL COMMENT '商品图片文件名（服务器本地存储索引），为空表示暂无图片',
    PRIMARY KEY (`id`),
    KEY `idx_sh_status` (`status`),
    CONSTRAINT `fk_sh_seller` FOREIGN KEY (`seller_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园二手市场商品表';

-- 9.2 二手商品调价日志表 (tbl_secondhand_price_log)
CREATE TABLE `tbl_secondhand_price_log` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '日志ID',
    `item_id` INT NOT NULL COMMENT '二手商品ID',
    `seller_id` VARCHAR(32) NOT NULL COMMENT '卖家一卡通号',
    `old_price` DECIMAL(10, 2) NOT NULL COMMENT '原价',
    `new_price` DECIMAL(10, 2) NOT NULL COMMENT '新价',
    `update_time` VARCHAR(32) NOT NULL COMMENT '调价时间 (yyyy-MM-dd HH:mm:ss)',
    PRIMARY KEY (`id`),
    KEY `idx_shpl_item` (`item_id`),
    CONSTRAINT `fk_shpl_item` FOREIGN KEY (`item_id`) REFERENCES `tbl_second_hand`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手商品调价日志表';

-- 9.2 二手交易订单表 (tbl_second_hand_order)
CREATE TABLE `tbl_second_hand_order` (
    `order_id` VARCHAR(64) NOT NULL COMMENT '订单流水号',
    `buyer_id` VARCHAR(32) NOT NULL COMMENT '买家一卡通号',
    `seller_id` VARCHAR(32) NOT NULL COMMENT '卖家一卡通号',
    `item_id` INT NOT NULL COMMENT '二手商品 ID',
    `title` VARCHAR(64) NOT NULL COMMENT '商品标题快照',
    `price` DECIMAL(10, 2) NOT NULL COMMENT '成交价',
    `order_time` VARCHAR(32) NOT NULL COMMENT '成交时间 (yyyy-MM-dd HH:mm:ss)',
    PRIMARY KEY (`order_id`),
    KEY `idx_sho_buyer` (`buyer_id`),
    KEY `idx_sho_seller` (`seller_id`),
    CONSTRAINT `fk_sho_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE,
    CONSTRAINT `fk_sho_seller` FOREIGN KEY (`seller_id`) REFERENCES `tbl_user`(`uid`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手交易订单记录表';

-- 9.3 二手商品买卖双方聊天记录表 (tbl_chat_message)
CREATE TABLE `tbl_chat_message` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '消息ID',
    `item_id` INT NOT NULL COMMENT '关联的二手商品 ID',
    `from_uid` VARCHAR(32) NOT NULL COMMENT '发送者一卡通号',
    `from_name` VARCHAR(32) NOT NULL COMMENT '发送者姓名快照',
    `to_uid` VARCHAR(32) NOT NULL COMMENT '接收者一卡通号',
    `content` VARCHAR(500) NOT NULL COMMENT '消息内容',
    `send_time` VARCHAR(32) NOT NULL COMMENT '发送时间 (yyyy-MM-dd HH:mm:ss)',
    PRIMARY KEY (`id`),
    KEY `idx_chat_item` (`item_id`),
    KEY `idx_chat_from` (`from_uid`),
    KEY `idx_chat_to` (`to_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二手商品买卖双方聊天记录表';

-- 10. 教务处公告表 (tbl_notice)
CREATE TABLE `tbl_notice` (
    `id` INT AUTO_INCREMENT NOT NULL COMMENT '公告自增ID',
    `title` VARCHAR(255) NOT NULL COMMENT '公告标题',
    `publish_date` VARCHAR(32) NOT NULL COMMENT '发布日期 (YYYY-MM-DD)',
    `category` VARCHAR(64) DEFAULT '教务公告' COMMENT '所属栏目',
    `url` VARCHAR(512) NOT NULL COMMENT '公告原文链接',
    `crawled_time` VARCHAR(32) NOT NULL COMMENT '爬取入库时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_url` (`url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教务处公告信息表';

-- 10.1 公告元数据表 (tbl_notice_meta)
CREATE TABLE `tbl_notice_meta` (
    `meta_key` VARCHAR(32) NOT NULL COMMENT '配置键',
    `meta_value` VARCHAR(255) NOT NULL COMMENT '配置值',
    PRIMARY KEY (`meta_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告系统元数据表';

INSERT INTO `tbl_notice_meta` (`meta_key`, `meta_value`) VALUES
('last_sync_time', '暂无记录');

-- ==========================================
-- 插入初始模拟测试数据
-- ==========================================

INSERT INTO `tbl_user` (`uid`, `password`, `role`, `name`, `balance`) VALUES
('admin', '123456', 'ADMIN', '系统管理员', 9999.00),
('213000001', '123456', 'STUDENT', '张三', 500.00),
('213000002', '123456', 'STUDENT', '李四', 500.00),
('100001', '123456', 'TEACHER', '李教授', 1000.00),
('100002', '123456', 'TEACHER', '王教授', 1000.00),
('300001', '123456', 'LIBRARIAN', '图书管理员', 1000.00),
('jwc_test', '123456', 'ACADEMIC_AFFAIRS_TEACHER', '测试教务老师', 1000.00);

INSERT INTO `tbl_user` (`uid`, `password`, `role`, `name`, `balance`, `status`) VALUES
('200001', '123456', 'SELLER', '高老板', 0.00, 1);

INSERT INTO `tbl_student` (`uid`, `gender`, `department`, `major`, `class_name`, `phone`) VALUES
('213000001', '男', '计算机科学与工程学院', '软件工程', '2101班', '13800000000'),
('213000002', '女', '计算机科学与工程学院', '软件工程', '2102班', '13900000000');

INSERT INTO `tbl_course` (`course_id`, `course_name`, `display_code`, `course_nature`, `teacher_id`, `teacher_name`, `credits`, `open_semester`, `status`, `max_capacity`, `current_num`, `time_slot`, `classroom`, `start_week`, `end_week`) VALUES
('CS101', 'Java程序设计', 'CS101', '必修', '100001', '李教授', 3.0, '2026-2027-1', 'ACTIVE', 50, 0, '周一 第1-2节', '九龙湖计算机楼101', 1, 4),
('CS101WANG', 'Java程序设计', 'CS101', '必修', '100002', '王教授', 3.0, '2026-2027-1', 'ACTIVE', 50, 0, '周一 第1-2节', '九龙湖计算机楼', 1, 4),
('CS102', '数据结构与算法', 'CS102', '必修', '100001', '李教授', 4.0, '2026-2027-1', 'ACTIVE', 40, 0, '周三 第3-4节', '九龙湖计算机楼203', 1, 4),
('DB101', '数据库原理', 'DB101', '必修', '100001', '李教授', 3.0, '2026-2027-1', 'ACTIVE', 60, 0, '1-4周 周一 第3-5节', '教三-302', 1, 4),
('CC101', '编译原理', 'CC101', '必修', '100002', '王教授', 3.0, '2026-2027-1', 'ACTIVE', 60, 0, '1-4周 周三 第3-5节;1-4周 周二 第8-9节', '教六-302', 1, 4),
('ML101', '机器学习(研讨)', 'ML101', '选修', '100002', '王教授', 2.0, '2026-2027-1', 'ACTIVE', 40, 0, '1-4周 周五 第3-5节', '教三-403', 1, 4),
('AD101', '智能汽车与自动驾驶(全英文)(研讨)', 'AD101', '选修', '100002', '王教授', 2.0, '2026-2027-1', 'ACTIVE', 40, 0, '1-4周 周一 第6-7节', '教二-303', 1, 4),
('VA101', '虚拟现实与增强现实(研讨)', 'VA101', '选修', '100002', '王教授', 2.0, '2026-2027-1', 'ACTIVE', 40, 0, '1-4周 周四 第3-5节', '教五-402', 1, 4),
('MA101', '移动应用开发(研讨)', 'MA101', '选修', '100002', '王教授', 2.0, '2026-2027-1', 'ACTIVE', 40, 0, '1-4周 周二 第3-5节', '教四-402', 1, 4),
('SE101', '软件工程(研讨)', 'SE101', '选修', '100002', '王教授', 2.0, '2026-2027-1', 'ACTIVE', 40, 0, '1-4周 周三 第6-7节', '教三-402', 1, 4),
('CSA101', '计算机系统结构', 'CSA101', '必修', '100001', '李教授', 3.0, '2026-2027-1', 'ACTIVE', 60, 0, '1-4周 周二 第6-7节', '教四-101', 1, 4);

INSERT INTO `tbl_course_score_component` (`course_id`, `component_name`, `weight`) VALUES
('CS101', '平时成绩', 0.400),
('CS101', '期末成绩', 0.600),
('CS101WANG', '平时成绩', 0.400),
('CS101WANG', '期末成绩', 0.600),
('CS102', '平时成绩', 0.300),
('CS102', '实验成绩', 0.200),
('CS102', '期末成绩', 0.500),
('DB101', '平时成绩', 0.400),
('DB101', '期末成绩', 0.600),
('CC101', '平时成绩', 0.400),
('CC101', '期末成绩', 0.600),
('ML101', '课堂表现', 0.300),
('ML101', '课程报告', 0.300),
('ML101', '期末成绩', 0.400),
('AD101', '课堂表现', 0.300),
('AD101', '课程报告', 0.300),
('AD101', '期末成绩', 0.400),
('CSA101', '平时成绩', 0.400),
('CSA101', '期末成绩', 0.600);

INSERT INTO `tbl_book` (`isbn`, `title`, `author`, `publisher`, `location`, `resource_file`, `total_num`, `current_num`) VALUES
('9787111213826', 'Java编程思想', 'Bruce Eckel', '机械工业出版社', '九龙湖馆三楼 TP312 区', NULL, 10, 10),
('9787115546081', '算法导论', 'Thomas H. Cormen', '人民邮电出版社', '九龙湖馆三楼 TP301 区', NULL, 5, 5),
('9787302520322', '计算机网络（第7版）', '谢希仁', '电子工业出版社', '九龙湖馆四楼 TN 区', NULL, 6, 6),
('9787115426799', '深入理解计算机系统', 'Randal E. Bryant', '机械工业出版社', '九龙湖馆三楼 TP303 区', NULL, 4, 4);

INSERT INTO `tbl_book` (`isbn`, `title`, `author`, `publisher`, `location`, `resource_file`, `total_num`, `current_num`) VALUES
('9787115546128', '数据库系统概念', 'Abraham Silberschatz', '机械工业出版社', '九龙湖馆三楼 TP311 区', NULL, 8, 8),
('9787115546098', '操作系统导论', 'Remzi H. Arpaci-Dusseau', '人民邮电出版社', '九龙湖馆四楼 TP316 区', NULL, 6, 6),
('9787115546104', '编译原理', 'Alfred V. Aho', '机械工业出版社', '九龙湖馆三楼 TP314 区', NULL, 7, 7),
('9787115546111', '人工智能：一种现代的方法', 'Stuart Russell', '人民邮电出版社', '九龙湖馆四楼 TP18 区', NULL, 5, 5),
('9787115459602', '软件工程：实践者的研究方法', 'Roger S. Pressman', '机械工业出版社', '九龙湖馆三楼 TP311 区', NULL, 8, 8),
('9787115351029', '高等数学（第七版）上册', '同济大学数学系', '高等教育出版社', '九龙湖馆二楼 O13 区', NULL, 12, 12),
('9787040396638', '线性代数（第六版）', '同济大学数学系', '高等教育出版社', '九龙湖馆二楼 O151 区', NULL, 10, 10),
('9787302517599', '概率论与数理统计', '盛骤', '高等教育出版社', '九龙湖馆二楼 O21 区', NULL, 10, 10),
('9787115546135', '机器学习', '周志华', '清华大学出版社', '九龙湖馆四楼 TP181 区', NULL, 9, 9),
('9787115546142', '深度学习', 'Ian Goodfellow', '人民邮电出版社', '九龙湖馆四楼 TP181 区', NULL, 6, 6),
('9787115459558', '鸟哥的Linux私房菜', '鸟哥', '人民邮电出版社', '九龙湖馆四楼 TP316 区', NULL, 7, 7),
('9787115546159', 'C++ Primer（第5版）', 'Stanley B. Lippman', '电子工业出版社', '九龙湖馆三楼 TP312 区', NULL, 8, 8),
('9787115546166', 'JavaScript高级程序设计', 'Matt Frisbie', '人民邮电出版社', '九龙湖馆三楼 TP312 区', NULL, 6, 6),
('9787115546173', 'Python编程：从入门到实践', 'Eric Matthes', '人民邮电出版社', '九龙湖馆三楼 TP311 区', NULL, 9, 9),
('9787115546180', '算法（第4版）', 'Robert Sedgewick', '人民邮电出版社', '九龙湖馆三楼 TP301 区', NULL, 6, 6),
('9787115546197', '数字逻辑与计算机组成', '袁春风', '机械工业出版社', '九龙湖馆三楼 TP303 区', NULL, 5, 5),
('9787115546203', '计算机网络：自顶向下方法', 'James F. Kurose', '机械工业出版社', '九龙湖馆四楼 TN 区', NULL, 6, 6),
('9787115546210', '通信原理', '樊昌信', '国防工业出版社', '九龙湖馆四楼 TN91 区', NULL, 7, 7),
('9787115546227', '经济学原理', 'N. Gregory Mankiw', '北京大学出版社', '九龙湖馆五楼 F0 区', NULL, 5, 5),
('9787115546234', '管理学', '斯蒂芬·罗宾斯', '中国人民大学出版社', '九龙湖馆五楼 C93 区', NULL, 6, 6),
('9787115546241', '大学英语综合教程', '李荫华', '上海外语教育出版社', '九龙湖馆五楼 H31 区', NULL, 10, 10),
('9787115546258', '中国哲学简史', '冯友兰', '北京大学出版社', '九龙湖馆五楼 B2 区', NULL, 4, 4),
('9787115546265', '万历十五年', '黄仁宇', '生活·读书·新知三联书店', '九龙湖馆五楼 K248 区', NULL, 6, 6),
('9787115546272', '百年孤独', '加西亚·马尔克斯', '南海出版公司', '九龙湖馆五楼 I775 区', NULL, 4, 4);

INSERT INTO `tbl_goods` (`goods_id`, `goods_name`, `price`, `stock`, `description`) VALUES
('G001', '东大纪念笔记本', 15.00, 100, '精装校徽文创笔记本'),
('G002', '晨光中性笔(黑)', 2.50, 200, '0.5mm 顺滑签字笔'),
('G003', '校园咖啡兑换券', 12.00, 50, '校内咖啡厅通用券');
INSERT INTO `tbl_goods` (`goods_id`, `goods_name`, `price`, `stock`, `description`) VALUES
('G004', '东南大学纪念帆布袋', 25.00, 80, '校徽图案环保帆布手提袋'),
('G005', '东大校徽金属书签', 9.90, 150, '镂空校徽书签'),
('G006', '校园文创保温杯', 45.00, 60, '不锈钢保温杯 350ml'),
('G007', '东大风景明信片套装', 18.00, 120, '校园风景明信片 6 张'),
('G008', '校徽钥匙扣', 12.00, 180, '合金校徽钥匙扣'),
('G009', '宿舍护眼台灯', 59.00, 40, '三档调光 LED 台灯'),
('G010', '便携折叠雨伞', 35.00, 70, '晴雨两用折叠伞'),
('G011', '校园文具套装', 22.00, 100, '含笔记本、中性笔和便签');
INSERT INTO `tbl_goods` (`goods_id`, `goods_name`, `price`, `stock`, `description`) VALUES
('G012', '大礼堂毛绒玩偶', 49.00, 60, '大礼堂造型毛绒纪念玩偶'),
('G013', '校园洗漱收纳包', 29.90, 80, '防泼水旅行洗漱收纳包'),
('G014', '校景校徽建筑卡套', 15.00, 120, '校景校徽主题建筑卡套'),
('G015', '校史留念徽章套盒', 39.90, 50, '校史纪念徽章套装礼盒'),
('G016', '东大字绘冰箱贴', 12.00, 150, '东大字绘校园冰箱贴');
INSERT INTO `tbl_goods` (`goods_id`, `goods_name`, `price`, `stock`, `description`) VALUES
('G017', '百醇', 6.50, 120, '巧克力味注心饼干，休闲零食'),
('G018', '背包', 89.00, 40, '校园风双肩背包，轻便耐装'),
('G019', '草稿纸', 5.00, 200, '日常学习用草稿纸'),
('G020', '脆香米', 5.50, 120, '牛奶巧克力脆米零食'),
('G021', '订书机', 12.00, 60, '办公学习两用订书机'),
('G022', '东大彩绘明信片', 15.00, 100, '东南大学主题彩绘明信片'),
('G023', '东大建筑四季徽章', 39.90, 80, '东大建筑四季主题徽章'),
('G024', '东大领带', 49.00, 30, '校徽元素正式领带'),
('G025', '东大松鼠练习本', 8.00, 150, '松鼠主题校园练习本'),
('G026', '东南大学可爱风雨伞', 35.00, 60, '晴雨两用校园主题雨伞'),
('G027', '东南大学练习本', 7.00, 160, '东南大学主题学习练习本'),
('G028', '固体胶', 3.50, 200, '日常手工与学习用固体胶'),
('G029', '果酱饼干', 6.00, 100, '果酱夹心酥脆饼干'),
('G030', '好丽友呀土豆', 5.50, 120, '香脆土豆膨化零食'),
('G031', '建筑雕塑模型', 69.00, 30, '校园地标建筑雕塑摆件'),
('G032', '劲仔小鱼', 4.50, 150, '香辣小鱼休闲零食'),
('G033', '乐事薯片', 6.50, 120, '经典香脆薯片'),
('G034', '六朝松手账本', 18.00, 80, '六朝松主题校园手账本'),
('G035', '绿箭口香糖', 5.00, 160, '清新薄荷口香糖'),
('G036', '麻辣王子辣条', 3.50, 160, '麻辣风味面筋零食'),
('G037', '美好时光海苔', 5.50, 120, '即食海苔休闲零食'),
('G038', '亲嘴烧辣条', 3.50, 160, '香辣面筋零食'),
('G039', '雀巢脆脆鲨', 6.00, 120, '巧克力威化饼干'),
('G040', '鼠标垫', 12.00, 100, '简洁校园风桌面鼠标垫'),
('G041', '双面胶', 3.00, 180, '手工与学习用双面胶'),
('G042', '四季主题笔记本', 16.00, 100, '校园四季主题记录本'),
('G043', '陶瓷马克杯', 29.90, 60, '校园文创陶瓷马克杯'),
('G044', '透光明信片', 10.00, 120, '校园风景透光明信片'),
('G045', '旺旺小小酥', 5.00, 120, '香脆米果休闲零食'),
('G046', '小米聚能写中性笔', 3.00, 200, '顺滑速干学生中性笔'),
('G047', '小松鼠折叠灯', 45.00, 50, '松鼠造型可折叠桌面灯'),
('G048', '心相印纸巾', 7.00, 150, '便携抽取式面巾纸'),
('G049', '修正带', 4.50, 180, '顺滑覆盖学生修正带'),
('G050', '盐津铺子鱼豆腐', 5.00, 140, '香辣味即食鱼豆腐'),
('G051', '樱花橡皮', 3.00, 180, '樱花造型学生橡皮'),
('G052', '云可柔卷纸', 10.00, 100, '柔软亲肤家用卷纸'),
('G053', '掌心脆', 3.00, 160, '香脆即食干脆面'),
('G054', '主题杯垫', 8.00, 120, '校园建筑主题桌面杯垫'),
('G055', '主题木尺', 6.00, 150, '校园主题木质直尺');

UPDATE `tbl_goods` SET `image_path` = 'g001_notebook.jpeg' WHERE `goods_id` = 'G001';
UPDATE `tbl_goods` SET `image_path` = 'g002_pen.jpeg' WHERE `goods_id` = 'G002';
UPDATE `tbl_goods` SET `image_path` = 'g003_coffee_voucher.jpeg' WHERE `goods_id` = 'G003';
UPDATE `tbl_goods` SET `image_path` = 'g004_tote_bag.jpg' WHERE `goods_id` = 'G004';
UPDATE `tbl_goods` SET `image_path` = 'g005_bookmark.jpg' WHERE `goods_id` = 'G005';
UPDATE `tbl_goods` SET `image_path` = 'g006_thermos.jpeg' WHERE `goods_id` = 'G006';
UPDATE `tbl_goods` SET `image_path` = 'g007_postcards.jpg' WHERE `goods_id` = 'G007';
UPDATE `tbl_goods` SET `image_path` = 'g008_keychain.jpeg' WHERE `goods_id` = 'G008';
UPDATE `tbl_goods` SET `image_path` = 'g009_lamp.jpeg' WHERE `goods_id` = 'G009';
UPDATE `tbl_goods` SET `image_path` = 'g010_umbrella.jpeg' WHERE `goods_id` = 'G010';
UPDATE `tbl_goods` SET `image_path` = 'g011_stationery_set.jpg' WHERE `goods_id` = 'G011';
UPDATE `tbl_goods` SET `image_path` = 'g012_hall_plush.jpg' WHERE `goods_id` = 'G012';
UPDATE `tbl_goods` SET `image_path` = 'g013_toiletry_bag.jpg' WHERE `goods_id` = 'G013';
UPDATE `tbl_goods` SET `image_path` = 'g014_card_holder.jpg' WHERE `goods_id` = 'G014';
UPDATE `tbl_goods` SET `image_path` = 'g015_badge_box.jpg' WHERE `goods_id` = 'G015';
UPDATE `tbl_goods` SET `image_path` = 'g016_fridge_magnet.jpg' WHERE `goods_id` = 'G016';
UPDATE `tbl_goods` SET `image_path` = 'g017_bai_chun.jpg' WHERE `goods_id` = 'G017';
UPDATE `tbl_goods` SET `image_path` = 'g018_backpack.jpg' WHERE `goods_id` = 'G018';
UPDATE `tbl_goods` SET `image_path` = 'g019_draft_paper.jpg' WHERE `goods_id` = 'G019';
UPDATE `tbl_goods` SET `image_path` = 'g020_crispy_rice.jpg' WHERE `goods_id` = 'G020';
UPDATE `tbl_goods` SET `image_path` = 'g021_stapler.jpg' WHERE `goods_id` = 'G021';
UPDATE `tbl_goods` SET `image_path` = 'g022_seu_postcards.jpg' WHERE `goods_id` = 'G022';
UPDATE `tbl_goods` SET `image_path` = 'g023_seu_season_badges.jpg' WHERE `goods_id` = 'G023';
UPDATE `tbl_goods` SET `image_path` = 'g024_seu_tie.jpg' WHERE `goods_id` = 'G024';
UPDATE `tbl_goods` SET `image_path` = 'g025_squirrel_notebook.png' WHERE `goods_id` = 'G025';
UPDATE `tbl_goods` SET `image_path` = 'g026_seu_umbrella.jpg' WHERE `goods_id` = 'G026';
UPDATE `tbl_goods` SET `image_path` = 'g027_seu_notebook.jpg' WHERE `goods_id` = 'G027';
UPDATE `tbl_goods` SET `image_path` = 'g028_glue_stick.jpg' WHERE `goods_id` = 'G028';
UPDATE `tbl_goods` SET `image_path` = 'g029_jam_biscuits.jpg' WHERE `goods_id` = 'G029';
UPDATE `tbl_goods` SET `image_path` = 'g030_orion_potato.jpg' WHERE `goods_id` = 'G030';
UPDATE `tbl_goods` SET `image_path` = 'g031_architecture_model.jpg' WHERE `goods_id` = 'G031';
UPDATE `tbl_goods` SET `image_path` = 'g032_jinzai_small_fish.jpg' WHERE `goods_id` = 'G032';
UPDATE `tbl_goods` SET `image_path` = 'g033_lays_chips.jpg' WHERE `goods_id` = 'G033';
UPDATE `tbl_goods` SET `image_path` = 'g034_liuchao_pine_journal.png' WHERE `goods_id` = 'G034';
UPDATE `tbl_goods` SET `image_path` = 'g035_green_chewing_gum.jpg' WHERE `goods_id` = 'G035';
UPDATE `tbl_goods` SET `image_path` = 'g036_spicy_prince.jpg' WHERE `goods_id` = 'G036';
UPDATE `tbl_goods` SET `image_path` = 'g037_haitai_seaweed.jpg' WHERE `goods_id` = 'G037';
UPDATE `tbl_goods` SET `image_path` = 'g038_qinzui_shao.jpg' WHERE `goods_id` = 'G038';
UPDATE `tbl_goods` SET `image_path` = 'g039_nestle_crispy_wafer.jpg' WHERE `goods_id` = 'G039';
UPDATE `tbl_goods` SET `image_path` = 'g040_mouse_pad.jpg' WHERE `goods_id` = 'G040';
UPDATE `tbl_goods` SET `image_path` = 'g041_double_sided_tape.jpg' WHERE `goods_id` = 'G041';
UPDATE `tbl_goods` SET `image_path` = 'g042_four_seasons_notebook.jpg' WHERE `goods_id` = 'G042';
UPDATE `tbl_goods` SET `image_path` = 'g043_ceramic_mug.jpg' WHERE `goods_id` = 'G043';
UPDATE `tbl_goods` SET `image_path` = 'g044_transparent_postcard.png' WHERE `goods_id` = 'G044';
UPDATE `tbl_goods` SET `image_path` = 'g045_want_want_crispy.jpg' WHERE `goods_id` = 'G045';
UPDATE `tbl_goods` SET `image_path` = 'g046_xiaomi_pen.jpg' WHERE `goods_id` = 'G046';
UPDATE `tbl_goods` SET `image_path` = 'g047_squirrel_folding_lamp.png' WHERE `goods_id` = 'G047';
UPDATE `tbl_goods` SET `image_path` = 'g048_xinxiangyin_tissue.jpg' WHERE `goods_id` = 'G048';
UPDATE `tbl_goods` SET `image_path` = 'g049_correction_tape.jpg' WHERE `goods_id` = 'G049';
UPDATE `tbl_goods` SET `image_path` = 'g050_yanjin_potato_tofu.jpg' WHERE `goods_id` = 'G050';
UPDATE `tbl_goods` SET `image_path` = 'g051_sakura_eraser.jpg' WHERE `goods_id` = 'G051';
UPDATE `tbl_goods` SET `image_path` = 'g052_yunkerou_toilet_paper.jpg' WHERE `goods_id` = 'G052';
UPDATE `tbl_goods` SET `image_path` = 'g053_zhangxin_crispy.jpg' WHERE `goods_id` = 'G053';
UPDATE `tbl_goods` SET `image_path` = 'g054_theme_coaster.jpg' WHERE `goods_id` = 'G054';
UPDATE `tbl_goods` SET `image_path` = 'g055_wooden_ruler.png' WHERE `goods_id` = 'G055';

INSERT INTO `tbl_second_hand` (`seller_id`, `seller_name`, `title`, `description`, `price`, `status`, `created_time`, `image_path`) VALUES
('213000002', '李四', '计算机组成原理（任国林版）二手书', '笔记齐全九成新，考试重点已标注', 999.00, 'ON_SALE', '2026-09-01 10:00:00', 'sh005_computer_organization_book.jpg'),
('213000002', '李四', '99新置物架', '宿舍用三层小置物架，99新', 20.00, 'ON_SALE', '2026-09-01 10:30:00', 'sh001_storage_rack.jpg'),
('213000002', '李四', '雅思真题书两本', '雅思备考书两本，成色良好，打包出', 15.00, 'ON_SALE', '2026-09-01 11:00:00', 'sh002_ielts_books.jpg'),
('213000002', '李四', '宿舍用小功率吹风机', '小功率宿舍可用，买来没用过几次', 25.00, 'ON_SALE', '2026-09-01 11:30:00', 'sh003_hair_dryer.png'),
('213000002', '李四', '法学大二专业书籍', '均为一手官方渠道购买，笔记勾画较少，多买可送电子版期末资料', 15.00, 'ON_SALE', '2026-09-01 12:00:00', 'sh004_law_books.jpg'),
('213000002', '李四', '胶卷相机', '复古胶卷相机，功能正常，适合日常拍照', 199.00, 'ON_SALE', '2026-09-01 12:30:00', 'sh006_film_camera.jpg'),
('213000002', '李四', '景德镇陶瓷工艺品', '景德镇陶瓷工艺品，可作桌面摆件', 20.00, 'ON_SALE', '2026-09-01 13:00:00', 'sh007_jingdezhen_ceramic.jpg'),
('213000002', '李四', '蓝虎鲸毛绒挂件', '蓝虎鲸毛绒挂件，柔软可爱，适合挂包', 15.00, 'ON_SALE', '2026-09-01 13:30:00', 'sh008_blue_whale_keychain.jpg'),
('213000002', '李四', '暑校智能机器人课程套件', '暑校课程智能机器人，配套齐全', 600.00, 'ON_SALE', '2026-09-01 14:00:00', 'sh009_summer_school_robot.jpg'),
('213000002', '李四', '全新瑜伽垫', '全新未使用，轻便易收纳', 30.00, 'ON_SALE', '2026-09-01 14:30:00', 'sh010_yoga_mat.png'),
('213000002', '李四', '九成新羽毛球拍', '只用过一次，轻盈好用，九成新', 35.00, 'ON_SALE', '2026-09-01 15:00:00', 'sh011_badminton_racket.png'),
('213000002', '李四', '智能车C车模摄像头组全套', '智能车C车模摄像头组全套，适合课程实践', 260.00, 'ON_SALE', '2026-09-01 15:30:00', 'sh012_smart_car_camera_set.jpg');

INSERT INTO `tbl_course_review` (`student_id`, `course_id`, `rating`, `comment`, `anonymous`, `review_time`) VALUES
('213000002', 'CS101WANG', 5, '夯', 0, NOW());
