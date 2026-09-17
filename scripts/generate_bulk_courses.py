#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为指定学期批量生成随机课程的 SQL 脚本。

生成内容：
  - 课程写入 tbl_course，状态为 ACTIVE，可直接被学生选课、显示在课表中；
  - 上课时间按客户端解析格式书写（多个时间段用 ";" 分隔）：
        "1-16周 周一 第3-5节;1-16周 周三 第1-2节"
  - 同一位教师的课程之间不会出现时间冲突（周次、星期、节次均不重叠）；
  - 每个课程同时生成成绩组成记录，写入 tbl_course_score_component；
  - 教师姓名留待导入后由 UPDATE 从 tbl_user 回填，保证与账号表一致。

用法：
    python scripts/generate_bulk_courses.py
    python scripts/generate_bulk_courses.py --count 50 --semesters 2026-2027-2,2026-2027-3
    python scripts/generate_bulk_courses.py --out scripts/002_bulk_courses.sql --seed 2026
"""

import argparse
import random
import re

# 课程名候选池，按“计算机 / 软件工程 / 公共基础”分类整理
COURSE_NAMES = [
    "程序设计基础(C语言)", "面向对象程序设计", "数据结构与算法(研讨)", "算法设计与分析",
    "计算机组成原理", "计算机体系结构", "汇编语言程序设计", "数字逻辑电路",
    "操作系统", "操作系统原理与实践", "计算机网络", "计算机网络实验",
    "数据库系统", "数据库应用实践", "编译技术", "软件工程导论", "软件测试技术",
    "需求工程", "软件项目管理", "软件体系结构", "人机交互技术", "开源软件实践",
    "人工智能导论", "机器学习基础", "深度学习实践", "强化学习", "知识图谱",
    "自然语言处理", "计算机视觉", "模式识别", "智能计算系统", "边缘计算",
    "计算机图形学", "数字图像处理", "虚拟现实技术", "游戏引擎原理",
    "信息安全导论", "密码学基础", "网络安全实践", "区块链技术",
    "分布式系统", "云计算与大数据", "并行计算", "嵌入式系统", "物联网技术",
    "移动应用开发", "Web前端开发", "Web后端开发", "微服务架构", "Python数据分析",
    "离散数学", "概率论与数理统计", "线性代数(工科)", "高等数学(工科)",
    "大学物理(工科)", "工程制图基础", "工程伦理", "科技论文写作",
    "创新创业实践", "智能硬件设计", "数据可视化", "智能汽车与自动驾驶(研讨)",
    "编译原理课程设计", "软件工程综合实践",
]

# 课程代码前缀，与已有课程的编号段（1xx）区分开
CODE_PREFIXES = ["CS", "SE", "AI", "DS", "NET", "DB", "IS", "CG"]

# 上课地点
CLASSROOM_BUILDINGS = ["九龙湖计算机楼", "教一", "教二", "教三", "教四", "教五", "教六"]

DAYS = ["周一", "周二", "周三", "周四", "周五"]

CREDITS = [1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0]
CAPACITIES = [30, 40, 50, 60, 80]

# 每学期可选周数：第 1 学期 4 周，第 2、3 学期 18 周
WEEKS_BY_SEMESTER_NO = {1: 4, 2: 18, 3: 18}

# 成绩组成模板，权重合计为 1
SCORE_TEMPLATES = [
    [("平时成绩", 0.4), ("期末成绩", 0.6)],
    [("平时成绩", 0.3), ("实验成绩", 0.2), ("期末成绩", 0.5)],
    [("课堂表现", 0.2), ("课程报告", 0.3), ("期末成绩", 0.5)],
    [("平时成绩", 0.3), ("课程报告", 0.3), ("期末成绩", 0.4)],
    [("考勤", 0.1), ("平时成绩", 0.3), ("期末成绩", 0.6)],
]

# 节次段落，避免出现跨午休的奇怪组合
PERIOD_BLOCKS = [(1, 2), (1, 3), (3, 5), (4, 5), (6, 7), (6, 8), (8, 9), (11, 13)]

DEFAULT_TEACHER_UIDS = [str(uid) for uid in [100001, 100002] + list(range(100003, 100103))]


def semester_weeks(semester):
    """由学期字符串取出该学期的总周数，取不到时按 18 周处理。"""
    try:
        no = int(semester.rsplit("-", 1)[1])
    except (IndexError, ValueError):
        return 18
    return WEEKS_BY_SEMESTER_NO.get(no, 18)


def week_range(rng, total_weeks):
    """随机生成一个合法周次区间，例如 1-8、9-16、1-18。"""
    length = rng.choice([4, 8, 16])
    length = min(length, total_weeks)
    start = rng.randint(1, max(1, total_weeks - length + 1))
    return start, start + length - 1


def overlaps(first, second):
    """判断两个时间段是否在周次、星期、节次上同时重叠。"""
    (s1, e1, day1, p1, q1), (s2, e2, day2, p2, q2) = first, second
    for (u1, v1, u2, v2) in ((s1, e1, s2, e2), (p1, q1, p2, q2)):
        if u1 > v2 or u2 > v1:
            return False
    return day1 == day2


def make_slots(rng, total_weeks, slot_count):
    """生成若干上课时间段，同一课程的各时间段使用相同的周次区间。"""
    start_week, end_week = week_range(rng, total_weeks)
    days = rng.sample(DAYS, slot_count)
    slots = []
    for index, day in enumerate(days):
        start_period, end_period = rng.choice(PERIOD_BLOCKS)
        if index == 0:
            slots.append((start_week, end_week, day, start_period, end_period))
        else:
            # 同一天不排连续冲突的节次：第二次课换一个节次段
            for _ in range(10):
                start_period, end_period = rng.choice(PERIOD_BLOCKS)
                candidate = (start_week, end_week, day, start_period, end_period)
                if not any(overlaps(candidate, slot) for slot in slots):
                    break
            slots.append((start_week, end_week, day, start_period, end_period))
    return slots


def slots_conflict(slots, busy):
    """判断课程时间是否与教师已有安排冲突。"""
    for slot in slots:
        for other in busy:
            if overlaps(slot, other):
                return True
    return False


def sql_escape(value):
    """转义 SQL 字符串中的单引号与反斜杠。"""
    return value.replace("\\", "\\\\").replace("'", "''")


def build_course_rows(rng, semesters, count, teacher_uids):
    """生成课程数据，保证课程代码唯一、同一教师课程时间不冲突。"""
    per_semester = distribute(count, len(semesters))
    names = rng.sample(COURSE_NAMES, count)
    used_codes = set()
    busy_by_teacher = {}
    courses = []
    name_index = 0

    for semester, semester_count in zip(semesters, per_semester):
        total_weeks = semester_weeks(semester)
        semester_no = semester.rsplit("-", 1)[-1]
        for _ in range(semester_count):
            name = names[name_index]
            name_index += 1

            course_code = None
            teacher_uid = None
            slots = None
            for _attempt in range(400):
                candidate_code = "{}{}{}".format(
                    rng.choice(CODE_PREFIXES), semester_no, rng.randint(10, 99))
                if candidate_code in used_codes:
                    continue
                candidate_teacher = rng.choice(teacher_uids)
                busy = busy_by_teacher.setdefault(candidate_teacher, [])
                candidate_slots = make_slots(rng, total_weeks, rng.choice([1, 2, 2]))
                if slots_conflict(candidate_slots, busy):
                    continue
                course_code = candidate_code
                teacher_uid = candidate_teacher
                slots = candidate_slots
                busy.extend(candidate_slots)
                break
            if course_code is None:
                raise RuntimeError("未能为课程 %s 找到不冲突的排课" % name)

            used_codes.add(course_code)
            schedule = ";".join(
                "{}-{}周 {} 第{}-{}节".format(s, e, day, p, q)
                for (s, e, day, p, q) in slots)
            start_week = min(slot[0] for slot in slots)
            end_week = max(slot[1] for slot in slots)
            classroom = "{}{}".format(rng.choice(CLASSROOM_BUILDINGS),
                                      rng.randint(101, 508))
            nature = "必修" if rng.random() < 0.4 else "选修"
            credits = rng.choice(CREDITS)
            capacity = rng.choice(CAPACITIES)
            components = rng.choice(SCORE_TEMPLATES)
            courses.append({
                "course_id": course_code,
                "course_name": name,
                "display_code": course_code,
                "nature": nature,
                "teacher_uid": teacher_uid,
                "credits": credits,
                "semester": semester,
                "capacity": capacity,
                "schedule": schedule,
                "classroom": classroom,
                "start_week": start_week,
                "end_week": end_week,
                "components": components,
            })
    return courses


def distribute(count, buckets):
    """把总数尽量平均分配到各个学期。"""
    base, remainder = divmod(count, buckets)
    return [base + (1 if index < remainder else 0) for index in range(buckets)]


def verify(courses):
    """自检：课程代码与课程名唯一，且同一位教师的时间段互不冲突。"""
    codes = [course["course_id"] for course in courses]
    names = [course["course_name"] for course in courses]
    if len(set(codes)) != len(codes):
        raise SystemExit("存在重复的课程代码")
    if len(set(names)) != len(names):
        raise SystemExit("存在重复的课程名")

    conflicts = 0
    by_teacher = {}
    for course in courses:
        by_teacher.setdefault(course["teacher_uid"], []).append(course)
    for teacher, teacher_courses in by_teacher.items():
        slots = []
        for course in teacher_courses:
            for segment in course["schedule"].split(";"):
                numbers = [int(value) for value in re.findall(r"\d+", segment)]
                day = None
                for candidate in ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]:
                    if candidate in segment:
                        day = candidate
                        break
                current = (numbers[0], numbers[1], day, numbers[2], numbers[3])
                for other in slots:
                    if overlaps(current, other):
                        conflicts += 1
                slots.append(current)
    if conflicts:
        raise SystemExit("教师排课存在 %d 处冲突" % conflicts)
    return len(by_teacher)


def build_sql(courses):
    lines = [
        "-- 批量随机课程（由 scripts/generate_bulk_courses.py 生成，请勿手工修改）",
        "-- 课程数：{}".format(len(courses)),
        "",
        "INSERT IGNORE INTO `tbl_course` (`course_id`, `course_name`, `display_code`,"
        " `course_nature`, `teacher_id`, `teacher_name`, `credits`, `open_semester`,"
        " `status`, `max_capacity`, `current_num`, `time_slot`, `classroom`,"
        " `start_week`, `end_week`) VALUES",
    ]
    for index, course in enumerate(courses):
        suffix = ";" if index == len(courses) - 1 else ","
        lines.append(
            "('{}', '{}', '{}', '{}', '{}', '', {}, '{}', 'ACTIVE', {}, 0, '{}', '{}', {}, {}){}".format(
                course["course_id"], sql_escape(course["course_name"]),
                course["display_code"], course["nature"], course["teacher_uid"],
                course["credits"], course["semester"], course["capacity"],
                sql_escape(course["schedule"]), sql_escape(course["classroom"]),
                course["start_week"], course["end_week"], suffix))
    lines.append("")

    component_rows = []
    for course in courses:
        for name, weight in course["components"]:
            component_rows.append("('{}', '{}', {})".format(
                course["course_id"], sql_escape(name), weight))
    if component_rows:
        lines.append("INSERT IGNORE INTO `tbl_course_score_component`"
                     " (`course_id`, `component_name`, `weight`) VALUES")
        for index, row in enumerate(component_rows):
            suffix = ";" if index == len(component_rows) - 1 else ","
            lines.append(row + suffix)
        lines.append("")

    semesters = sorted({course["semester"] for course in courses})
    lines.append("-- 教师姓名以 tbl_user 为准回填，避免与账号表中的姓名不一致")
    lines.append("UPDATE `tbl_course` c JOIN `tbl_user` u ON u.`uid` = c.`teacher_id`"
                 " SET c.`teacher_name` = u.`name`")
    lines.append("WHERE c.`open_semester` IN ({})".format(
        ", ".join("'{}'".format(semester) for semester in semesters)))
    lines.append("  AND c.`teacher_id` IN ({})".format(
        ", ".join("'{}'".format(uid) for uid in sorted({c["teacher_uid"] for c in courses}))))
    lines.append("  AND c.`status` = 'ACTIVE';")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="生成批量随机课程的 SQL 脚本")
    parser.add_argument("--count", type=int, default=50, help="课程总数")
    parser.add_argument("--semesters", default="2026-2027-2,2026-2027-3",
                        help="目标学期，多个用逗号分隔")
    parser.add_argument("--teachers", default=",".join(DEFAULT_TEACHER_UIDS),
                        help="可分配的教师工号，多个用逗号分隔")
    parser.add_argument("--seed", type=int, default=20260916, help="随机种子，保证可复现")
    parser.add_argument("--out", default="scripts/002_bulk_courses.sql", help="输出的 SQL 文件路径")
    args = parser.parse_args()

    semesters = [item.strip() for item in args.semesters.split(",") if item.strip()]
    teacher_uids = [item.strip() for item in args.teachers.split(",") if item.strip()]
    if args.count > len(COURSE_NAMES):
        raise SystemExit("课程名候选只有 %d 个，无法生成 %d 门课程"
                         % (len(COURSE_NAMES), args.count))

    rng = random.Random(args.seed)
    courses = build_course_rows(rng, semesters, args.count, teacher_uids)
    teacher_count = verify(courses)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(build_sql(courses))

    for semester in semesters:
        count = sum(1 for course in courses if course["semester"] == semester)
        print("{}: {} 门课程".format(semester, count))
    print("排课自检：课程代码/课程名无重复，{} 位教师的课程时间无冲突".format(teacher_count))
    print("已生成 {}：共 {} 门课程".format(args.out, len(courses)))


if __name__ == "__main__":
    main()
