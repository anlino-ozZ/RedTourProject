-- ============================================================
-- 红色文旅智能导览系统 · 数据库建表脚本
-- MySQL 8.0+ · utf8mb4 · InnoDB
-- 对应文档: docs/04_精简PRD开发要点.md 第六章 + docs/05_全局接口精简文档.md
-- ============================================================

CREATE DATABASE IF NOT EXISTS `red_tour`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `red_tour`;

-- ============================================================
-- 一、用户与权限
-- ============================================================

-- 1. 用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`       VARCHAR(50)  NOT NULL COMMENT '用户名（字母数字下划线 3-20字符）',
  `password`       VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密密码',
  `nickname`       VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `role`           VARCHAR(20)  NOT NULL DEFAULT 'tourist' COMMENT '角色: super_admin / admin / tourist',
  `scenic_area_id` BIGINT       DEFAULT NULL COMMENT '管辖景区 ID（普管必填）',
  `avatar`         VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0 禁用 / 1 启用',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除: 0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_scenic_area` (`scenic_area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 二、景区与景点
-- ============================================================

-- 2. 景区表
DROP TABLE IF EXISTS `scenic_area`;
CREATE TABLE `scenic_area` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50)   NOT NULL COMMENT '景区名称',
  `intro`       TEXT          DEFAULT NULL COMMENT '景区简介',
  `longitude`   DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
  `latitude`    DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
  `address`     VARCHAR(200)  DEFAULT NULL COMMENT '详细地址',
  `open_hours`  VARCHAR(50)   DEFAULT NULL COMMENT '开放时间，如 08:30-17:00',
  `cover_image` VARCHAR(255)  DEFAULT NULL COMMENT '封面图 URL',
  `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态: 0 下线 / 1 上线',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区表';

-- 3. 景点表
DROP TABLE IF EXISTS `spot`;
CREATE TABLE `spot` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT        NOT NULL COMMENT '归属景区 ID',
  `name`           VARCHAR(50)   NOT NULL COMMENT '景点名称',
  `intro`          TEXT          NOT NULL COMMENT '景点简介',
  `images`         JSON          DEFAULT NULL COMMENT '图片 URL 数组',
  `longitude`      DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
  `latitude`       DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
  `audio_url`      VARCHAR(255)  DEFAULT NULL COMMENT '讲解音频 URL',
  `wiki_ref`       VARCHAR(255)  DEFAULT NULL COMMENT '关联 Wiki 条目路径',
  `sort_order`     INT           NOT NULL DEFAULT 0 COMMENT '排序权重（越小越靠前）',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`),
  KEY `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景点表';

-- 4. 导览路线表
DROP TABLE IF EXISTS `guide_route`;
CREATE TABLE `guide_route` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT       NOT NULL COMMENT '归属景区 ID',
  `name`           VARCHAR(50)  NOT NULL COMMENT '路线名称',
  `description`    VARCHAR(200) DEFAULT NULL COMMENT '路线描述',
  `spot_ids`       JSON         NOT NULL COMMENT '景点 ID 有序数组，如 [1,3,5,7,9]',
  `duration`       INT          NOT NULL COMMENT '预计时长（分钟）',
  `difficulty`     VARCHAR(10)  NOT NULL DEFAULT 'easy' COMMENT '难度: easy / medium / hard',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0 下线 / 1 上线',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导览路线表';

-- 5. 打卡点表
DROP TABLE IF EXISTS `checkpoint`;
CREATE TABLE `checkpoint` (
  `id`        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `spot_id`   BIGINT        NOT NULL COMMENT '关联景点 ID',
  `longitude` DECIMAL(10,7) NOT NULL COMMENT '打卡点经度',
  `latitude`  DECIMAL(10,7) NOT NULL COMMENT '打卡点纬度',
  `radius`    INT           NOT NULL DEFAULT 50 COMMENT '打卡有效半径（米）',
  PRIMARY KEY (`id`),
  KEY `idx_spot` (`spot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打卡点表';

-- ============================================================
-- 三、知识库与问答
-- ============================================================

-- 6. Wiki 条目表
DROP TABLE IF EXISTS `wiki_entry`;
CREATE TABLE `wiki_entry` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT       NOT NULL COMMENT '归属景区 ID',
  `title`          VARCHAR(100) NOT NULL COMMENT '条目标题',
  `file_path`      VARCHAR(255) NOT NULL COMMENT 'Markdown 文件路径',
  `content`        LONGTEXT     DEFAULT NULL COMMENT '条目正文（Markdown）',
  `tags`           JSON         DEFAULT NULL COMMENT '分类标签数组',
  `links`          JSON         DEFAULT NULL COMMENT '引用的 Wiki 条目路径数组',
  `compile_status` VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '编译状态: pending / compiling / done / failed',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后编译时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`),
  KEY `idx_compile_status` (`compile_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 条目表';

-- 7. 问答记录表
DROP TABLE IF EXISTS `ask_log`;
CREATE TABLE `ask_log` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT      NOT NULL COMMENT '景区 ID',
  `user_id`        BIGINT      DEFAULT NULL COMMENT '游客用户 ID（触摸屏端为 NULL）',
  `question`       TEXT        NOT NULL COMMENT '游客问题',
  `answer`         TEXT        DEFAULT NULL COMMENT 'AI 回答',
  `sources`        JSON        DEFAULT NULL COMMENT '引用的 Wiki 条目路径',
  `duration_ms`    INT         DEFAULT NULL COMMENT '推理耗时（毫秒）',
  `device_id`      VARCHAR(50) DEFAULT NULL COMMENT '来源设备标识',
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '问答时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答记录表';

-- 8. 推荐问题表
DROP TABLE IF EXISTS `recommendation_question`;
CREATE TABLE `recommendation_question` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT       NOT NULL COMMENT '所属景区 ID',
  `question`       VARCHAR(200) NOT NULL COMMENT '推荐问题文本',
  `sort_weight`    INT          NOT NULL DEFAULT 0 COMMENT '排序权重（越大越靠前）',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐问题表';

-- ============================================================
-- 四、特产与文创
-- ============================================================

-- 9. 特产/文创产品表
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT        NOT NULL COMMENT '归属景区 ID',
  `name`           VARCHAR(100)  NOT NULL COMMENT '产品名称',
  `price`          DECIMAL(10,2) NOT NULL COMMENT '单价（元）',
  `description`    TEXT          DEFAULT NULL COMMENT '产品描述',
  `image`          VARCHAR(255)  DEFAULT NULL COMMENT '产品图片 URL',
  `stock`          INT           NOT NULL DEFAULT 0 COMMENT '库存数量',
  `category`       VARCHAR(20)   NOT NULL DEFAULT 'specialty' COMMENT '分类: specialty 特产 / cultural 文创',
  `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态: 0 下架 / 1 上架',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='特产/文创产品表';

-- 10. 特产意向订单表
DROP TABLE IF EXISTS `product_order`;
CREATE TABLE `product_order` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` BIGINT       NOT NULL COMMENT '产品 ID',
  `user_id`    BIGINT       DEFAULT NULL COMMENT '游客用户 ID',
  `phone`      VARCHAR(20)  NOT NULL COMMENT '联系电话',
  `quantity`   INT          NOT NULL DEFAULT 1 COMMENT '数量',
  `remark`     VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态: pending 待处理 / contacted 已联系 / closed 已关闭',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='特产意向订单表';

-- ============================================================
-- 五、文物
-- ============================================================

-- 11. 文物表
DROP TABLE IF EXISTS `artifact`;
CREATE TABLE `artifact` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT       NOT NULL COMMENT '归属景区 ID',
  `name`           VARCHAR(100) NOT NULL COMMENT '文物名称',
  `era`            VARCHAR(50)  DEFAULT NULL COMMENT '年代，如 1935年',
  `intro`          TEXT         NOT NULL COMMENT '文物简介',
  `images`         JSON         DEFAULT NULL COMMENT '图片 URL 数组',
  `location`       VARCHAR(100) DEFAULT NULL COMMENT '出土/收藏地点',
  `category`       VARCHAR(50)  DEFAULT NULL COMMENT '品类，如 武器 / 文献',
  `wiki_ref`       VARCHAR(255) DEFAULT NULL COMMENT '关联 Wiki 条目路径',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0 下架 / 1 上架',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文物表';

-- ============================================================
-- 六、剧本与成就
-- ============================================================

-- 12. 剧本表
DROP TABLE IF EXISTS `script`;
CREATE TABLE `script` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `scenic_area_id` BIGINT       NOT NULL COMMENT '归属景区 ID',
  `title`          VARCHAR(100) NOT NULL COMMENT '剧本名称',
  `description`    TEXT         DEFAULT NULL COMMENT '剧本简介',
  `cover_image`    VARCHAR(255) DEFAULT NULL COMMENT '封面图 URL',
  `roles`          JSON         DEFAULT NULL COMMENT '角色列表 [{id,name,avatar,skill}]',
  `nodes`          JSON         DEFAULT NULL COMMENT '剧情节点树 [{id,sceneText,options:[{text,nextNodeId}]}]',
  `start_node_id`  INT          DEFAULT 1 COMMENT '起始节点 ID',
  `achievements`   JSON         DEFAULT NULL COMMENT '可解锁成就 ID 列表',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0 下线 / 1 上线',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scenic_area` (`scenic_area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本表';

-- 13. 成就表
DROP TABLE IF EXISTS `achievement`;
CREATE TABLE `achievement` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title`             VARCHAR(50)  NOT NULL COMMENT '成就标题',
  `icon`              VARCHAR(255) DEFAULT NULL COMMENT '图标 URL',
  `description`       VARCHAR(200) DEFAULT NULL COMMENT '成就描述',
  `trigger_type`      VARCHAR(20)  NOT NULL COMMENT '触发类型: visit / pose / script / checkin',
  `trigger_condition` JSON         DEFAULT NULL COMMENT '触发条件配置',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_trigger_type` (`trigger_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成就表';

-- 14. 用户成就关联表
DROP TABLE IF EXISTS `user_achievement`;
CREATE TABLE `user_achievement` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT   NOT NULL COMMENT '用户 ID',
  `achievement_id` BIGINT  NOT NULL COMMENT '成就 ID',
  `unlocked_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '解锁时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_achievement` (`user_id`, `achievement_id`),
  KEY `idx_achievement` (`achievement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户成就关联表';

-- 15. 剧本进度表
DROP TABLE IF EXISTS `script_progress`;
CREATE TABLE `script_progress` (
  `id`              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT   NOT NULL COMMENT '用户 ID',
  `script_id`       BIGINT   NOT NULL COMMENT '剧本 ID',
  `current_node_id` INT      NOT NULL COMMENT '当前节点 ID',
  `completed`       TINYINT  NOT NULL DEFAULT 0 COMMENT '是否完成: 0 进行中 / 1 已完成',
  `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_script` (`user_id`, `script_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='剧本进度表';

-- ============================================================
-- 七、游客交互
-- ============================================================

-- 16. 景点收藏表
DROP TABLE IF EXISTS `spot_favorite`;
CREATE TABLE `spot_favorite` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT   NOT NULL COMMENT '用户 ID',
  `spot_id`    BIGINT   NOT NULL COMMENT '景点 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_spot` (`user_id`, `spot_id`),
  KEY `idx_spot` (`spot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景点收藏表';

-- 17. 游览记录表
DROP TABLE IF EXISTS `visit_record`;
CREATE TABLE `visit_record` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT      NOT NULL COMMENT '用户 ID',
  `scenic_area_id`  BIGINT      NOT NULL COMMENT '景区 ID',
  `route_id`        BIGINT      DEFAULT NULL COMMENT '路线 ID',
  `spot_count`      INT         NOT NULL DEFAULT 0 COMMENT '打卡景点数',
  `duration`        INT         NOT NULL DEFAULT 0 COMMENT '游览时长（分钟）',
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '游览时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_scenic_area` (`scenic_area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游览记录表';

-- ============================================================
-- 八、设备管理
-- ============================================================

-- 18. 设备状态表
DROP TABLE IF EXISTS `device_status`;
CREATE TABLE `device_status` (
  `device_id`         VARCHAR(50)   NOT NULL COMMENT '设备标识（主键）',
  `scenic_area_id`    BIGINT        NOT NULL COMMENT '绑定景区 ID',
  `online`            TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '在线状态: 0 离线 / 1 在线',
  `cpu_temp`          DECIMAL(4,1)  DEFAULT NULL COMMENT 'CPU 温度（℃）',
  `memory_usage`      DECIMAL(5,2)  DEFAULT NULL COMMENT '内存占用率（%）',
  `disk_usage`        DECIMAL(5,2)  DEFAULT NULL COMMENT '磁盘占用率（%）',
  `hailo_status`      VARCHAR(20)   DEFAULT NULL COMMENT 'Hailo8L 状态: active / inactive / error',
  `camera_connected`  TINYINT(1)    DEFAULT 0 COMMENT '摄像头连接: 0 否 / 1 是',
  `speaker_connected` TINYINT(1)    DEFAULT 0 COMMENT '音箱连接: 0 否 / 1 是',
  `last_heartbeat`    DATETIME      DEFAULT NULL COMMENT '最后心跳时间',
  PRIMARY KEY (`device_id`),
  KEY `idx_scenic_area` (`scenic_area_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备状态表';

-- ============================================================
-- 九、初始数据（遵义会议纪念馆）
-- 注意：
-- 1. 默认管理员 / 普管 / 游客测试账号 由启动类 DataInitializer 自动生成（密码: admin123）
--    若需手动生成，运行 util.PasswordHashGenerator#main
-- 2. 示例数据采用 LAST_INSERT_ID() 变量承接插入ID，避免空库自增起始不同导致引用错乱
-- ============================================================

-- 9.1 景区：遵义会议纪念馆
INSERT INTO `scenic_area` (`name`, `intro`, `longitude`, `latitude`, `address`, `open_hours`, `cover_image`, `status`)
VALUES (
  '遵义会议纪念馆',
  '遵义会议纪念馆位于贵州省遵义市红花岗区，是为纪念1935年1月中国共产党在遵义召开的具有伟大历史转折意义的中共中央政治局扩大会议（即遵义会议）而建立的专题性纪念馆。
   遵义会议结束了王明"左"倾冒险主义在党中央的统治，确立了以毛泽东同志为代表的马克思主义正确路线在中共中央和红军的领导地位，
   是中国共产党历史上一个生死攸关的转折点，标志着中国共产党在政治上开始走向成熟。
   纪念馆包括遵义会议会址、红军总政治部旧址、毛泽东/张闻天/王稼祥住处、博古李德住处、红军遵义警备司令部旧址、红军街、红军烈士陵园、四渡赤水纪念园等多个红色遗址，
   是全国爱国主义教育示范基地、全国重点文物保护单位、首批国家一级博物馆。',
  106.9272000, 27.6934000,
  '贵州省遵义市红花岗区子尹路96号',
  '08:30-17:00（周一闭馆，16:30停止入馆）',
  '/uploads/scenic/1/cover.jpg',
  1
);
SET @scenic_id = LAST_INSERT_ID();

-- 9.2 景点 1-8
INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '遵义会议会址',
  '遵义会议会址原是黔军师长柏辉章的私邸，建于20世纪30年代，是一座中西合璧的砖木结构建筑。
   1935年1月15日至17日，中共中央政治局扩大会议在这里召开。出席会议的有毛泽东、朱德、陈云、周恩来、张闻天、博古、王稼祥、刘少奇、邓发、凯丰、刘伯承、李富春、林彪、聂荣臻、彭德怀、杨尚昆、李卓然、邓小平、李德、伍修权等20人。
   会议集中全力解决了当时具有决定意义的军事问题和组织问题，通过了《中央关于反对敌人五次"围剿"的总结决议》，改组了中央领导机构，选举毛泽东为中央政治局常委。
   会址大门的黑漆门楣上悬挂着毛泽东1964年题写的"遵义会议会址"六个大字匾额，是毛泽东为全国革命纪念馆唯一的题字。',
  '["/uploads/spots/1/1.jpg","/uploads/spots/1/2.jpg","/uploads/spots/1/3.jpg"]',
  106.9272000, 27.6934000,
  '/audio/spots/1.mp3',
  '遵义会议会址',
  1
);
SET @spot1_id = LAST_INSERT_ID();

INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '红军总政治部旧址',
  '红军总政治部旧址位于遵义会议会址东侧杨柳街28号，原为国民党遵义县党部，红军占领遵义后成为红军总政治部机关所在地。
   主任为王稼祥，副主任为李富春、贺昌、杨尚昆。在这里，红军总政治部召开了遵义各革命群众团体代表会，创建了遵义县革命委员会（红军长征途中建立的第一个县级红色政权），
   发布了《中国工农红军总政治部布告》等重要文件，负责宣传党的方针政策、扩大红军、筹措给养和维护社会治安。
   旧址内完整保存了政治部宣传科、组织科、破坏部、秘书处、油印科等办公室，陈列着当年使用过的油印机、文件箱、宣传标语等珍贵实物。',
  '["/uploads/spots/2/1.jpg","/uploads/spots/2/2.jpg"]',
  106.9281000, 27.6940000,
  '/audio/spots/2.mp3',
  '红军总政治部',
  2
);
SET @spot2_id = LAST_INSERT_ID();

INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '毛泽东 张闻天 王稼祥住处',
  '该处位于遵义市新城古式巷（今幸福巷）19号，原是黔军旅长易怀之（易少荃）的私邸，当地人称为"易宅"。
   1935年1月红军进驻遵义后，毛泽东、张闻天、王稼祥三位中央领导同志一同居住于此。
   这是一座典型的黔北民居风格的二层楼房，砖木结构，坐北朝南，四周有封火砖墙。
   据记载，著名的"遵义会议准备提纲"（即张闻天起草的反报告提纲）正是在这栋小楼里反复酝酿、讨论形成的，
   三人在此多次深入交流第五次反"围剿"失败的教训，为遵义会议的胜利召开奠定了重要的思想基础。
   1楼为王稼祥卧室（他在第四次反"围剿"中身负重伤，此处方便担架抬入），2楼分别为毛泽东、张闻天卧室。',
  '["/uploads/spots/3/1.jpg","/uploads/spots/3/2.jpg"]',
  106.9295000, 27.6958000,
  '/audio/spots/3.mp3',
  '毛泽东张闻天王稼祥住处',
  3
);
SET @spot3_id = LAST_INSERT_ID();

INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '博古 李德住处',
  '该处位于遵义市老城杨柳街天主堂旁，原为国民党贵州军阀犹国才部师长的私邸。
   红军到达遵义后，秦邦宪（博古，当时的中共中央总负责人）和共产国际军事顾问奥托·布劳恩（李德，又名华夫）被安排居住在这里。
   陪同李德的有他的翻译伍修权以及中央书记处的几位同志。
   据伍修权后来的回忆，在遵义会议上，博古和李德都曾极力为自己的错误军事路线辩护，但在大量事实和多数同志的批评下，
   博古在会议最后表示服从多数意见，李德此后则基本退出了红军的最高军事指挥核心。
   此处与毛泽东住处形成鲜明的历史对照，生动见证了中国共产党独立自主做出伟大抉择的关键时刻。',
  '["/uploads/spots/4/1.jpg","/uploads/spots/4/2.jpg"]',
  106.9278000, 27.6945000,
  '/audio/spots/4.mp3',
  '博古李德住处',
  4
);
SET @spot4_id = LAST_INSERT_ID();

INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '红军警备司令部旧址',
  '红军遵义警备司令部旧址位于遵义市老城大十字附近，原是黔军旅长周西成（后曾任贵州省主席）的官邸。
   1935年1月9日，红军进驻遵义当天，中央军委即决定成立遵义警备司令部，任命刘伯承为警备司令员，陈云为政治委员。
   警备司令部的主要任务是：维持遵义全城的社会治安、打击敌对分子的破坏活动、保护遵义会议的顺利召开、组织扩红和筹粮筹款。
   据统计，红军在遵义停留的12天内，通过警备司令部组织的扩红运动，共有4000余名遵义青壮年参加了红军，极大补充了红军的兵源。
   这里同时也是红军后勤供给部的办公地点，为红军四渡赤水前的作战物资准备发挥了重要作用。',
  '["/uploads/spots/5/1.jpg","/uploads/spots/5/2.jpg"]',
  106.9266000, 27.6929000,
  '/audio/spots/5.mp3',
  '红军警备司令部',
  5
);
SET @spot5_id = LAST_INSERT_ID();

INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '红军街（遵义红色旅游步行街）',
  '红军街北起遵义会议会址，南至红军烈士陵园，全长约600米，是一条集红色文化、民俗风情、特色美食、文创购物于一体的主题步行街。
   沿街建筑按照黔北民居"小青瓦、坡屋顶、穿斗枋、雕花窗、转角楼、三合院"的风格统一改造。
   街内设有红色书店、长征故事馆、红军食堂、红歌广场、红色邮局、长征主题雕塑群等多个文化体验点。
   值得一提的是，1935年1月遵义会议期间，毛泽东、朱德、周恩来等老一辈革命家正是沿这条老街往来于各处会议地点。
   如今，游客可在红军食堂品尝红米饭、南瓜汤、野菜等红军套餐，沉浸式体验当年红军的艰苦岁月。',
  '["/uploads/spots/6/1.jpg","/uploads/spots/6/2.jpg","/uploads/spots/6/3.jpg"]',
  106.9265000, 27.6928000,
  '/audio/spots/6.mp3',
  '红军街',
  6
);
SET @spot6_id = LAST_INSERT_ID();

INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '红军烈士陵园（红军山）',
  '红军烈士陵园位于遵义市城区中部小龙山上（当地人称"红军山"），是为纪念遵义会议前后牺牲的红军指战员而修建。
   陵园始建于1953年，占地约660亩，由烈士陵园大门、316级青石板台阶、红军烈士纪念碑、邓萍烈士墓、红军坟、红军卫生员铜像等部分组成。
   纪念碑正面是邓小平同志题写的"红军烈士永垂不朽"八个金光大字，碑座四围镶嵌四组巨型汉白玉浮雕，分别展现了"强渡乌江""遵义人民迎红军""四渡赤水""娄山关战斗"的壮阔历史画面。
   半山腰的"红军坟"是当地老百姓自发祭祀的地方，据传里面安葬的是一位因给穷苦百姓治病而被反动派杀害的不知名红军卫生员。
   每逢清明，遵义群众都会自发到此祭扫，敬献花圈、松枝，形成了深厚的红色信仰文化。',
  '["/uploads/spots/7/1.jpg","/uploads/spots/7/2.jpg","/uploads/spots/7/3.jpg"]',
  106.9255000, 27.6918000,
  '/audio/spots/7.mp3',
  '红军烈士陵园',
  7
);
SET @spot7_id = LAST_INSERT_ID();

INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES (
  @scenic_id,
  '四渡赤水纪念园',
  '四渡赤水纪念园位于遵义市仁怀市茅台镇赤水河畔，与红军长征三渡赤水主要渡口之一——茅台渡口仅一桥之隔。
   纪念园占地250余亩，由四渡赤水纪念馆、四渡赤水战役指挥艺术陈列馆、红军桥、纪念塔、英雄树、鲁班场战斗遗址展示区等组成。
   四渡赤水战役是遵义会议之后，中央红军在长征途中处于国民党几十万重兵围追堵截的艰险条件下，进行的一次决定性运动战战役。
   从1935年1月19日至3月22日，毛泽东同志等指挥中央红军三个月的时间六次穿越三条河流，转战川贵滇三省，巧妙地穿插于国民党军重兵集团围剿之间，
   不断创造战机，大量歼灭敌人，牢牢地掌握战场的主动权，取得了红军长征史上以少胜多变被动为主动的光辉战例，被毛泽东同志称为"平生最得意之笔"。',
  '["/uploads/spots/8/1.jpg","/uploads/spots/8/2.jpg","/uploads/spots/8/3.jpg"]',
  106.9230000, 27.6900000,
  '/audio/spots/8.mp3',
  '四渡赤水',
  8
);
SET @spot8_id = LAST_INSERT_ID();

