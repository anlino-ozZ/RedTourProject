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
-- 九、初始数据
-- 注意：
-- 1. 默认管理员账号密码由启动类 DataInitializer 自动生成（密码: admin123）
--    若需手动生成，运行 util.PasswordHashGenerator#main
-- 2. 示例数据采用 LAST_INSERT_ID() 变量承接插入ID，避免空库自增起始不同导致引用错乱
-- ============================================================

-- 示例景区（承接ID到变量）
INSERT INTO `scenic_area` (`name`, `intro`, `longitude`, `latitude`, `address`, `open_hours`, `cover_image`, `status`)
VALUES ('遵义会议纪念馆', '遵义会议是中国共产党历史上一个生死攸关的转折点，1935年1月在此召开。', 106.9272000, 27.6934000, '贵州省遵义市红花岗区子尹路96号', '08:30-17:00', '/uploads/scenic/1/cover.jpg', 1);
SET @scenic_id = LAST_INSERT_ID();

-- 示例景点（用 @scenic_id，景点ID承接变量）
INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES
  (@scenic_id, '遵义会议会址', '遵义会议在此召开，确立了毛泽东在党中央和红军的领导地位。', '["/uploads/spots/1/1.jpg","/uploads/spots/1/2.jpg"]', 106.9272000, 27.6934000, '/audio/spots/1.mp3', 'wiki/遵义会议', 1);
SET @spot1_id = LAST_INSERT_ID();
INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES
  (@scenic_id, '红军总政治部旧址', '红军长征途中的总政治部所在地，负责政治宣传和思想工作。', '["/uploads/spots/2/1.jpg"]', 106.9281000, 27.6940000, '/audio/spots/2.mp3', 'wiki/红军总政治部', 2);
SET @spot2_id = LAST_INSERT_ID();
INSERT INTO `spot` (`scenic_area_id`, `name`, `intro`, `images`, `longitude`, `latitude`, `audio_url`, `wiki_ref`, `sort_order`) VALUES
  (@scenic_id, '红军街', '红军长征经过的老街，保留了大量革命时期的历史建筑。', '["/uploads/spots/3/1.jpg"]', 106.9265000, 27.6928000, '/audio/spots/3.mp3', 'wiki/红军街', 3);
SET @spot3_id = LAST_INSERT_ID();

-- 示例导览路线（spot_ids 使用拼接的变量）
INSERT INTO `guide_route` (`scenic_area_id`, `name`, `description`, `spot_ids`, `duration`, `difficulty`, `status`)
VALUES (@scenic_id, '经典红色之旅', '涵盖景区核心红色景点', CONCAT('[', @spot1_id, ',', @spot2_id, ',', @spot3_id, ']'), 90, 'easy', 1);

-- 示例打卡点
INSERT INTO `checkpoint` (`spot_id`, `longitude`, `latitude`, `radius`) VALUES
  (@spot1_id, 106.9272000, 27.6934000, 50),
  (@spot2_id, 106.9281000, 27.6940000, 50),
  (@spot3_id, 106.9265000, 27.6928000, 50);

-- 示例推荐问题
INSERT INTO `recommendation_question` (`scenic_area_id`, `question`, `sort_weight`) VALUES
  (@scenic_id, '长征路上有哪些关键决策？', 30),
  (@scenic_id, '遵义会议的历史背景是什么？', 20),
  (@scenic_id, '红军四渡赤水经历了哪些战役？', 10);

-- 示例特产
INSERT INTO `product` (`scenic_area_id`, `name`, `price`, `description`, `image`, `stock`, `category`, `status`) VALUES
  (@scenic_id, '遵义茅台酒', 58.00, '贵州茅台镇特产酱香型白酒', '/uploads/products/1.jpg', 100, 'specialty', 1),
  (@scenic_id, '红色文化书签', 25.00, '遵义会议主题金属书签', '/uploads/products/2.jpg', 200, 'cultural', 1);

-- 示例文物（补 status=1）
INSERT INTO `artifact` (`scenic_area_id`, `name`, `era`, `intro`, `images`, `location`, `category`, `wiki_ref`, `status`) VALUES
  (@scenic_id, '红军军号', '1935年', '红军长征时期使用的铜质军号，司号员负责传递战斗命令。', '["/uploads/artifacts/1/1.jpg"]', '遵义会议纪念馆', '武器', 'wiki/红军军号', 1),
  (@scenic_id, '遵义会议决议', '1935年', '遵义会议通过的重要决议文件副本。', '["/uploads/artifacts/2/1.jpg"]', '遵义会议纪念馆', '文献', 'wiki/遵义会议决议', 1);

-- 示例设备
INSERT INTO `device_status` (`device_id`, `scenic_area_id`, `online`, `hailo_status`, `camera_connected`, `speaker_connected`, `last_heartbeat`)
VALUES ('rpi-001', @scenic_id, 1, 'active', 1, 1, NOW());

-- ============================================================
-- 建表完成
-- 共 18 张表，覆盖 PRD 全部 12 个核心实体 + 接口文档 6 个关联表
-- ============================================================
