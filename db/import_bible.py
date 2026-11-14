# -*- coding: utf-8 -*-
"""
精读圣经 · 数据导入脚本

把「和合本.db」(SQLite) 中的经文、分段标题导入 MySQL：
  * book          书卷（含中英文名、简称、别名、章/节统计）
  * chapter       章（节数、标题数）
  * verse         经文
  * section_title 分段标题（含诗篇卷标）

用法：
  python db/import_bible.py
  python db/import_bible.py --port 3306 --password 1106135 --sqlite "D:\\...\\和合本.db"
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import sys
import time

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
from pathlib import Path

import pymysql

BASE_DIR = Path(__file__).resolve().parent
SCHEMA_FILE = BASE_DIR / "schema.sql"
DEFAULT_SQLITE = Path(r"D:\file\study\圣经\精读圣经\bibles\和合本.db")

# id, 中文名, 简称, 英文名, 英文缩写, 额外别名, 约, 分组
BOOKS = [
    (1, "创世记", "创", "Genesis", "Gen", ["Ge", "Gn"], "OT", "律法书"),
    (2, "出埃及记", "出", "Exodus", "Exod", ["Ex", "Exo"], "OT", "律法书"),
    (3, "利未记", "利", "Leviticus", "Lev", ["Le", "Lv"], "OT", "律法书"),
    (4, "民数记", "民", "Numbers", "Num", ["Nu", "Nm", "Nb"], "OT", "律法书"),
    (5, "申命记", "申", "Deuteronomy", "Deut", ["De", "Dt"], "OT", "律法书"),
    (6, "约书亚记", "书", "Joshua", "Josh", ["Jos"], "OT", "历史书"),
    (7, "士师记", "士", "Judges", "Judg", ["Jdg", "Jg"], "OT", "历史书"),
    (8, "路得记", "得", "Ruth", "Ruth", ["Rut", "Rth"], "OT", "历史书"),
    (9, "撒母耳记上", "撒上", "1 Samuel", "1Sam", ["1Sa", "1Samuel", "I Samuel"], "OT", "历史书"),
    (10, "撒母耳记下", "撒下", "2 Samuel", "2Sam", ["2Sa", "2Samuel", "II Samuel"], "OT", "历史书"),
    (11, "列王纪上", "王上", "1 Kings", "1Kgs", ["1Ki", "1Kings", "I Kings"], "OT", "历史书"),
    (12, "列王纪下", "王下", "2 Kings", "2Kgs", ["2Ki", "2Kings", "II Kings"], "OT", "历史书"),
    (13, "历代志上", "代上", "1 Chronicles", "1Chr", ["1Ch", "1Chronicles", "I Chronicles"], "OT", "历史书"),
    (14, "历代志下", "代下", "2 Chronicles", "2Chr", ["2Ch", "2Chronicles", "II Chronicles"], "OT", "历史书"),
    (15, "以斯拉记", "拉", "Ezra", "Ezr", ["Ezra"], "OT", "历史书"),
    (16, "尼希米记", "尼", "Nehemiah", "Neh", ["Ne"], "OT", "历史书"),
    (17, "以斯帖记", "斯", "Esther", "Esth", ["Est", "Es"], "OT", "历史书"),
    (18, "约伯记", "伯", "Job", "Job", ["Jb"], "OT", "诗歌智慧书"),
    (19, "诗篇", "诗", "Psalms", "Ps", ["Psalm", "Psa", "Psm"], "OT", "诗歌智慧书"),
    (20, "箴言", "箴", "Proverbs", "Prov", ["Pro", "Pr", "Prv"], "OT", "诗歌智慧书"),
    (21, "传道书", "传", "Ecclesiastes", "Eccl", ["Ecc", "Ec", "Qoheleth"], "OT", "诗歌智慧书"),
    (22, "雅歌", "歌", "Song of Songs", "Song", ["Sng", "Canticles", "Song of Solomon"], "OT", "诗歌智慧书"),
    (23, "以赛亚书", "赛", "Isaiah", "Isa", ["Is"], "OT", "大先知书"),
    (24, "耶利米书", "耶", "Jeremiah", "Jer", ["Je", "Jr"], "OT", "大先知书"),
    (25, "耶利米哀歌", "哀", "Lamentations", "Lam", ["La"], "OT", "大先知书"),
    (26, "以西结书", "结", "Ezekiel", "Ezek", ["Eze", "Ezk"], "OT", "大先知书"),
    (27, "但以理书", "但", "Daniel", "Dan", ["Da", "Dn"], "OT", "大先知书"),
    (28, "何西阿书", "何", "Hosea", "Hos", ["Ho"], "OT", "小先知书"),
    (29, "约珥书", "珥", "Joel", "Joel", ["Joe", "Jl"], "OT", "小先知书"),
    (30, "阿摩司书", "摩", "Amos", "Amos", ["Am"], "OT", "小先知书"),
    (31, "俄巴底亚书", "俄", "Obadiah", "Obad", ["Oba", "Ob"], "OT", "小先知书"),
    (32, "约拿书", "拿", "Jonah", "Jonah", ["Jon", "Jnh"], "OT", "小先知书"),
    (33, "弥迦书", "弥", "Micah", "Mic", ["Mc"], "OT", "小先知书"),
    (34, "那鸿书", "鸿", "Nahum", "Nah", ["Na"], "OT", "小先知书"),
    (35, "哈巴谷书", "哈", "Habakkuk", "Hab", ["Hb"], "OT", "小先知书"),
    (36, "西番雅书", "番", "Zephaniah", "Zeph", ["Zep", "Zp"], "OT", "小先知书"),
    (37, "哈该书", "该", "Haggai", "Hag", ["Hg"], "OT", "小先知书"),
    (38, "撒迦利亚书", "亚", "Zechariah", "Zech", ["Zec", "Zc"], "OT", "小先知书"),
    (39, "玛拉基书", "玛", "Malachi", "Mal", ["Ml"], "OT", "小先知书"),
    (40, "马太福音", "太", "Matthew", "Matt", ["Mat", "Mt"], "NT", "福音书"),
    (41, "马可福音", "可", "Mark", "Mark", ["Mrk", "Mk", "Mr"], "NT", "福音书"),
    (42, "路加福音", "路", "Luke", "Luke", ["Luk", "Lk"], "NT", "福音书"),
    (43, "约翰福音", "约", "John", "John", ["Jn", "Joh"], "NT", "福音书"),
    (44, "使徒行传", "徒", "Acts", "Acts", ["Act", "Ac"], "NT", "教会历史"),
    (45, "罗马书", "罗", "Romans", "Rom", ["Ro", "Rm"], "NT", "保罗书信"),
    (46, "哥林多前书", "林前", "1 Corinthians", "1Cor", ["1Co", "1Corinthians", "I Corinthians"], "NT", "保罗书信"),
    (47, "哥林多后书", "林后", "2 Corinthians", "2Cor", ["2Co", "2Corinthians", "II Corinthians"], "NT", "保罗书信"),
    (48, "加拉太书", "加", "Galatians", "Gal", ["Ga"], "NT", "保罗书信"),
    (49, "以弗所书", "弗", "Ephesians", "Eph", ["Ep"], "NT", "保罗书信"),
    (50, "腓立比书", "腓", "Philippians", "Phil", ["Php", "Pp"], "NT", "保罗书信"),
    (51, "歌罗西书", "西", "Colossians", "Col", ["Co"], "NT", "保罗书信"),
    (52, "帖撒罗尼迦前书", "帖前", "1 Thessalonians", "1Thess", ["1Th", "1Thessalonians", "I Thessalonians"], "NT", "保罗书信"),
    (53, "帖撒罗尼迦后书", "帖后", "2 Thessalonians", "2Thess", ["2Th", "2Thessalonians", "II Thessalonians"], "NT", "保罗书信"),
    (54, "提摩太前书", "提前", "1 Timothy", "1Tim", ["1Ti", "1Timothy", "I Timothy"], "NT", "保罗书信"),
    (55, "提摩太后书", "提后", "2 Timothy", "2Tim", ["2Ti", "2Timothy", "II Timothy"], "NT", "保罗书信"),
    (56, "提多书", "多", "Titus", "Titus", ["Tit"], "NT", "保罗书信"),
    (57, "腓利门书", "门", "Philemon", "Phlm", ["Phm", "Pm"], "NT", "保罗书信"),
    (58, "希伯来书", "来", "Hebrews", "Heb", ["He"], "NT", "一般书信"),
    (59, "雅各书", "雅", "James", "Jas", ["Jam", "Jm"], "NT", "一般书信"),
    (60, "彼得前书", "彼前", "1 Peter", "1Pet", ["1Pe", "1Peter", "I Peter"], "NT", "一般书信"),
    (61, "彼得后书", "彼后", "2 Peter", "2Pet", ["2Pe", "2Peter", "II Peter"], "NT", "一般书信"),
    (62, "约翰一书", "约一", "1 John", "1John", ["1Jn", "I John"], "NT", "一般书信"),
    (63, "约翰二书", "约二", "2 John", "2John", ["2Jn", "II John"], "NT", "一般书信"),
    (64, "约翰三书", "约三", "3 John", "3John", ["3Jn", "III John"], "NT", "一般书信"),
    (65, "犹大书", "犹", "Jude", "Jude", ["Jud"], "NT", "一般书信"),
    (66, "启示录", "启", "Revelation", "Rev", ["Re", "Apocalypse", "Rv"], "NT", "启示录"),
]

VOLUME_RE = re.compile(r"^诗篇卷[一二三四五六七八九十]+$")


def clean_text(value: str) -> str:
    if value is None:
        return ""
    return value.replace("\r\n", "\n").replace("\r", "\n").strip()


def build_aliases(name, short, english, abbr, extra):
    seen, result = set(), []

    def add(value):
        value = (value or "").strip()
        key = re.sub(r"[\s.]+", "", value).lower()
        if value and key not in seen:
            seen.add(key)
            result.append(value)

    for value in [name, short, english, abbr, *extra]:
        add(value)
    return result


def read_schema(conn):
    sql = SCHEMA_FILE.read_text(encoding="utf-8")
    sql = "\n".join(line for line in sql.splitlines() if not line.strip().startswith("--"))
    statements = [s.strip() for s in sql.split(";") if s.strip()]
    with conn.cursor() as cur:
        for statement in statements:
            cur.execute(statement)
    conn.commit()


def main() -> int:
    parser = argparse.ArgumentParser(description="导入和合本圣经到 MySQL")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="1106135")
    parser.add_argument("--database", default="bible")
    parser.add_argument("--sqlite", default=str(DEFAULT_SQLITE))
    args = parser.parse_args()

    sqlite_path = Path(args.sqlite)
    if not sqlite_path.exists():
        print(f"[ERROR] 找不到 SQLite 文件: {sqlite_path}", file=sys.stderr)
        return 1

    print(f"[1/4] 读取 {sqlite_path.name} ...")
    lite = sqlite3.connect(str(sqlite_path))
    lite.text_factory = str
    lcur = lite.cursor()
    lcur.execute("SELECT Book, Chapter, Verse, Scripture FROM Bible ORDER BY Book, Chapter, Verse")
    rows = lcur.fetchall()
    lcur.execute("SELECT Book, Chapter, Verse, Scripture FROM Titles ORDER BY Book, Chapter, Verse, id")
    title_rows = lcur.fetchall()
    lite.close()
    print(f"      经文 {len(rows)} 节，标题 {len(title_rows)} 条")

    # 统计章/节
    chapters: dict[tuple[int, int], int] = {}
    book_verses: dict[int, int] = {}
    for book, chapter, _verse, _text in rows:
        chapters[(book, chapter)] = chapters.get((book, chapter), 0) + 1
        book_verses[book] = book_verses.get(book, 0) + 1

    chapter_books = {book for book, _chapter in chapters}
    if chapter_books != set(range(1, 67)):
        print(f"[ERROR] 书卷不完整: {sorted(chapter_books)}", file=sys.stderr)
        return 1
    if len(chapters) != 1189:
        print(f"[WARN] 章数为 {len(chapters)}，预期 1189")

    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"CREATE DATABASE IF NOT EXISTS `{args.database}` "
                "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
            )
            cur.execute(f"USE `{args.database}`")
        print("[2/4] 重建表结构 ...")
        read_schema(conn)

        print("[3/4] 写入书卷 / 章 / 经文 ...")
        with conn.cursor() as cur:
            book_values = []
            for book_id, name, short, english, abbr, extra, testament, category in BOOKS:
                book_values.append(
                    (
                        book_id,
                        name,
                        short,
                        english,
                        abbr,
                        json.dumps(build_aliases(name, short, english, abbr, extra), ensure_ascii=False),
                        testament,
                        category,
                        max(ch for (b, ch) in chapters if b == book_id),
                        book_verses.get(book_id, 0),
                        book_id,
                    )
                )
            cur.executemany(
                "INSERT INTO book (id, name, short_name, english_name, english_abbr, aliases, "
                "testament, category, chapter_count, verse_count, sort_order) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                book_values,
            )

            chapter_values = [(b, c, n, 0) for (b, c), n in sorted(chapters.items())]
            cur.executemany(
                "INSERT INTO chapter (book_id, chapter, verse_count, title_count) VALUES (%s,%s,%s,%s)",
                chapter_values,
            )

            verse_values = []
            for book, chapter, verse, text in rows:
                verse_values.append((book, chapter, verse, clean_text(text)))
            cur.executemany(
                "INSERT INTO verse (book_id, chapter, verse, content) VALUES (%s,%s,%s,%s)",
                verse_values,
            )

            title_values = []
            chapter_title_count: dict[tuple[int, int], int] = {}
            for book, chapter, verse, text in title_rows:
                for line in clean_text(text).split("\n"):
                    line = line.strip()
                    if not line:
                        continue
                    kind = "volume" if VOLUME_RE.match(line) else "section"
                    title_values.append((book, chapter, verse, kind, line[:255]))
                    chapter_title_count[(book, chapter)] = chapter_title_count.get((book, chapter), 0) + 1
            cur.executemany(
                "INSERT INTO section_title (book_id, chapter, verse, kind, content) VALUES (%s,%s,%s,%s,%s)",
                title_values,
            )
            for (book, chapter), count in chapter_title_count.items():
                cur.execute(
                    "UPDATE chapter SET title_count=%s WHERE book_id=%s AND chapter=%s",
                    (count, book, chapter),
                )
        conn.commit()

        print("[4/4] 校验 ...")
        with conn.cursor() as cur:
            for table in ("book", "chapter", "verse", "section_title"):
                cur.execute(f"SELECT COUNT(*) FROM {table}")
                print(f"      {table:<14} {cur.fetchone()[0]:>6} 行")
            cur.execute("SELECT COUNT(DISTINCT book_id, chapter) FROM chapter")
            print(f"      章总数         {cur.fetchone()[0]:>6}")
            cur.execute("SELECT COUNT(*) FROM verse WHERE content=''")
            print(f"      空经文         {cur.fetchone()[0]:>6}")
    finally:
        conn.close()

    print("完成 ✅")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

