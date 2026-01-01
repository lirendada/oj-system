-- ==================================================================================
-- Spring Cloud Alibaba OJ 系统初始化脚本
-- 版本：V1.0
-- 包含模块：用户、题目、标签、题解、判题记录、竞赛、系统管理
-- ==================================================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS oj_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE oj_system;

-- ==================================================================================
-- 1. 系统管理模块 (后台管理员)
-- ==================================================================================

DROP TABLE IF EXISTS `tb_sys_user`;
CREATE TABLE `tb_sys_user` (
    `user_id` BIGINT NOT NULL COMMENT '管理员ID',
    `user_account` VARCHAR(50) NOT NULL COMMENT '账号',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `nick_name` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_user_account` (`user_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统管理员表';

-- 初始化超级管理员 (密码: 123456)
INSERT INTO `tb_sys_user` (`user_id`, `user_account`, `password`, `nick_name`) VALUES
(1, 'admin', '$2a$10$rd71IhdgCfR6IcjNltq2oOXz9fL9uYtwMO1F7fI4yRSsfiQZFBVP2', '超级管理员');


-- ==================================================================================
-- 2. C端用户模块 (普通做题用户)
-- ==================================================================================

DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `user_account` VARCHAR(50) NOT NULL COMMENT '账号',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `nick_name` VARCHAR(50) COMMENT '昵称',
    `avatar` VARCHAR(500) COMMENT '头像URL',
    `email` VARCHAR(100) COMMENT '邮箱',
    `phone` VARCHAR(20) COMMENT '手机号',
    `school` VARCHAR(100) COMMENT '学校',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
    `submitted_count` INT DEFAULT 0 COMMENT '提交次数',
    `accepted_count` INT DEFAULT 0 COMMENT '通过次数',
    `rating` INT DEFAULT 1500 COMMENT 'Rating分数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_user_account` (`user_account`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端普通用户表';

-- 初始化测试用户 (密码: 123456)
INSERT INTO `tb_user` (`user_id`, `user_account`, `password`, `nick_name`, `email`, `school`, `rating`) VALUES
(1001, 'user001', '$2a$10$rd71IhdgCfR6IcjNltq2oOXz9fL9uYtwMO1F7fI4yRSsfiQZFBVP2', '选手小明', 'user001@test.com', '清华大学', 1500),
(1002, 'user002', '$2a$10$rd71IhdgCfR6IcjNltq2oOXz9fL9uYtwMO1F7fI4yRSsfiQZFBVP2', '选手小红', 'user002@test.com', '北京大学', 1600);


-- ==================================================================================
-- 3. 题库管理模块 (核心业务)
-- ==================================================================================

-- 3.1 题目表
DROP TABLE IF EXISTS `tb_problem`;
CREATE TABLE `tb_problem` (
    `problem_id` BIGINT NOT NULL COMMENT '题目ID',
    `title` VARCHAR(200) NOT NULL COMMENT '题目标题',
    `difficulty` TINYINT NOT NULL COMMENT '难度：1-简单 2-中等 3-困难',
    `submit_num` INT NOT NULL DEFAULT 0 COMMENT '提交总数',
    `accepted_num` INT NOT NULL DEFAULT 0 COMMENT '通过总数',
    `description` LONGTEXT NOT NULL COMMENT '题目描述(支持Markdown)',
    `input_description` TEXT COMMENT '输入描述',
    `output_description` TEXT COMMENT '输出描述',
    `time_limit` INT NOT NULL DEFAULT 1000 COMMENT '时间限制(ms)',
    `memory_limit` INT NOT NULL DEFAULT 128 COMMENT '内存限制(MB)',
    `stack_limit` INT DEFAULT 128 COMMENT '栈限制(MB)',
    `sample_input` TEXT COMMENT '样例输入(展示用)',
    `sample_output` TEXT COMMENT '样例输出(展示用)',
    `hint` TEXT COMMENT '提示',
    `source` VARCHAR(200) COMMENT '来源',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-隐藏 1-正常',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`problem_id`),
    KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';

-- 3.2 题目标签表
DROP TABLE IF EXISTS `tb_problem_tag`;
CREATE TABLE `tb_problem_tag` (
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    `tag_name` VARCHAR(50) NOT NULL COMMENT '标签名称',
    `tag_color` VARCHAR(20) COMMENT '标签颜色(如#FF0000)',
    PRIMARY KEY (`tag_id`),
    UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签表';

-- 3.3 题目-标签关联表
DROP TABLE IF EXISTS `tb_problem_tag_relation`;
CREATE TABLE `tb_problem_tag_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `problem_id` BIGINT NOT NULL COMMENT '题目ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_problem_tag` (`problem_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`) -- 方便通过标签反查题目
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签关联表';

-- 3.4 测试用例表 (判题用)
DROP TABLE IF EXISTS `tb_test_case`;
CREATE TABLE `tb_test_case` (
    `case_id` BIGINT NOT NULL COMMENT '测试用例ID',
    `problem_id` BIGINT NOT NULL COMMENT '题目ID',
    `input` LONGTEXT NOT NULL COMMENT '输入数据(大文本)',
    `output` LONGTEXT NOT NULL COMMENT '期望输出(大文本)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`case_id`),
    KEY `idx_problem_id` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例表';

-- 3.5 题解表 (新增)
DROP TABLE IF EXISTS `tb_solution`;
CREATE TABLE `tb_solution` (
    `solution_id` BIGINT NOT NULL COMMENT '题解ID',
    `problem_id` BIGINT NOT NULL COMMENT '题目ID',
    `user_id` BIGINT NOT NULL COMMENT '发布用户ID',
    `title` VARCHAR(100) NOT NULL COMMENT '题解标题',
    `content` LONGTEXT NOT NULL COMMENT '题解内容(Markdown)',
    `cover` VARCHAR(500) DEFAULT NULL COMMENT '封面图',
    `visit_count` INT DEFAULT 0 COMMENT '浏览量',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `reply_count` INT DEFAULT 0 COMMENT '评论数',
    `type` TINYINT DEFAULT 0 COMMENT '类型：0-用户题解 1-官方题解',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-草稿 1-发布 2-下架',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`solution_id`),
    KEY `idx_problem_id` (`problem_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题解表';


-- ==================================================================================
-- 4. 判题记录模块 (核心业务 - 新增)
-- ==================================================================================

DROP TABLE IF EXISTS `tb_submit_record`;
CREATE TABLE `tb_submit_record` (
    `submit_id` BIGINT NOT NULL COMMENT '提交ID',
    `problem_id` BIGINT NOT NULL COMMENT '题目ID',
    `contest_id` BIGINT DEFAULT 0 COMMENT '竞赛ID (0表示非竞赛提交)',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `code` LONGTEXT NOT NULL COMMENT '提交的代码',
    `language` VARCHAR(20) NOT NULL COMMENT '编程语言(Java/C++/Python)',

    -- 判题状态与结果
    `status` TINYINT NOT NULL DEFAULT 10 COMMENT '判题状态: 10-待判题, 20-判题中, 30-结束',
    `judge_result` TINYINT DEFAULT NULL COMMENT '判题结果: 0-AC, 1-WA, 2-TLE, 3-MLE, 4-RE, 5-CE...',

    -- 运行指标
    `time_cost` INT DEFAULT NULL COMMENT '最大耗时(ms)',
    `memory_cost` INT DEFAULT NULL COMMENT '最大内存(KB)',
    `error_message` TEXT DEFAULT NULL COMMENT '错误信息(编译错误/运行错误)',
    `case_result` JSON DEFAULT NULL COMMENT '每个测试点的详细结果JSON',

    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`submit_id`),
    KEY `idx_problem_id` (`problem_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_contest_id` (`contest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码提交记录表';


-- ==================================================================================
-- 5. 竞赛管理模块 (核心业务)
-- ==================================================================================

-- 5.1 竞赛表
DROP TABLE IF EXISTS `tb_contest`;
CREATE TABLE `tb_contest` (
    `contest_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `title` VARCHAR(200) NOT NULL COMMENT '竞赛标题',
    `description` TEXT COMMENT '竞赛描述',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-未开始 1-进行中 2-已结束',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `create_by` BIGINT COMMENT '创建人',
    `update_by` BIGINT COMMENT '更新人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`contest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞赛表';

-- 5.2 竞赛-题目关联表
DROP TABLE IF EXISTS `tb_contest_problem`;
CREATE TABLE `tb_contest_problem` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `problem_id` BIGINT NOT NULL COMMENT '题目ID',
    `display_id` VARCHAR(10) NOT NULL COMMENT '展示编号(A,B,C...)',
    `display_title` VARCHAR(200) DEFAULT NULL COMMENT '竞赛中展示的标题(可选)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_contest_problem` (`contest_id`, `problem_id`),
    UNIQUE KEY `uk_contest_display` (`contest_id`, `display_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞赛题目关联表';

-- 5.3 竞赛报名表
DROP TABLE IF EXISTS `tb_contest_registration`;
CREATE TABLE `tb_contest_registration` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `contest_id` BIGINT NOT NULL COMMENT '竞赛ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_contest_user` (`contest_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞赛报名表';


-- ==================================================================================
-- 6. 初始化基础数据
-- ==================================================================================

-- 6.1 插入标签
INSERT INTO `tb_problem_tag` (`tag_id`, `tag_name`, `tag_color`) VALUES
(1, '数组', '#1890ff'),
(2, '动态规划', '#faad14'),
(3, '数学', '#52c41a'),
(4, '哈希表', '#f5222d');

-- 6.2 插入示例题目
INSERT INTO `tb_problem` (`problem_id`, `title`, `difficulty`, `description`, `submit_num`, `accepted_num`, `time_limit`, `memory_limit`, `sample_input`, `sample_output`, `create_by`) VALUES
(1001, '两数之和', 1, '给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。', 100, 50, 1000, 128, 'nums = [2,7,11,15], target = 9', '[0,1]', 1),
(1002, '最长递增子序列', 2, '给你一个整数数组 nums ，找到其中最长严格递增子序列的长度。', 20, 5, 1000, 128, 'nums = [10,9,2,5,3,7,101,18]', '4', 1);

-- 6.3 插入题目-标签关联
INSERT INTO `tb_problem_tag_relation` (`problem_id`, `tag_id`) VALUES
(1001, 1), -- 两数之和 -> 数组
(1001, 4), -- 两数之和 -> 哈希表
(1002, 1), -- LIS -> 数组
(1002, 2); -- LIS -> 动态规划

-- 6.4 插入示例用例 (仅供测试)
INSERT INTO `tb_test_case` (`case_id`, `problem_id`, `input`, `output`) VALUES
(1, 1001, '2 7 11 15\n9', '0 1'),
(2, 1001, '3 2 4\n6', '1 2');