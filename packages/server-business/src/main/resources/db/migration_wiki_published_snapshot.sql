-- wiki 条目「已发布版本」快照字段
--
-- 背景：管理端编辑条目会把 compile_status 重置为 pending 重新编译，
-- 触摸屏公开接口只返回已发布内容，否则会读到半成品。
-- 为了让游客在编译期间（pending / compiling / failed）仍能读到上一版内容，
-- 在编译成功（markDone）时把对外字段整体快照一份。
--
-- 约定：
--   compile_status = 'done'          -> 公开接口返回当前字段（title/file_path/content/tags/links）
--   compile_status != 'done'         -> 公开接口回退到 published_* 快照
--   published_file_path IS NULL      -> 从未成功发布过，公开接口不返回该条目
--
-- 用法：对已存在的库执行本文件；新库直接跑 schema.sql 即可（建表语句已含这些字段）。

ALTER TABLE `wiki_entry`
  ADD COLUMN `published_title`      varchar(100) DEFAULT NULL COMMENT '已发布版本标题快照（上次编译成功时的 title）' AFTER `compile_status`,
  ADD COLUMN `published_file_path`  varchar(255) DEFAULT NULL COMMENT '已发布版本文件路径快照（上次编译成功时的 file_path）' AFTER `published_title`,
  ADD COLUMN `published_content`    longtext COMMENT '已发布版本正文快照（上次编译成功时的 content）' AFTER `published_file_path`,
  ADD COLUMN `published_tags`       json DEFAULT NULL COMMENT '已发布版本标签快照（上次编译成功时的 tags）' AFTER `published_content`,
  ADD COLUMN `published_links`      json DEFAULT NULL COMMENT '已发布版本引用链接快照（上次编译成功时的 links）' AFTER `published_tags`;

-- 回填：把建表前就已处于 done 的条目当作已发布版本，
-- 否则这些条目一旦被编辑（状态回到 pending）就会因为没有快照而从触摸屏消失。
UPDATE `wiki_entry`
SET `published_title`     = `title`,
    `published_file_path` = `file_path`,
    `published_content`   = `content`,
    `published_tags`      = `tags`,
    `published_links`     = `links`
WHERE `compile_status` = 'done';