-- 9.3 导览路线 3 条
INSERT INTO `guide_route` (`scenic_area_id`, `name`, `description`, `spot_ids`, `duration`, `difficulty`, `status`) VALUES (
  @scenic_id,
  '经典红色之旅',
  '涵盖景区最核心的 5 个红色景点，适合首次参观游客，90 分钟深度了解遵义会议全过程。',
  CONCAT('[', @spot1_id, ',', @spot2_id, ',', @spot3_id, ',', @spot6_id, ',', @spot7_id, ']'),
  90, 'easy', 1
);
INSERT INTO `guide_route` (`scenic_area_id`, `name`, `description`, `spot_ids`, `duration`, `difficulty`, `status`) VALUES (
  @scenic_id,
  '决策深度研学线',
  '包含博古李德住处、警备司令部等决策相关遗址，120 分钟沉浸式体验中国共产党的伟大抉择。',
  CONCAT('[', @spot1_id, ',', @spot4_id, ',', @spot3_id, ',', @spot2_id, ',', @spot5_id, ',', @spot8_id, ']'),
  120, 'medium', 1
);
INSERT INTO `guide_route` (`scenic_area_id`, `name`, `description`, `spot_ids`, `duration`, `difficulty`, `status`) VALUES (
  @scenic_id,
  '快速打卡精品线',
  '45 分钟精华体验，适合时间较紧的团体游客，直奔遵义会议会址 + 红军街 + 红军山。',
  CONCAT('[', @spot1_id, ',', @spot6_id, ',', @spot7_id, ']'),
  45, 'easy', 1
);

