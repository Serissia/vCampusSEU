#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为指定学期的课程重新分配任课教师，保证教师课程时间不冲突。

教师池 = 原有教师（在其他学期已经开课的教师，如李教授、王教授）
       + 随机生成的教师（tbl_user 中 role='TEACHER' 的全部教师账号）。

冲突口径与客户端「教务安排课程时间」保持一致：**同一学期内**星期相同、周次区间
重叠、节次区间重叠才算冲突；不同学期的课程即使时间相同也不冲突。原有教师会被
保证分到至少 --min-original 门课，避免被随机结果漏掉。

输出：
  1. scripts/assign_course_teachers.sql —— 可直接执行的 UPDATE 语句；
  2. 就地同步 scripts/bulk_courses.sql 中每门课的教师工号，保证重新导入后一致。

用法（需要能访问数据库，密码通过 MYSQL_PWD 环境变量传入）：
    python scripts/assign_course_teachers.py
    python scripts/assign_course_teachers.py --semesters 2026-2027-2,2026-2027-3 --seed 2026
"""

import argparse
import os
import random
import re
import subprocess

DEFAULT_MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
SLOT_RE = re.compile(r"(\d+)-(\d+)周\s*(周[一二三四五六日])\s*第(\d+)-(\d+)节")
BULK_ROW_RE = re.compile(r"^\('([A-Za-z0-9]+)', '[^']*', '[^']*', '[^']*', '(\d+)', ''")


def query(mysql_exe, database, sql, user, host, port):
    """执行查询并返回按制表符切分的行。"""
    command = [mysql_exe, "-h", host, "-P", str(port), "-u", user,
               "-B", "-N", "--default-character-set=utf8mb4", database, "-e", sql]
    result = subprocess.run(command, capture_output=True)
    if result.returncode != 0:
        raise SystemExit("数据库查询失败（请确认 MYSQL_PWD 环境变量已设置为该用户的密码）："
                         + result.stderr.decode("utf-8", "replace"))
    text = result.stdout.decode("utf-8", "replace")
    return [line.split("\t") for line in text.splitlines() if line.strip()]


def parse_slots(time_slot):
    """把 time_slot 字符串解析成 (起周, 止周, 星期, 起节, 止节) 列表。"""
    slots = []
    if not time_slot:
        return slots
    for segment in time_slot.split(";"):
        match = SLOT_RE.search(segment)
        if match:
            slots.append((int(match.group(1)), int(match.group(2)), match.group(3),
                          int(match.group(4)), int(match.group(5))))
    return slots


def overlap(first, second):
    """星期相同、周次重叠且节次重叠即冲突。"""
    if first[2] != second[2]:
        return False
    for low_a, high_a, low_b, high_b in ((first[0], first[1], second[0], second[1]),
                                         (first[3], first[4], second[3], second[4])):
        if high_a < low_b or high_b < low_a:
            return False
    return True


def conflicts_with(slots, busy):
    """课程时间段是否与某位教师的已有安排冲突。"""
    for slot in slots:
        for other in busy:
            if overlap(slot, other):
                return True
    return False


def assign(courses, pool, busy_by_teacher, original_teachers, max_load,
           min_original, seed):
    """贪心分配教师：先保证原有教师有名额，再按负载均衡分配其余课程。"""
    rng = random.Random(seed)
    order = list(courses)
    rng.shuffle(order)
    load = {uid: 0 for uid in pool}
    assigned = {}

    def feasible(teacher, course):
        """该教师能否接下这门课：无时间冲突且未超出负载上限。"""
        if load[teacher] >= max_load:
            return False
        busy = busy_by_teacher.get((teacher, course["semester"]), [])
        return not conflicts_with(course["slots"], busy)

    def take(teacher, course):
        busy_by_teacher.setdefault((teacher, course["semester"]), []).extend(course["slots"])
        load[teacher] += 1
        assigned[course["course_id"]] = teacher

    remaining = []
    for course in order:
        if not course["slots"]:
            remaining.append(course)
            continue
        remaining.append(course)

    # 第一轮：优先满足原有教师的课程数量
    for teacher in sorted(original_teachers):
        need = min_original
        for course in list(remaining):
            if need <= 0:
                break
            if course["course_id"] in assigned:
                continue
            if feasible(teacher, course):
                take(teacher, course)
                need -= 1

    # 第二轮：其余课程按负载从低到高挑选教师
    for course in remaining:
        if course["course_id"] in assigned:
            continue
        candidates = [uid for uid in pool if feasible(uid, course)]
        if not candidates:
            continue
        lowest = min(load[uid] for uid in candidates)
        candidates = [uid for uid in candidates if load[uid] == lowest]
        take(rng.choice(candidates), course)

    # 第三轮：个别课程若因负载上限没分出去，放宽负载限制
    for course in remaining:
        if course["course_id"] in assigned:
            continue
        candidates = [uid for uid in pool
                      if not conflicts_with(course["slots"],
                                           busy_by_teacher.get((uid, course["semester"]), []))]
        if candidates:
            take(min(candidates, key=lambda uid: load[uid]), course)
        else:
            assigned[course["course_id"]] = course["current_teacher"]
    return assigned, load


def sync_bulk_file(path, assigned):
    """把新分配结果写回 bulk_courses.sql，保证重新导入后一致。"""
    if not os.path.exists(path):
        return 0
    with open(path, "r", encoding="utf-8") as handle:
        lines = handle.readlines()
    changed = 0
    teacher_ids = []
    for index, line in enumerate(lines):
        match = BULK_ROW_RE.match(line)
        if not match:
            continue
        course_id, old_teacher = match.group(1), match.group(2)
        new_teacher = assigned.get(course_id, old_teacher)
        if new_teacher and new_teacher != old_teacher:
            lines[index] = line.replace("'{}', ''".format(old_teacher),
                                        "'{}', ''".format(new_teacher), 1)
            changed += 1
        teacher_ids.append(new_teacher)
    # 教师姓名回填语句里的工号列表也要同步，否则新加入的教师姓名会回填不到
    if teacher_ids:
        normalized = "  AND c.`teacher_id` IN ({})".format(
            ", ".join("'{}'".format(uid) for uid in sorted(set(teacher_ids))))
        for index, line in enumerate(lines):
            if line.startswith("  AND c.`teacher_id` IN ("):
                lines[index] = normalized + "\n"
                break
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.writelines(lines)
    return changed


def main():
    parser = argparse.ArgumentParser(description="为指定学期的课程重新分配任课教师")
    parser.add_argument("--semesters", default="2026-2027-2,2026-2027-3",
                        help="需要重新分配教师的学期，多个用逗号分隔")
    parser.add_argument("--database", default="db_vcampus", help="数据库名")
    parser.add_argument("--user", default="root", help="数据库用户名")
    parser.add_argument("--host", default="127.0.0.1", help="数据库地址")
    parser.add_argument("--port", type=int, default=3306, help="数据库端口")
    parser.add_argument("--mysql", default=DEFAULT_MYSQL, help="mysql 客户端路径")
    parser.add_argument("--out", default="scripts/assign_course_teachers.sql",
                        help="输出的 UPDATE 语句文件")
    parser.add_argument("--bulk-file", default="scripts/bulk_courses.sql",
                        help="需要同步教师工号的批量课程文件")
    parser.add_argument("--max-load", type=int, default=3, help="每位教师最多承担的课程数")
    parser.add_argument("--min-original", type=int, default=2,
                        help="原有教师至少分到的课程数")
    parser.add_argument("--seed", type=int, default=20260917, help="随机种子")
    args = parser.parse_args()

    target_semesters = [item.strip() for item in args.semesters.split(",") if item.strip()]
    quoted = ", ".join("'{}'".format(item) for item in target_semesters)

    course_rows = query(args.mysql, args.database,
                        "SELECT course_id, teacher_id, open_semester, time_slot "
                        "FROM tbl_course WHERE status = 'ACTIVE'",
                        args.user, args.host, args.port)
    teacher_rows = query(args.mysql, args.database,
                         "SELECT uid, name FROM tbl_user WHERE role = 'TEACHER'",
                         args.user, args.host, args.port)

    pool = {row[0]: row[1] for row in teacher_rows if len(row) >= 2}
    if not pool:
        raise SystemExit("教师池为空，请先导入教师账号")

    busy_by_teacher = {}
    target_courses = []
    original_teachers = set()
    for row in course_rows:
        if len(row) < 4:
            continue
        course_id, teacher_id, semester, time_slot = row[0], row[1], row[2], row[3]
        slots = parse_slots(time_slot)
        if semester in target_semesters:
            target_courses.append({
                "course_id": course_id,
                "current_teacher": teacher_id,
                "semester": semester,
                "slots": slots,
            })
        else:
            if teacher_id:
                busy_by_teacher.setdefault((teacher_id, semester), []).extend(slots)
                original_teachers.add(teacher_id)

    if not target_courses:
        raise SystemExit("在 {} 中没找到课程".format(quoted))

    assigned, load = assign(target_courses, list(pool), busy_by_teacher,
                            original_teachers, args.max_load, args.min_original,
                            args.seed)

    # 自检：确认分配后不存在任何教师时间冲突
    final_busy = {}
    for row in course_rows:
        if len(row) < 4:
            continue
        course_id, teacher_id, semester, time_slot = row[0], row[1], row[2], row[3]
        owner = assigned.get(course_id, teacher_id)
        final_busy.setdefault((owner, semester), []).extend(parse_slots(time_slot))
    conflicts = []
    for (teacher, semester), slots in final_busy.items():
        for i in range(len(slots)):
            for j in range(i + 1, len(slots)):
                if overlap(slots[i], slots[j]):
                    conflicts.append((teacher, semester, slots[i], slots[j]))
    if conflicts:
        raise SystemExit("仍存在 {} 处教师时间冲突，请调整参数后重试".format(len(conflicts)))

    lines = [
        "-- 由 scripts/assign_course_teachers.py 生成：重新分配任课教师（已通过冲突自检）",
        "-- 学期：{}".format(", ".join(target_semesters)),
        "",
    ]
    for course in sorted(target_courses, key=lambda item: item["course_id"]):
        teacher_id = assigned.get(course["course_id"])
        if not teacher_id:
            continue
        lines.append("UPDATE `tbl_course` SET `teacher_id` = '{}', `teacher_name` = '{}' "
                     "WHERE `course_id` = '{}';".format(
                         teacher_id, pool.get(teacher_id, ""), course["course_id"]))
    lines.append("")
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")

    changed = sync_bulk_file(args.bulk_file, assigned)

    original_used = sum(1 for course in target_courses
                        if assigned.get(course["course_id"]) in original_teachers)
    random_used = len(target_courses) - original_used
    print("已生成 {}：共 {} 门课程".format(args.out, len(target_courses)))
    print("原有教师承担 {} 门，随机教师承担 {} 门".format(original_used, random_used))
    print("涉及的教师数：{}，单教师最多 {} 门".format(
        len([uid for uid, count in load.items() if count > 0]), max(load.values(), default=0)))
    print("同步 {} 中 {} 门课程的教师工号".format(args.bulk_file, changed))
    print("冲突自检：通过（0 处冲突）")


if __name__ == "__main__":
    main()
