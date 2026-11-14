-- ============================================================
-- 精读圣经 · 数据库结构 (MySQL 8.0+)
-- 字符集：utf8mb4，排序规则：utf8mb4_0900_ai_ci
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS section_title;
DROP TABLE IF EXISTS verse;
DROP TABLE IF EXISTS chapter;
DROP TABLE IF EXISTS book;

-- 书卷
CREATE TABLE book (
    id             TINYINT UNSIGNED  NOT NULL COMMENT '书卷序号 1-66',
    name           VARCHAR(32)       NOT NULL COMMENT '中文全名',
    short_name     VARCHAR(16)       NOT NULL COMMENT '中文简称',
    english_name   VARCHAR(64)       NOT NULL COMMENT '英文名',
    english_abbr   VARCHAR(24)       NOT NULL COMMENT '英文缩写',
    aliases        TEXT              NOT NULL COMMENT '检索别名 JSON 数组',
    testament      ENUM('OT','NT')   NOT NULL COMMENT '旧约/新约',
    category       VARCHAR(24)       NOT NULL COMMENT '书卷分组',
    chapter_count  SMALLINT UNSIGNED NOT NULL COMMENT '章数',
    verse_count    SMALLINT UNSIGNED NOT NULL COMMENT '节数',
    sort_order     SMALLINT UNSIGNED NOT NULL COMMENT '排序',
    PRIMARY KEY (id),
    UNIQUE KEY uk_book_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '书卷';

-- 章
CREATE TABLE chapter (
    book_id     TINYINT UNSIGNED  NOT NULL,
    chapter     SMALLINT UNSIGNED NOT NULL,
    verse_count SMALLINT UNSIGNED NOT NULL,
    title_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (book_id, chapter),
    CONSTRAINT fk_chapter_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '章';

-- 经文
CREATE TABLE verse (
    id       INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    book_id  TINYINT UNSIGNED  NOT NULL,
    chapter  SMALLINT UNSIGNED NOT NULL,
    verse    SMALLINT UNSIGNED NOT NULL,
    content  MEDIUMTEXT        NOT NULL COMMENT '经文正文（和合本）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_verse (book_id, chapter, verse),
    KEY idx_chapter (book_id, chapter, verse),
    FULLTEXT KEY ft_content (content) WITH PARSER ngram,
    CONSTRAINT fk_verse_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '经文';

-- 分段标题 / 诗篇卷标
CREATE TABLE section_title (
    id       INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    book_id  TINYINT UNSIGNED  NOT NULL,
    chapter  SMALLINT UNSIGNED NOT NULL,
    verse    SMALLINT UNSIGNED NOT NULL COMMENT '标题出现在该节之前',
    kind     VARCHAR(16)       NOT NULL DEFAULT 'section' COMMENT 'section/volume',
    content  VARCHAR(255)      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_title_chapter (book_id, chapter, verse),
    CONSTRAINT fk_title_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '分段标题';