-- 9.4 打卡点（8 个景点各 1 个 GPS，半径 50 米）
INSERT INTO `checkpoint` (`spot_id`, `longitude`, `latitude`, `radius`) VALUES
  (@spot1_id, 106.9272000, 27.6934000, 50),
  (@spot2_id, 106.9281000, 27.6940000, 50),
  (@spot3_id, 106.9295000, 27.6958000, 50),
  (@spot4_id, 106.9278000, 27.6945000, 50),
  (@spot5_id, 106.9266000, 27.6929000, 50),
  (@spot6_id, 106.9265000, 27.6928000, 50),
  (@spot7_id, 106.9255000, 27.6918000, 50),
  (@spot8_id, 106.9230000, 27.6900000, 50);

-- 9.5 Wiki 条目（10 条，含 content Markdown 正文，用于 RAG 问答知识库）
INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '遵义会议',
  'wiki/遵义会议.md',
  '# 遵义会议\n\n## 一、会议基本信息\n\n- **时间**：1935年1月15日至17日\n- **地点**：贵州遵义老城子尹路96号（原黔军25军第2师师长柏辉章私邸）\n- **性质**：中共中央政治局扩大会议\n- **出席人员**：毛泽东、朱德、陈云、周恩来、张闻天、博古、王稼祥、刘少奇、邓发、凯丰、刘伯承、李富春、林彪、聂荣臻、彭德怀、杨尚昆、李卓然、邓小平（以上18人），列席：李德（共产国际顾问）、伍修权（翻译）\n\n## 二、历史背景\n\n1933年9月，蒋介石调集100万军队发动对中央革命根据地的第五次\"围剿\"，其中50万兵力直接进攻中央苏区。由于王明\"左\"倾教条主义在党中央占据统治地位，共产国际军事顾问李德和博古放弃了毛泽东之前行之有效的游击战和运动战方针，
转而推行\"御敌于国门之外\"、\"堡垒对堡垒\"、\"短促突击\"等冒险主义和保守主义军事路线，导致红军第五次反\"围剿\"失败，被迫于1934年10月实行战略转移——长征。\n\n长征初期，\"左\"倾领导者又犯了逃跑主义错误：大搬家式转移（携带大量辎重，包括印刷机、造币机等非战斗器材），在湘江战役中付出惨重代价。中央红军从出发时的8.6万余人锐减至3万余人。\n\n## 三、会议经过\n\n### 第一天（1月15日）\n博古作《关于第五次反\"围剿\"的总结报告》，将失败原因主要归咎于客观因素（敌人力量强大、苏区支援不够等），回避主观错误。\n\n### 第二天（1月16日）\n张闻天作了反对\"左\"倾军事路线的报告（通称\"反报告\"），系统批评博古、李德在军事指挥上的错误。随后毛泽东作长篇发言，深刻分析了前四次反\"围剿\"胜利和第五次反\"围剿\"失败的原因，列举大量事实批驳了博古、李德的错误。\n\n### 第三天（1月17日）\n王稼祥、朱德、周恩来、刘少奇、彭德怀等多数同志相继发言，完全赞同毛泽东的正确意见。会议通过了以下决议：\n\n1. 选举毛泽东为中央政治局常委\n2. 取消博古、李德的最高军事指挥权\n3. 决定由周恩来、朱德负责军事指挥\n4. 张闻天负责起草《中共中央关于反对敌人五次\"围剿\"的总结决议》（即遵义会议决议）\n\n## 四、历史意义\n\n> 遵义会议是中国共产党历史上一个**生死攸关的转折点**。\n\n1. **组织上**：确立了毛泽东同志在党中央和红军中的领导地位，形成了以毛泽东为代表的新的中央领导集体。\n2. **军事上**：结束了王明\"左\"倾教条主义军事路线在红军中的统治，恢复了灵活机动的战略战术。\n3. **政治上**：标志着中国共产党开始独立自主地运用马克思主义基本原理解决自己的问题，从幼年走向成熟。\n4. **历史上**：挽救了党、挽救了红军、挽救了中国革命，为长征胜利和中国革命在全国的胜利奠定了基础。\n',
  '["遵义会议","历史背景","转折点","毛泽东","博古","李德"]',
  '["四渡赤水","红军总政治部","遵义会议决议"]',
  'done'
);
SET @wiki1_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '四渡赤水',
  'wiki/四渡赤水.md',
  '# 四渡赤水：毛泽东军事指挥艺术的巅峰之作\n\n## 战役概况\n\n- **时间**：1935年1月19日 — 1935年3月22日\n- **地点**：贵州、四川、云南三省交界的赤水河流域\n- **指挥**：毛泽东、周恩来、朱德、王稼祥\n- **兵力对比**：中央红军约3万人 vs 国民党军约40万人\n- **结果**：红军胜利跳出包围圈，取得战略转移中具有决定意义的胜利\n\n## 四渡过程详解\n\n| 渡次 | 时间 | 渡口 | 目的与效果 |\n| --- | --- | --- | --- |\n| 一渡赤水 | 1月29日 | 土城 | 从遵义北进受挫，避其锋芒，转兵扎西，调动敌人向川南集结 |\n| 二渡赤水 | 2月18-20日 | 太平渡、二郎滩 | 杀回马枪回师黔北，重占遵义，歼灭吴奇伟部2个师8个团，俘敌3000余人 |\n| 三渡赤水 | 3月16日 | 茅台 | 公开北渡，诱使蒋介石判断红军要北渡长江，急调川、黔、滇军再次北围 |\n| 四渡赤水 | 3月21-22日 | 太平渡、二郎滩、九溪口 | 秘密南渡，急行军直插贵阳，调出滇军孙渡部东进，随后乘虚入滇，巧渡金沙江 |\n\n## 艺术特色\n\n毛泽东在七大总结中说：\"四渡赤水，那是我平生最得意之笔。\"\n\n其核心艺术在于：**高度机动、声东击西、调动敌人而不被敌人所调动**。红军在三个月内迂回穿插于川、黔、滇三省，行程数千公里，进行大小战斗40余次，
歼敌1.8万余人，将蒋介石\"围歼红军于黔北\"的战略部署彻底粉碎。四渡赤水之后，中央红军彻底摆脱了国民党军队的围追堵截，实现了遵义会议确立的战略方针。\n',
  '["四渡赤水","毛泽东","军事指挥","得意之笔","土城战役","茅台渡口"]',
  '["遵义会议","红军烈士陵园","遵义会议决议"]',
  'done'
);
SET @wiki2_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '遵义会议决议',
  'wiki/遵义会议决议.md',
  '# 《中共中央关于反对敌人五次\"围剿\"的总结决议》\n\n## 决议起草与通过\n\n- 起草人：张闻天\n- 依据：毛泽东在遵义会议上的发言内容\n- 通过时间：1935年2月8日（扎西会议正式通过文本）\n- 文件性质：中共中央正式决议文件，约1.3万字\n\n## 决议核心内容\n\n决议从军事路线、政治路线、组织路线三个层面，系统总结第五次反\"围剿\"失败和长征初期受挫的原因：\n\n1. **肯定了毛泽东的正确军事路线**：认为红军前四次反\"围剿\"的胜利是因为采取了毛泽东的运动战和游击战方针。\n2. **批判了博古、李德的\"左\"倾军事错误**：指出在第五次反\"围剿\"中\"左\"倾错误表现在三个阶段：\n   - 进攻中的冒险主义（\"御敌于国门之外\"）\n   - 防御中的保守主义（\"堡垒对堡垒\"）\n   - 退却中的逃跑主义（\"大搬家\"式转移）\n3. **改组中央军事领导**：取消\"三人团\"（博古、李德、周恩来），设立毛泽东、周恩来、王稼祥三人军事指挥小组（简称\"新三人团\"），代表中央全权指挥军事。\n4. **确立新的战略方向**：放弃与红二、六军团会合的原定计划，改在云贵川边区建立新的根据地。\n',
  '["遵义会议决议","张闻天","军事路线","三人团","扎西会议"]',
  '["遵义会议","四渡赤水"]',
  'done'
);
SET @wiki3_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '红军总政治部',
  'wiki/红军总政治部.md',
  '# 红军总政治部的使命与在遵义的工作\n\n## 组织架构\n\n- 主任：王稼祥\n- 副主任：李富春、贺昌、杨尚昆\n- 下设机构：宣传部、组织部、破坏部（敌军工作部）、秘书处、青年部、地方工作部、油印科\n\n## 在遵义的主要工作（1935.1.9 - 1.19）\n\n### 1. 建立红色政权\n1月12日，总政治部召开遵义各革命群众团体代表大会，成立了红军长征途中第一个县级革命政权——**遵义县革命委员会**，由罗梓铭任主席。\n\n### 2. 扩红运动\n通过宣传、群众集会、戏剧演出等多种方式，12天中组织遵义及其附近各县共4000多名青壮年参加了红军。\n\n### 3. 筹粮筹款\n没收军阀、地主、官僚的财产和粮食，共筹粮10万余斤，筹款数万银元，为四渡赤水作战提供了重要物资保障。\n\n### 4. 宣传工作\n印发《中国工农红军总政治部布告》、《致黔北民众书》等宣传品数万份，运用标语、壁画、街头演讲等形式宣传党的抗日主张和民族政策。\n',
  '["红军总政治部","王稼祥","李富春","扩红运动","遵义县革命委员会"]',
  '["遵义会议","红军警备司令部"]',
  'done'
);
SET @wiki4_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '红军警备司令部',
  'wiki/红军警备司令部.md',
  '# 遵义警备司令部的设立与贡献\n\n## 成立背景\n\n红军于1935年1月7日攻占遵义后，为了维护城市秩序、保障遵义会议顺利召开，中央军委于1月9日正式成立**遵义警备司令部**。\n\n## 领导班子\n\n- 司令员：刘伯承（红军总参谋长兼任）\n- 政治委员：陈云（中央政治局委员兼任）\n- 作战参谋：张云逸、叶剑英等协助\n\n## 主要职责\n\n1. **城市治安**：负责遵义全城的警戒巡逻、清查特务、打击投机倒把。\n2. **保卫会议**：在遵义会议会址周边设立三道警戒线，严密防范国民党特务破坏。\n3. **扩红征粮**：协助总政治部组织扩红、征粮筹款。\n4. **后勤保障**：管理军械、被服、医药等作战物资的集中与分配。\n\n## 刘伯承的传奇\n\n刘伯承司令员是四川开县人，辛亥革命时参加学生军，后入重庆军政府将校学堂。长征中，他作为红军总参谋长，亲自指挥了强渡乌江、智取遵义等战斗。
