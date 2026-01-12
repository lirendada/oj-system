/*
 Navicat Premium Dump SQL

 Source Server         : 腾讯云
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : lirendada.art:3306
 Source Schema         : oj_system

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 12/01/2026 15:01:51
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tb_contest
-- ----------------------------
DROP TABLE IF EXISTS `tb_contest`;
CREATE TABLE `tb_contest` (
  `contest_id` bigint NOT NULL COMMENT '竞赛ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '竞赛标题',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '竞赛描述',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-未开始 1-进行中 2-已结束',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`contest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞赛表';

-- ----------------------------
-- Table structure for tb_contest_problem
-- ----------------------------
DROP TABLE IF EXISTS `tb_contest_problem`;
CREATE TABLE `tb_contest_problem` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `contest_id` bigint NOT NULL COMMENT '竞赛ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `display_id` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示编号(A,B,C...)',
  `display_title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '竞赛中展示的标题(可选)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_problem` (`contest_id`,`problem_id`),
  UNIQUE KEY `uk_contest_display` (`contest_id`,`display_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2007650817414152203 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞赛题目关联表';

-- ----------------------------
-- Table structure for tb_contest_registration
-- ----------------------------
DROP TABLE IF EXISTS `tb_contest_registration`;
CREATE TABLE `tb_contest_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `contest_id` bigint NOT NULL COMMENT '竞赛ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_user` (`contest_id`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞赛报名表';

-- ----------------------------
-- Table structure for tb_problem
-- ----------------------------
DROP TABLE IF EXISTS `tb_problem`;
CREATE TABLE `tb_problem` (
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
  `difficulty` tinyint NOT NULL COMMENT '难度：1-简单 2-中等 3-困难',
  `submit_num` int NOT NULL DEFAULT '0' COMMENT '提交总数',
  `accepted_num` int NOT NULL DEFAULT '0' COMMENT '通过总数',
  `description` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目描述(支持Markdown)',
  `input_description` text COLLATE utf8mb4_unicode_ci COMMENT '输入描述',
  `output_description` text COLLATE utf8mb4_unicode_ci COMMENT '输出描述',
  `time_limit` int NOT NULL DEFAULT '1000' COMMENT '时间限制(ms)',
  `memory_limit` int NOT NULL DEFAULT '128' COMMENT '内存限制(MB)',
  `stack_limit` int DEFAULT '128' COMMENT '栈限制(MB)',
  `sample_input` text COLLATE utf8mb4_unicode_ci COMMENT '样例输入(展示用)',
  `sample_output` text COLLATE utf8mb4_unicode_ci COMMENT '样例输出(展示用)',
  `hint` text COLLATE utf8mb4_unicode_ci COMMENT '提示',
  `source` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-隐藏 1-正常',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`problem_id`),
  KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';

-- ----------------------------
-- Table structure for tb_problem_tag
-- ----------------------------
DROP TABLE IF EXISTS `tb_problem_tag`;
CREATE TABLE `tb_problem_tag` (
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `tag_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `tag_color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签颜色(如#FF0000)',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签表';

-- ----------------------------
-- Table structure for tb_problem_tag_relation
-- ----------------------------
DROP TABLE IF EXISTS `tb_problem_tag_relation`;
CREATE TABLE `tb_problem_tag_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_tag` (`problem_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签关联表';

-- ----------------------------
-- Table structure for tb_solution
-- ----------------------------
DROP TABLE IF EXISTS `tb_solution`;
CREATE TABLE `tb_solution` (
  `solution_id` bigint NOT NULL COMMENT '题解ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `user_id` bigint NOT NULL COMMENT '发布用户ID',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题解标题',
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题解内容(Markdown)',
  `cover` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面图',
  `visit_count` int DEFAULT '0' COMMENT '浏览量',
  `like_count` int DEFAULT '0' COMMENT '点赞数',
  `reply_count` int DEFAULT '0' COMMENT '评论数',
  `type` tinyint DEFAULT '0' COMMENT '类型：0-用户题解 1-官方题解',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-草稿 1-发布 2-下架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`solution_id`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题解表';

-- ----------------------------
-- Table structure for tb_submit_record
-- ----------------------------
DROP TABLE IF EXISTS `tb_submit_record`;
CREATE TABLE `tb_submit_record` (
  `submit_id` bigint NOT NULL COMMENT '提交ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `contest_id` bigint DEFAULT '0' COMMENT '竞赛ID (0表示非竞赛提交)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `code` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交的代码',
  `language` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编程语言(Java/C++/Python)',
  `status` tinyint NOT NULL DEFAULT '10' COMMENT '判题状态: 10-待判题, 20-判题中, 30-结束',
  `judge_result` tinyint DEFAULT NULL COMMENT '判题结果: 0-AC, 1-WA, 2-TLE, 3-MLE, 4-RE, 5-CE...',
  `time_cost` int DEFAULT NULL COMMENT '最大耗时(ms)',
  `memory_cost` int DEFAULT NULL COMMENT '最大内存(KB)',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息(编译错误/运行错误)',
  `case_result` json DEFAULT NULL COMMENT '每个测试点的详细结果JSON',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `score` int DEFAULT '0' COMMENT '得分',
  `pass_case_count` int DEFAULT '0' COMMENT '通过用例数',
  `total_case_count` int DEFAULT '0' COMMENT '总用例数',
  PRIMARY KEY (`submit_id`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_contest_id` (`contest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码提交记录表';

-- ----------------------------
-- Table structure for tb_sys_user
-- ----------------------------
DROP TABLE IF EXISTS `tb_sys_user`;
CREATE TABLE `tb_sys_user` (
  `user_id` bigint NOT NULL COMMENT '管理员ID',
  `user_account` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `nick_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_account` (`user_account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统管理员表';

-- ----------------------------
-- Table structure for tb_test_case
-- ----------------------------
DROP TABLE IF EXISTS `tb_test_case`;
CREATE TABLE `tb_test_case` (
  `case_id` bigint NOT NULL COMMENT '测试用例ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `input` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '输入数据(大文本)',
  `output` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '期望输出(大文本)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`case_id`),
  KEY `idx_problem_id` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例表';

-- ----------------------------
-- Table structure for tb_user
-- ----------------------------
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `user_account` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `nick_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `school` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学校',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-禁用 1-正常',
  `submitted_count` int DEFAULT '0' COMMENT '提交次数',
  `accepted_count` int DEFAULT '0' COMMENT '通过次数',
  `rating` int DEFAULT '1500' COMMENT 'Rating分数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_account` (`user_account`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端普通用户表';

SET FOREIGN_KEY_CHECKS = 1;
