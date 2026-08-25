CREATE DATABASE IF NOT EXISTS vcampus DEFAULT CHARACTER SET utf8mb4;
USE vcampus;

CREATE TABLE IF NOT EXISTS course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_code VARCHAR(32) NOT NULL UNIQUE,
    course_name VARCHAR(128) NOT NULL,
    credit DOUBLE NOT NULL DEFAULT 0,
    teacher_id VARCHAR(32) NOT NULL,
    teacher_name VARCHAR(64) DEFAULT '',
    capacity INT NOT NULL DEFAULT 0,
    selected_count INT NOT NULL DEFAULT 0,
    semester VARCHAR(32) DEFAULT '',
    class_time VARCHAR(128) DEFAULT '',
    location VARCHAR(128) DEFAULT '',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS course_selection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(32) NOT NULL,
    course_code VARCHAR(32) NOT NULL,
    select_time DATETIME,
    status VARCHAR(16) NOT NULL DEFAULT 'SELECTED',
    UNIQUE KEY uk_student_course (student_id, course_code)
);

CREATE TABLE IF NOT EXISTS grade (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(32) NOT NULL,
    course_code VARCHAR(32) NOT NULL,
    course_name VARCHAR(128) DEFAULT '',
    usual_score DOUBLE NOT NULL DEFAULT 0,
    exam_score DOUBLE NOT NULL DEFAULT 0,
    final_score DOUBLE NOT NULL DEFAULT 0,
    gpa DOUBLE NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    UNIQUE KEY uk_grade_student_course (student_id, course_code)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_number VARCHAR(32) NOT NULL UNIQUE,
    password VARCHAR(64) NOT NULL,
    name VARCHAR(64) DEFAULT '',
    role_code INT NOT NULL
);

INSERT INTO sys_user(account_number, password, name, role_code) VALUES
('10001', '123456', '教务老师', 1),
('10002', '123456', '图书管理员', 2),
('10003', '123456', '商店管理员', 3),
('10004', '123456', '教职工', 4),
('10005', '123456', '学生', 5)
ON DUPLICATE KEY UPDATE name = VALUES(name);