在遵义警备司令任内，他严明军纪，亲自巡视执勤，确保了遵义会议12天的绝对安全。\n',
  '["红军警备司令部","刘伯承","陈云","扩红征粮","保卫会议"]',
  '["遵义会议","红军总政治部"]',
  'done'
);
SET @wiki5_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '毛泽东生平（遵义会议前后）',
  'wiki/毛泽东生平遵义会议前后.md',
  '# 毛泽东：从\"靠边站\"到核心领导\n\n## 1934 年底之前：受排挤的艰难岁月\n\n从1931年六届四中全会王明上台后，毛泽东的正确主张就不断受到\"左\"倾教条主义的指责和批评：\n- 1931年11月赣南会议：被指责为\"狭隘经验论\"、\"富农路线\"\n- 1932年10月宁都会议：被撤销红一方面军总政委职务，失去军事指挥权\n- 第五次反\"围剿\"期间：中央和红军的战略战术完全由博古、李德决定，毛泽东几乎没有发言权\n\n长征开始后，毛泽东和张闻天、王稼祥被编在中央纵队一起行军，途中三人多次深入交谈第五次反\"围剿\"失败的原因，形成了反对\"左\"倾错误的共同认识，这被称为\"担架上的三人团\"。\n\n## 遵义会议：伟大转折\n\n1935年1月15日至17日，在遵义会议上，毛泽东作长篇发言，用大量事实批判博古、李德的错误军事路线。王稼祥在发言中掷地有声地说：\"事实证明，博古、李德不行，必须让毛泽东出来指挥。\"\n\n会议最后：\n- 毛泽东当选为中央政治局常委\n- 取消博古、李德的军事指挥权\n- 军事指挥由周恩来、朱德负责，毛泽东为周恩来在军事指挥上的帮助者\n\n## 之后的历史证明\n\n从遵义会议到长征胜利，再到抗日战争、解放战争、新中国成立，历史以铁一般的事实证明：正是因为确立了毛泽东在党和红军中的领导地位，中国革命才走上了不断胜利的正确道路。\n',
  '["毛泽东","生平","博古","李德","王稼祥","军事指挥","担架三人团"]',
  '["遵义会议","四渡赤水","博古李德住处"]',
  'done'
);
SET @wiki6_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '博古与李德：长征初期的指挥者',
  'wiki/博古与李德.md',
  '# 博古与李德\n\n## 博古（1907—1946）\n\n原名秦邦宪，江苏无锡人。1925年加入中国共产党，1926年赴莫斯科中山大学学习，1930年回国后在上海负责共青团中央工作。1931年9月六届四中全会后，24岁的博古成为中共中央临时总负责人。\n\n由于年纪轻、缺乏实际革命斗争经验，博古主要听从共产国际的指示和顾问李德的意见，忠实地执行了王明\"左\"倾教条主义路线，在第五次反\"围剿\"中犯下严重错误。\n\n## 李德（1900—1974）\n\n原名奥托·布劳恩（Otto Braun），德国人。1928年进入伏龙芝军事学院学习，1932年春毕业后被共产国际派往中国。他到达中央苏区后，以共产国际军事顾问的身份，实际掌握了红军的最高军事指挥权。\n\n李德既不懂中国国情，也不懂红军的实际情况，只凭在军事学院学到的书本知识指挥作战。他和博古一起推行了一系列错误的军事路线：\n- 堡垒对堡垒：以红军劣势装备和敌人打阵地战\n- 短促突击：敌军推进时红军短促突击\n- 大搬家：长征初期携带一切物资，损失惨重\n\n## 遵义会议之后\n\n1935年2月5日，在扎西会议上，博古经张闻天、周恩来谈话后，服从了组织决定，交出了党中央最高领导权，由张闻天接替。此后，博古负责党的宣传和统战工作，仍为党做了大量有益工作。1946年4月8日，博古在返回延安途中因飞机失事遇难，即\"四八烈士\"。\n\n李德在遵义会议后基本退出指挥岗位，随红军到达陕北，1939年返回苏联，此后从事翻译和写作工作。',
  '["博古","李德","秦邦宪","奥托布劳恩","三人团","左倾","共产国际"]',
  '["遵义会议","毛泽东生平遵义会议前后","遵义会议决议"]',
  'done'
);
SET @wiki7_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '红军烈士陵园',
  'wiki/红军烈士陵园.md',
  '# 红军山的故事：永远的纪念\n\n## 陵园概览\n\n红军烈士陵园位于遵义市小龙山，占地面积约660亩，距遵义会议会址约1公里。1953年由遵义地委决定兴建，将原零散安葬在遵义城郊的红军烈士遗骨集中迁葬于此，1958年正式建成开放。\n\n## 主要纪念建筑\n\n### 1. 红军烈士纪念碑\n\n碑高30米，碑正面镶嵌着邓小平同志1984年亲笔题写的\"红军烈士永垂不朽\"八个鎏金大字。碑座四周是四组大型汉白玉浮雕，分别刻画了：\n- 强渡乌江\n- 遵义人民迎红军\n- 四渡赤水\n- 娄山关战斗\n\n### 2. 邓萍烈士墓\n\n邓萍，四川富顺人，平江起义领导人之一，红军第3军团参谋长，是红军长征途中牺牲的最高级别将领。1935年2月27日，在二渡赤水重占遵义的战斗中，邓萍亲临前线侦察敌情，不幸中弹牺牲，年仅27岁。\n\n### 3. 红军坟（卫生员铜像）\n\n传说是一位因给当地百姓治病而被反动派杀害的红军卫生员。解放后，遵义群众自发在墓前祭拜，常以\"菩萨\"相称，香火不断，形成了独特的民间红色祭拜文化。\n',
  '["红军烈士陵园","红军山","邓萍","红军坟","邓小平题字","四八烈士"]',
  '["红军街","遵义会议","四渡赤水"]',
  'done'
);
SET @wiki8_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '红军街：从战场老街到文旅步行街',
  'wiki/红军街.md',
  '# 红军街：一条连接历史与今天的街道\n\n## 街道概况\n\n红军街位于遵义市红花岗区老城片区，北连遵义会议会址，南接红军烈士陵园，全长约600米，宽约15米。\n\n## 历史沿革\n\n- **明清时代**：遵义府城一条连接南北的主要街道，商铺林立，商贾云集\n- **1935年1月**：红军进驻遵义期间，毛泽东、朱德、周恩来等老一辈革命家多次往返于此\n- **改革开放后**：逐渐改造为商业步行街，但风貌逐渐失色\n- **2009年**：遵义市委、市政府启动红军街改造提升工程\n- **2011年**：改造完成，正式定名\"红军街\"\n\n## 主要体验点\n\n1. **红色书店**：专营党史、红色文化书籍\n2. **长征故事馆**：通过声光电手段再现长征历史\n3. **红军食堂**：红米饭、南瓜汤、野菜、糙米饭——体验式红军套餐\n4. **红色邮局**：可盖\"遵义会议旧址\"风景邮戳\n5. **红歌广场**：每日定时播放、表演红歌演唱\n6. **文创市集**：红色主题文创产品，本地特色手工艺\n',
  '["红军街","红歌广场","红色邮局","红军食堂","文创市集"]',
  '["遵义会议会址","红军烈士陵园"]',
  'done'
);
SET @wiki9_id = LAST_INSERT_ID();

