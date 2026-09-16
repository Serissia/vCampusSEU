#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""批量生成学生 / 教师测试账号的 SQL 脚本。

账号规则：
  - 学生：uid 从 21324000003 起递增，phone 从 13900000001 起递增，
          姓名为随机中国人姓名，其余档案与张三一致（男、计算机科学与工程学院、
          软件工程、2101班），登录密码 123456，余额 500.00。
  - 教师：uid 从 100003 起递增，姓名为“随机姓 + 职称”，
          其余信息与李教授一致，登录密码 123456，余额 1000.00。

生成的 SQL 使用 INSERT IGNORE，重复执行不会因主键冲突报错。

用法：
    python scripts/generate_bulk_accounts.py
    python scripts/generate_bulk_accounts.py --students 2000 --teachers 100
    python scripts/generate_bulk_accounts.py --out scripts/bulk_accounts.sql --seed 2026
"""

import argparse
import random

# 常见姓氏，取自百家姓前段
SURNAMES = list(
    "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜"
    "戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳鲍史唐费"
    "廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄和"
    "穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁杜"
    "阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞"
    "万支柯管卢莫"
)

# 常用于名字的汉字，用于随机组合名字
GIVEN_CHARS = list(
    "伟芳娜秀英敏静丽强磊军洋勇艳杰娟涛明超霞平刚建华文博宇轩浩然子涵"
    "欣怡雨泽佳琪思远志晓东梦洁俊嘉雅宁彤鑫程晨星月云天辰一若初思源沐凡秋实"
)

# 教师姓名的职称后缀及其权重
TEACHER_TITLES = ["教授", "副教授", "讲师", "助教"]
TEACHER_TITLE_WEIGHTS = [3, 3, 5, 2]

STUDENT_DEPARTMENT = "计算机科学与工程学院"
STUDENT_MAJOR = "软件工程"
STUDENT_CLASS = "2101班"
STUDENT_GENDER = "男"

PASSWORD = "123456"
STUDENT_BALANCE = "500.00"
TEACHER_BALANCE = "1000.00"


def random_person_name(rng):
    """随机组合一个中国人姓名：姓 + 单字或双字名。"""
    surname = rng.choice(SURNAMES)
    given_len = 1 if rng.random() < 0.25 else 2
    given = "".join(rng.choice(GIVEN_CHARS) for _ in range(given_len))
    return surname + given


def random_teacher_name(rng):
    """随机组合教师姓名：姓 + 职称。"""
    surname = rng.choice(SURNAMES)
    title = rng.choices(TEACHER_TITLES, weights=TEACHER_TITLE_WEIGHTS, k=1)[0]
    return surname + title


def sql_escape(value):
    """转义 SQL 字符串中的单引号与反斜杠。"""
    return value.replace("\\", "\\\\").replace("'", "''")


def write_user_inserts(lines, rows, columns, chunk_size=200):
    """按固定行数分块写出 INSERT IGNORE 语句。"""
    for start in range(0, len(rows), chunk_size):
        chunk = rows[start:start + chunk_size]
        lines.append("INSERT IGNORE INTO `{}` ({}) VALUES".format(
            columns[0], ", ".join("`{}`".format(col) for col in columns[1])))
        for index, row in enumerate(chunk):
            suffix = ";" if index == len(chunk) - 1 else ","
            lines.append("(" + row + ")" + suffix)
        lines.append("")


def build_sql(student_count, teacher_count, student_uid_start, phone_start,
              teacher_uid_start, seed):
    rng = random.Random(seed)

    user_columns = ("tbl_user", ["uid", "password", "role", "name", "balance"])
    student_columns = ("tbl_student",
                       ["uid", "gender", "department", "major", "class_name", "phone"])

    student_user_rows = []
    student_profile_rows = []
    for offset in range(student_count):
        uid = str(student_uid_start + offset)
        phone = str(phone_start + offset)
        name = sql_escape(random_person_name(rng))
        student_user_rows.append(
            "'{}', '{}', 'STUDENT', '{}', {}".format(uid, PASSWORD, name, STUDENT_BALANCE))
        student_profile_rows.append(
            "'{}', '{}', '{}', '{}', '{}', '{}'".format(
                uid, STUDENT_GENDER, STUDENT_DEPARTMENT, STUDENT_MAJOR,
                STUDENT_CLASS, phone))

    teacher_user_rows = []
    for offset in range(teacher_count):
        uid = str(teacher_uid_start + offset)
        name = sql_escape(random_teacher_name(rng))
        teacher_user_rows.append(
            "'{}', '{}', 'TEACHER', '{}', {}".format(uid, PASSWORD, name, TEACHER_BALANCE))

    lines = [
        "-- 批量测试账号（由 scripts/generate_bulk_accounts.py 生成，请勿手工修改）",
        "-- 学生 {} 个：uid {} 起，phone {} 起".format(
            student_count, student_uid_start, phone_start),
        "-- 教师 {} 个：uid {} 起".format(teacher_count, teacher_uid_start),
        "",
        "-- 学生账号",
    ]
    write_user_inserts(lines, student_user_rows, user_columns)
    lines.append("-- 学生档案")
    write_user_inserts(lines, student_profile_rows, student_columns)
    lines.append("-- 教师账号")
    write_user_inserts(lines, teacher_user_rows, user_columns)
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="生成批量学生 / 教师账号的 SQL 脚本")
    parser.add_argument("--students", type=int, default=2000, help="学生账号数量")
    parser.add_argument("--teachers", type=int, default=100, help="教师账号数量")
    parser.add_argument("--student-uid-start", type=int, default=21324000003,
                        help="学生 uid 起始值")
    parser.add_argument("--phone-start", type=int, default=13900000001,
                        help="学生手机号起始值")
    parser.add_argument("--teacher-uid-start", type=int, default=100003,
                        help="教师 uid 起始值")
    parser.add_argument("--seed", type=int, default=20260916, help="随机种子，保证可复现")
    parser.add_argument("--out", default="scripts/bulk_accounts.sql",
                        help="输出的 SQL 文件路径")
    args = parser.parse_args()

    sql = build_sql(args.students, args.teachers, args.student_uid_start,
                    args.phone_start, args.teacher_uid_start, args.seed)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(sql)
    print("已生成 {}：学生 {} 个，教师 {} 个".format(args.out, args.students, args.teachers))


if __name__ == "__main__":
    main()
