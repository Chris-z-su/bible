# 圣经数据库分析报告

> 分析对象：`D:\file\study\圣经\精读圣经\bibles` 下的两个 SQLite 数据库。
> 结论：选用 `和合本.db` 作为主数据源，导入 MySQL 后共 **66 卷 / 1,189 章 / 31,102 节 / 2,667 条分段标题**。

## 1. 文件概览

| 文件 | 大小 | 表 | 说明 |
| --- | --- | --- | --- |
| `和合本.db` | 14.65 MB | `Bible`、`Chapters`、`Titles`、`metadata` | 简体中文和合本，正文干净，含 2,651 条分段标题 |
| `信望爱(和合本).db` | 10.65 MB | `Bible`、`Chapters`、`Paragraph`、`Details`、`metadata` | 信望爱版和合本，正文带 Strong 原文编号标记，`Chapters`/`Paragraph` 为空 |

两个库的 `Bible` 表结构一致：

```sql
CREATE TABLE Bible (
  id INTEGER PRIMARY KEY,
  Book INT,       -- 书卷序号 1-66
  Chapter INT,    -- 章
  Verse INT,      -- 节
  Scripture TEXT  -- 经文
);
CREATE INDEX bible_lookup_index ON Bible (book, chapter, verse);
```

## 2. 数据完整性

以 `和合本.db` 为基准：

- 书卷范围：`1..66`，共 **66** 卷，无缺卷。
- 章节：`COUNT(DISTINCT Book, Chapter) = 1189`，与正典章数完全一致。
- 经文：**31,102** 节；按 `(Book, Chapter, Verse)` 排序后逐章逐节连续，**无缺节、无重复**。
- 各卷章数：创世记 50、出埃及记 40……诗篇 150、启示录 22，与通用正典一致。
- 文本长度：最短 1 字符，最长 110 字符，平均约 34.5 字符。
- 文本中**不含** HTML/Strong 标记（`<`、`{` 出现次数为 0）；275 节以全角空格 `\u3000` 开头（排版缩进），导入时去除首尾空白。
- 诗篇 117 章 2 节、约翰福音 3:16 等抽查内容正确。

`信望爱(和合本).db`：

- 31,103 节，仅启示录多 1 节（405 vs 404），差异来自该版本自身的原文编号/校订数据。
- 每节带有 `<WH0430>`、`<WTH8804>` 等 Strong 标记，适合原文研究，不适合作为默认阅读文本。
- `Details` 表记录版本：`信望爱和合本 2.0`，2016-02-25。

**选择**：`和合本.db` 作为阅读与搜索正文；`信望爱` 仅作对照，不导入，避免标记干扰阅读与复制。

## 3. 分段标题（Titles）

```sql
CREATE TABLE Titles (
  id INTEGER PRIMARY KEY,
  Book INT, Chapter INT, Verse INT,
  Scripture TEXT   -- 标题文本，可能含换行
);
```

- 共 **2,651** 行，覆盖 66 卷。
- 有 5 个位置存在同一节多条标题（雅歌中的 speaker 标记、撒母耳记上的并行标题等）。
- 部分标题含换行，例如 `诗篇卷一\r\n弃恶从善必蒙福`、`第四首\n〔新娘〕`；导入时按行拆分。
- 5 条诗篇卷标识别为 `kind=volume`，其余为 `kind=section`。
- 拆分清洗后入库 **2,667** 条。

## 4. 其他表

- `Chapters`：1,189 行，`Content` 是 Base64 形态的加密/编码内容（约 3–5 KB/章），无法直接还原为明文；本项目不使用。
- `Paragraph`（信望爱）：0 行。
- `metadata`：版本名称、语言、日期等；`和合本.db` 的 `name=和合本`、`langcode=zh-CN`。
- `Details`（信望爱）：版本、版权、方向等元数据。

## 5. 导入 MySQL 的映射

| SQLite | MySQL | 说明 |
| --- | --- | --- |
| `Bible.Book` | `book.id` | 1–66，另建书卷名称/别名/分组 |
| `Bible.Chapter` | `chapter.chapter` | 每章统计节数、标题数 |
| `Bible.Verse` | `verse.verse` | 与 `(book_id, chapter)` 组成唯一键 |
| `Bible.Scripture` | `verse.content` | 去除首尾空白 |
| `Titles` | `section_title` | 按行拆分，标记 `section` / `volume` |

MySQL 表结构见 [`db/schema.sql`](../db/schema.sql)，导入脚本见 [`db/import_bible.py`](../db/import_bible.py)。

## 6. 复现导入

```powershell
# 默认导入系统 MySQL 3306（root/1106135）；如使用 3307 回退实例请加 --port 3307 --password 11016135
& 'D:\dev\Python\Python313\python.exe' 'D:\file\aidemo\bible\db\import_bible.py' --port 3306 --password 1106135
```

脚本会重建 `book/chapter/verse/section_title` 四张表并校验行数，输出：

```
book               66
chapter          1189
verse           31102
section_title    2667
```

## 7. 校验 SQL

```sql
USE bible;
SELECT COUNT(*) AS books FROM book;                       -- 66
SELECT COUNT(*) AS chapters FROM chapter;                 -- 1189
SELECT COUNT(*) AS verses FROM verse;                     -- 31102
SELECT COUNT(*) AS titles FROM section_title;             -- 2667
SELECT book_id, chapter, verse, content FROM verse
 WHERE book_id = 43 AND chapter = 3 AND verse = 16;       -- 约翰福音 3:16
SELECT book_id, chapter, COUNT(*) FROM verse
 GROUP BY book_id, chapter ORDER BY book_id, chapter;     -- 每章节数
```

## 8. 文本特点与前端展示

- 中文全角标点、引号原样保留；部分经文以 `“　`（引号 + 全角空格）开头，属于和合本排版习惯。
- 诗篇、箴言等诗歌体在原文中有全角空格缩进；导入时仅去掉首尾空白，不破坏句内空格。
- 前端以衬线字体、1.9–2.2 行距展示，支持“逐节”和“段落连读”两种模式。
- 全文搜索使用 MySQL `LIKE` 子串匹配（31k 行数据量下响应为毫秒级）；`verse.content` 另建 `ngram` 全文索引，便于后续扩展相关度排序。