INSERT INTO `wiki_entry` (`scenic_area_id`, `title`, `file_path`, `content`, `tags`, `links`, `compile_status`) VALUES (
  @scenic_id, '长征时间线：从瑞金到延安',
  'wiki/长征时间线.md',
  '# 二万五千里长征：关键时间节点\n\n| 时间 | 事件 | 地点 |\n| --- | --- | --- |\n| 1934.10.10 | 中共中央、中革军委率中央红军8.6万人撤出中央苏区，开始长征 | 江西瑞金、于都 |\n| 1934.11.25-12.1 | 湘江战役，损失惨重，8.6万→3万余人 | 广西兴安、全州 |\n| 1934.12.15 | 中央红军攻占贵州黎平，召开黎平会议，接受毛泽东转兵贵州的正确建议 | 贵州黎平 |\n| 1935.1.1 | 强渡乌江天险，击溃黔军侯之担部 | 贵州乌江江界河 |\n| 1935.1.7 | 智取遵义 | 贵州遵义 |\n| 1935.1.15-17 | **遵义会议召开，伟大的历史转折** | 贵州遵义 |\n| 1935.1.29 | **一渡赤水** | 贵州土城 |\n| 1935.2.18-20 | **二渡赤水**，回师黔北 | 贵州太平渡、二郎滩 |\n| 1935.2.24-28 | 遵义大捷，歼灭吴奇伟2个师 | 贵州遵义 |\n| 1935.3.16 | **三渡赤水** | 贵州茅台 |\n| 1935.3.21-22 | **四渡赤水** | 贵州太平渡 |\n| 1935.4.29-5.9 | 巧渡金沙江，跳出国民党包围圈 | 云南皎平渡 |\n| 1935.5.22 | 彝海结盟（刘伯承与小叶丹歃血为盟） | 四川冕宁 |\n| 1935.5.24-25 | 强渡大渡河（17勇士飞舟） | 四川安顺场 |\n| 1935.5.29 | 飞夺泸定桥（22勇士攀铁索） | 四川泸定 |\n| 1935.6.12 | 翻越夹金山（第一座雪山） | 四川宝兴 |\n| 1935.6.14 | 中央红军与红四方面军会师 | 四川懋功（今小金） |\n| 1935.8.21 | 过草地（6天6夜） | 川西北松潘草地 |\n| 1935.9.16-17 | 突破天险腊子口 | 甘肃迭部 |\n| 1935.10.19 | 到达陕北吴起镇，与红15军团会师 | 陕西吴起 |\n| 1936.10.22 | 红一、二、四方面军三大主力胜利会师 | 甘肃会宁、静宁 |\n\n## 长征意义\n\n长征的胜利，是中国革命转危为安的关键。毛泽东同志说：\"长征是宣言书，长征是宣传队，长征是播种机。\"长征铸就了伟大的\"长征精神\"，始终是激励中国人民不断前进的巨大精神力量。\n',
  '["长征","时间线","瑞金","延安","湘江战役","遵义会议","四渡赤水","巧渡金沙江","飞夺泸定桥"]',
  '["遵义会议","四渡赤水","红军烈士陵园"]',
  'done'
);
SET @wiki10_id = LAST_INSERT_ID();

-- 9.6 推荐问题（20 条热门问题）
INSERT INTO `recommendation_question` (`scenic_area_id`, `question`, `sort_weight`) VALUES
  (@scenic_id, '遵义会议是什么时候召开的？有什么重要意义？', 100),
  (@scenic_id, '参加遵义会议的有哪些人？', 95),
  (@scenic_id, '遵义会议为什么被称为"生死攸关的转折点"？', 90),
  (@scenic_id, '毛泽东在遵义会议上做了什么重要发言？', 85),
  (@scenic_id, '博古和李德在遵义会议后去了哪里？', 80),
  (@scenic_id, '四渡赤水是哪四次？为什么说是毛泽东得意之笔？', 98),
  (@scenic_id, '长征的时间线是怎样的？从哪里开始到哪里结束？', 88),
  (@scenic_id, '遵义会议通过的决议包含哪些主要内容？', 86),
  (@scenic_id, '湘江战役为什么那么惨烈？', 82),
  (@scenic_id, '红军总政治部在遵义期间做了哪些重要工作？', 80),
  (@scenic_id, '张闻天在遵义会议中发挥了什么作用？', 78),
  (@scenic_id, '王稼祥为什么说"要让毛泽东出来指挥"？', 76),
  (@scenic_id, '遵义县革命委员会是怎样建立的？', 74),
  (@scenic_id, '飞夺泸定桥的22勇士后来怎么样了？', 72),
  (@scenic_id, '长征路上的"彝海结盟"是怎么回事？', 70),
  (@scenic_id, '红军烈士陵园里安葬的最高级别将领是谁？', 68),
  (@scenic_id, '邓萍烈士是怎么牺牲的？', 66),
  (@scenic_id, '红二十五军的长征和一方面军有什么不同？', 64),
  (@scenic_id, '遵义会议后确立的三人军事指挥小组是哪三人？', 62),
  (@scenic_id, '李德这个德国人为什么能指挥中国红军？', 60);

-- 9.7 特产 / 文创（8 个）
INSERT INTO `product` (`scenic_area_id`, `name`, `price`, `description`, `image`, `stock`, `category`, `status`) VALUES
  (@scenic_id, '遵义茅台酒 文创小瓶装（50ml）', 58.00,
   '贵州茅台镇特产酱香型白酒，53度50ml文创小瓶，瓶身印有遵义会议会址浮雕图案，限量纪念款。',
   '/uploads/products/1.jpg', 500, 'specialty', 1),
  (@scenic_id, '遵义红 特级红茶（150g装）', 68.00,
   '产自贵州遵义湄潭县的工夫红茶，选用明前嫩芽，汤色红艳、滋味浓醇、香气馥郁，被誉为"高原红茶明珠"。',
   '/uploads/products/2.jpg', 300, 'specialty', 1),
  (@scenic_id, '遵义虾子辣椒（罐装250g）', 32.00,
   '遵义虾子镇"中国辣椒城"正宗朝天椒、小米辣罐装，口感醇厚香辣，是黔北风味的灵魂。',
   '/uploads/products/3.jpg', 200, 'specialty', 1),
  (@scenic_id, '贵州茅台镇酱香型老酱酒（500ml礼盒）', 298.00,
   '遵义红色文化主题礼盒装，53度纯粮食坤沙工艺酱香酒，包装印有四渡赤水主题画，送礼珍藏佳品。',
   '/uploads/products/4.jpg', 150, 'specialty', 1),
  (@scenic_id, '遵义会议主题金属书签礼盒（4枚装）', 45.00,
   '文创金属书签 4 枚一组：遵义会议会址、四渡赤水、红军山、娄山关四款造型，镀金色做旧工艺，配红色礼盒。',
   '/uploads/products/5.jpg', 400, 'cultural', 1),
  (@scenic_id, '红军军号黄铜模型（1:3 缩小版）', 168.00,
   '按1935年红军司号员使用的制式军号1:3比例缩小铸造，纯黄铜材质，可吹奏出真实号音，配实木底座和收藏证书。',
   '/uploads/products/6.jpg', 120, 'cultural', 1),
  (@scenic_id, '八角红军帽金属徽章（3枚套装）', 39.00,
   '八角红军帽造型金属徽章3枚套装：红五星、镰刀锤头、红色遵义三色可选，烤漆珐琅工艺，锌合金材质。',
   '/uploads/products/7.jpg', 500, 'cultural', 1),
  (@scenic_id, '长征手账本 + 红色文化主题印章礼盒', 89.00,
   '长征主题手账本，封面仿老牛皮压印长征地图，附4枚红色文化主题印章（遵义、四渡赤水、吴起镇、会宁），可集章式打卡游览。',
   '/uploads/products/8.jpg', 200, 'cultural', 1);

-- 9.8 文物（8 件）
INSERT INTO `artifact` (`scenic_area_id`, `name`, `era`, `intro`, `images`, `location`, `category`, `wiki_ref`, `status`) VALUES
  (@scenic_id, '红军司号用铜质军号', '1935年',
   '1935年遵义会议期间红军司号员使用过的制式军号。黄铜材质，整体长约38cm，喇叭口直径11cm，号管刻有编号。
    军号在红军中既是指挥通信工具，也是精神象征——冲锋号、起床号、熄灯号、开饭号，贯穿了红军的每一个战斗日。
    此件为1958年从遵义县枫香区一位病故的老司号员家中征集而来，是一级文物。',
   '["/uploads/artifacts/1/1.jpg","/uploads/artifacts/1/2.jpg"]',
   '遵义会议纪念馆陈列厅 1 号展柜', '武器', '红军军号', 1),
  (@scenic_id, '《遵义会议决议》油印本', '1935年',
   '1935年2月扎西会议后，红军总政治部油印科用手摇式油印机印发的《中共中央关于反对敌人五次"围剿"的总结决议》（遵义会议决议）早期版本。
    白棉纸，双面刻印，共28页，封面盖有"中国工农红军总政治部宣传部"的红色篆书印章。
    由于当时物资极度匮乏，纸张多为从土豪地主家征收而来，纸质参差不一。现存世不足10本，为国家一级文物。',
   '["/uploads/artifacts/2/1.jpg","/uploads/artifacts/2/2.jpg"]',
   '遵义会议纪念馆陈列厅 2 号展柜', '文献', '遵义会议决议', 1),
  (@scenic_id, '红军电台用西门子手摇发电机', '1934年',
   '红军军委二局（电台侦查破译部门）使用的德国西门子产手摇式直流发电机，是当时红军电台的唯一供电来源。
    整机约25公斤，需2人交替摇转才能供15W电台正常工作。长征途中，正是靠这样的设备，红军保持了各部队之间的通信，
    同时军委二局还利用类似设备不间断侦听国民党军电台，破译了大量国民党军电报，为四渡赤水等作战决策提供了关键情报。',
   '["/uploads/artifacts/3/1.jpg","/uploads/artifacts/3/2.jpg"]',
   '遵义会议纪念馆陈列厅 3 号展柜', '通讯器材', '红军电台', 1),
  (@scenic_id, '长征途中使用的军事地图（手绘）', '1935年',
   '1935年2月三渡赤水前，红军总参谋部绘制的茅台-二郎滩-太平渡区域1:5万比例尺手绘军事地图，图上有红笔圈画的渡口位置和蓝笔标注的敌军部署。
    由于国军封锁严密，正规军用地图奇缺，红军的地图多由侦察兵现场测绘或从敌人手中缴获。此图对四渡赤水的指挥决策发挥了重要作用。',
   '["/uploads/artifacts/4/1.jpg","/uploads/artifacts/4/2.jpg"]',
   '遵义会议纪念馆陈列厅 4 号展柜', '文献', '四渡赤水', 1),
  (@scenic_id, '红军战士穿过的手工编织草鞋', '1935年',
   '1935年1月遵义会议期间，当地群众为红军战士连夜赶制的手工编织草鞋。以当地盛产的糯谷草、龙须草为主要材料，
    一双草鞋约需4-6小时手工编织。长征路上，一双草鞋平均只能穿3-5天，红军正是穿着这样的草鞋走完了二万五千里。
    这双草鞋征集自遵义县鸭溪镇一位当年给红军编过草鞋的老阿婆家中。',
   '["/uploads/artifacts/5/1.jpg","/uploads/artifacts/5/2.jpg"]',
   '遵义会议纪念馆陈列厅 5 号展柜', '生活用品', '长征时间线', 1),
  (@scenic_id, '红军缴获的汉阳造七九步枪', '1934年',
   '红军从国民党黔军王家烈部手中缴获的汉阳造八八式步枪（俗称"老套筒"或"汉阳造"，口径7.92mm）。
    该枪是19世纪末德国毛瑟1888式的中国仿制版，生产超过50年，是清末到抗战时期中国军队的主要步枪。
    1935年1月强渡乌江、攻占遵义的战斗中，红军曾大量缴获此型枪械装备。',
   '["/uploads/artifacts/6/1.jpg","/uploads/artifacts/6/2.jpg"]',
   '遵义会议纪念馆陈列厅 6 号展柜', '武器', '红军警备司令部', 1),
  (@scenic_id, '中华苏维埃共和国国家银行纸币（壹角/壹元）', '1934年',
   '中华苏维埃共和国国家银行1934年发行的壹角和壹元纸币。壹角券正面图案为列宁头像，壹元券正面为列宁像与苏维埃国徽。
    长征途中，苏维埃国家银行被编为"中央纵队十五大队"，携带印钞设备跟随红军主力转移。由于国民党封锁和苏区丧失，
    这些纸币在长征后期逐渐不再流通，但作为长征金融史的实物见证，具有极高历史价值。',
   '["/uploads/artifacts/7/1.jpg","/uploads/artifacts/7/2.jpg"]',
   '遵义会议纪念馆陈列厅 7 号展柜', '文献', '红军总政治部', 1),
  (@scenic_id, '红军伤员名册（手抄本）', '1935年',
   '1935年1-2月间，红军总卫生部在遵义及周边地区收容治疗伤病员的手写登记名册一本。
    记录了当时约400多名伤员的姓名、籍贯、所在部队、负伤部位、治疗情况、归队/转移/牺牲等信息。
    遵义会议期间，红军伤病员多达数千人，遵义本地百姓主动腾出房屋、捐献药品、护理伤员，谱写出军民鱼水情的动人篇章。',
   '["/uploads/artifacts/8/1.jpg","/uploads/artifacts/8/2.jpg"]',
   '遵义会议纪念馆陈列厅 8 号展柜', '文献', '红军烈士陵园', 1);

-- 9.9 成就（8 个：4 visit + 2 pose + 1 剧本 + 1 全打卡）
INSERT INTO `achievement` (`title`, `icon`, `description`, `trigger_type`, `trigger_condition`) VALUES
  ('初入遵义', '/uploads/achievements/1.png',
   '参观遵义会议会址，完成第一个景点打卡', 'visit',
   '{"spot_ids": [1], "count": 1}'),
  ('追寻伟人足迹', '/uploads/achievements/2.png',
   '参观毛泽东、张闻天、王稼祥三位革命领袖的住处', 'visit',
   '{"spot_ids": [3], "count": 1}'),
  ('英雄永志', '/uploads/achievements/3.png',
   '登上红军山，瞻仰红军烈士纪念碑', 'visit',
   '{"spot_ids": [7], "count": 1}'),
  ('得意之笔见证者', '/uploads/achievements/4.png',
   '参观四渡赤水纪念园，了解毛泽东军事指挥艺术', 'visit',
   '{"spot_ids": [8], "count": 1}'),
  ('红军礼赞', '/uploads/achievements/5.png',
   '在触摸屏端完成"敬礼"姿态互动，向革命先烈致敬', 'pose',
   '{"pose": "salute"}'),
  ('长征告别·挥手', '/uploads/achievements/6.png',
   '在触摸屏端完成"挥手"姿态互动，重温长征送别之情', 'pose',
   '{"pose": "wave"}'),
  ('抉择者', '/uploads/achievements/7.png',
   '完成《生死抉择·1935》剧本通关，感受伟大历史转折', 'script',
   '{"script_id": 1, "completed": 1}'),
  ('遵义大会师', '/uploads/achievements/8.png',
   '完成景区全部 8 个景点打卡，点亮完整红色地图', 'checkin',
   '{"spot_count": 8, "scenic_area_id": 1}');

-- 9.10 剧本《生死抉择·1935》
INSERT INTO `script` (`scenic_area_id`, `title`, `description`, `cover_image`, `roles`, `nodes`, `start_node_id`, `achievements`, `status`) VALUES (
  @scenic_id,
  '生死抉择·1935',
  '1935年1月，中国革命走到了最危险的十字路口。第五次反"围剿"失败、湘江战役惨败、8万红军只剩3万人……
   在遵义这座黔北小城里，一群人即将决定红军、中国共产党、乃至整个中国的命运。\n\n你将扮演一位年轻的红军参谋，亲历那场伟大的历史抉择。
   你会支持谁？你会提出什么建议？你的每一个选择，都将影响故事的走向和最终结局。',
  '/uploads/scripts/1/cover.jpg',
  -- 角色：玩家参谋 + 毛泽东 + 博古 + 李德 + 张闻天
  '[
    {"id": 1, "name": "你（小参谋·小钟）", "avatar": "/uploads/scripts/1/roles/1.png", "skill": "参会记录员，可查阅作战资料"},
    {"id": 2, "name": "毛泽东（毛委员）", "avatar": "/uploads/scripts/1/roles/2.png", "skill": "擅长运动战、游击战，军事眼光敏锐"},
    {"id": 3, "name": "博古（秦邦宪，中央总负责）", "avatar": "/uploads/scripts/1/roles/3.png", "skill": "理论功底深厚，坚持共产国际路线"},
    {"id": 4, "name": "李德（共产国际军事顾问）", "avatar": "/uploads/scripts/1/roles/4.png", "skill": "伏龙芝军事学院毕业，主张堡垒对堡垒"},
    {"id": 5, "name": "张闻天（洛甫）", "avatar": "/uploads/scripts/1/roles/5.png", "skill": "冷静务实，善作分析报告"}
  ]',
  -- 10 个剧情节点，3 个分支
  '[
    {
      "id": 1,
      "sceneText": "1935年1月9日，遵义城。你作为红一方面军司令部的年轻参谋，跟随中央纵队刚进驻这座黔北重镇。\n\n清晨，遵义县革命委员会成立大会正在天主教堂举行，红军总政治部副主任李富春正在台上演讲。台下的百姓人头攒动，掌声雷动。\n\n突然，你看到机要员匆匆走来，递给你一份刚译出的电报——蒋介石调集了 40 万大军，正从川、黔、滇三面合围而来！",
      "options": [
        {"text": "立刻把电报送给毛泽东", "nextNodeId": 2},
        {"text": "按照惯例先交给博古和李德", "nextNodeId": 3}
      ]
    },
    {
      "id": 2,
      "sceneText": "你来到古式巷易宅，毛泽东正在屋中与张闻天、王稼祥谈话。看了电报，毛泽东用一根手指指着军用地图上的\"土城\"二字，沉声道：\n\n\"博古和李德要在这里和郭勋祺决战，搞不好，又要吃败仗。洛甫、稼祥，我看是该开会好好总结一下了。\" \n\n他转向你：\"小参谋，你的意见呢？\"",
      "options": [
        {"text": "我支持毛委员，应该马上开政治局扩大会议，总结失败教训！", "nextNodeId": 4},
        {"text": "我觉得目前军情紧急，还是先打好这一仗再说……", "nextNodeId": 5}
      ]
    },
    {
      "id": 3,
      "sceneText": "你到杨柳街博古住处时，李德正在用俄文向伍修权高声说着什么。博古看了电报，眉头紧锁：\n\n\"这是意料之中的。按既定方针，红军还是要北渡长江和张国焘、徐向前的四方面军会合。\"\n\n李德通过翻译补充：\"命令部队明天就向土城前进，要和川军打一场漂亮的阵地战，像苏联红军那样！\"",
      "options": [
        {"text": "（内心赞同）相信共产国际顾问的专业指挥，按命令办。", "nextNodeId": 5},
        {"text": "（心里有疑问）但前四次反围剿都是按毛泽东的办法赢的……", "nextNodeId": 4}
      ]
    },
    {
      "id": 4,
      "sceneText": "1月15日，历史性的遵义会议在柏公馆二楼客厅召开。博古作完总结报告后，张闻天站起来作了系统的\"反报告\"——正是你们三人在前几日反复讨论的内容。\n\n会场安静得听得见窗外的雪粒声。你坐在后排负责会议记录，双手因激动微微发抖。\n\n毛泽东最后做总结发言，他提出一个决定红军命运的问题：今后谁来指挥？",
      "options": [
        {"text": "在记录簿上郑重写下：\"多数同志建议：由毛泽东同志担任中央政治局常委，参与军事指挥。\"", "nextNodeId": 6},
        {"text": "（小声对旁边的邓小平说）我担心博古不会轻易交权……", "nextNodeId": 6}
      ]
    },
    {
      "id": 5,
      "sceneText": "1月28日，土城战斗打响。红军猛攻川军郭勋祺部，不料敌军越打越多——原探报是4个团，实际投入超过8个团。\n\n红军伤亡惨重。朱德总司令亲自冲到前沿指挥，陈赓团长负了伤，干部团的学生兵也全部拉上去……\n\n傍晚，你浑身是血地从前线撤下，路过毛泽东身边时，听见他低声对周恩来说：\"这个仗，不能再这么打下去了。\"",
      "options": [
        {"text": "你强忍着眼泪，下定决心：一定要在即将召开的遵义会议上，支持正确路线。", "nextNodeId": 4},
        {"text": "（END 分支 · 历史遗憾线）你继续盲从教条路线，后来……", "nextNodeId": 8}
      ]
    },
    {
      "id": 6,
      "sceneText": "经过三天激烈的讨论，遵义会议终于迎来了最后的表决时刻。\n\n19人举手表决：17票赞成，2票保留意见。\n\n最后，周恩来站起来庄重地说：\"同志们，我们中国共产党人，终于可以自己决定自己的命运了。\"",
      "options": [
        {"text": "（结局 A · 真实历史线）会议确立了毛泽东同志的领导地位，四渡赤水即将开始……", "nextNodeId": 7},
        {"text": "（结局 B · 历史的另一种可能）如果那天不是这个结果……", "nextNodeId": 9}
      ]
    },
    {
      "id": 7,
      "sceneText": "【剧本通关 · 真实结局】\n\n从此刻起，你以一个亲历者的身份，见证了中国革命的伟大转折。\n\n接下来的故事，已被历史永远铭刻——\n\n★ 1935年1月29日 一渡赤水，\n★ 2月18-20日 二渡赤水，遵义大捷，\n★ 3月16日 三渡赤水，\n★ 3月21-22日 四渡赤水，\n\n……最后，红军胜利到达陕北，三大主力1936年10月会宁会师。\n\n1949年10月1日，你以38岁的年纪站在天安门广场的观礼台上，望着五星红旗冉冉升起。\n\n遵义，那间小小的客厅，便是这一切伟大故事的起点。\n\n（恭喜通关！已解锁成就：「抉择者」）",
      "options": [],
      "end": true
    },
    {
      "id": 8,
      "sceneText": "【历史遗憾结局 · 不要真的选这条路线哦～】\n\n红军继续执行\"左\"倾错误路线，在土城与优势川军拼消耗，损失了大量有生力量。\n\n随后几个月里，由于没有灵活机动的战略战术，红军始终未能跳出国民党大军的包围圈……\n\n（这条路线只是让你体验一下：中国革命的胜利并非理所当然，而正是遵义会议——这个伟大的历史抉择——才让光明最终到来。）\n\n要不要重新选择？",
      "options": [
        {"text": "回到遵义会议前夕，重新作出正确选择。", "nextNodeId": 4}
      ]
    },
    {
      "id": 9,
      "sceneText": "【想象结局 · 中国人民已经证明了哪种选择是正确的】\n\n假如那天的会议不是这个结果，那长征可能失败，中国革命可能还要在黑暗中摸索几十年。\n\n但历史没有如果——1935年的那个春天，中国人在没有共产国际干预的情况下，第一次独立自主地选择了正确的领袖、正确的路线。\n\n这便是遵义会议留给后人最宝贵的精神财富：相信自己，相信人民，相信实践。\n\n（恭喜你完成整个剧本！已解锁成就：「抉择者」）",
      "options": [],
      "end": true
    }
  ]',
  1,
  '[7]',
  1
);

-- 9.11 触摸屏设备 2 台
INSERT INTO `device_status` (`device_id`, `scenic_area_id`, `online`, `hailo_status`, `camera_connected`, `speaker_connected`, `last_heartbeat`) VALUES
  ('rpi-001', @scenic_id, 1, 'active', 1, 1, NOW()),
  ('rpi-002', @scenic_id, 1, 'active', 1, 1, NOW());

-- ============================================================
-- 建表 + 初始化数据完成
-- 共 18 张表，遵义纪念馆完整测试数据：
--   1 景区、8 景点、3 路线、8 打卡点、10 Wiki、20 推荐问题、
--   8 特产文创、8 件文物、8 个成就、1 个完整剧本、2 台触摸屏
--   账号（admin/普管/游客）由 DataInitializer 在启动时创建
-- ============================================================
