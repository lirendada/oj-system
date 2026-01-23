/*
 Navicat Premium Dump SQL

 Source Server         : 腾讯云
 Source Server Type    : MySQL
 Source Server Version : 80045 (8.0.45)
 Source Host           : 49.235.136.223:3306
 Source Schema         : oj_system

 Target Server Type    : MySQL
 Target Server Version : 80045 (8.0.45)
 File Encoding         : 65001

 Date: 23/01/2026 16:54:43
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for tb_contest
-- ----------------------------
DROP TABLE IF EXISTS `tb_contest`;
CREATE TABLE `tb_contest`  (
  `contest_id` bigint NOT NULL COMMENT '竞赛ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '竞赛标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '竞赛描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-未开始 1-进行中 2-已结束',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `update_by` bigint NULL DEFAULT NULL COMMENT '更新人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`contest_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '竞赛表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_contest_problem
-- ----------------------------
DROP TABLE IF EXISTS `tb_contest_problem`;
CREATE TABLE `tb_contest_problem`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `contest_id` bigint NOT NULL COMMENT '竞赛ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `display_id` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示编号(A,B,C...)',
  `display_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '竞赛中展示的标题(可选)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_contest_problem`(`contest_id` ASC, `problem_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_contest_display`(`contest_id` ASC, `display_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2007650817414152213 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '竞赛题目关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_contest_registration
-- ----------------------------
DROP TABLE IF EXISTS `tb_contest_registration`;
CREATE TABLE `tb_contest_registration`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `contest_id` bigint NOT NULL COMMENT '竞赛ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_contest_user`(`contest_id` ASC, `user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '竞赛报名表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_problem
-- ----------------------------
DROP TABLE IF EXISTS `tb_problem`;
CREATE TABLE `tb_problem`  (
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标题',
  `difficulty` tinyint NOT NULL COMMENT '难度：1-简单 2-中等 3-困难',
  `submit_num` int NOT NULL DEFAULT 0 COMMENT '提交总数',
  `accepted_num` int NOT NULL DEFAULT 0 COMMENT '通过总数',
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目描述(支持Markdown)',
  `input_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输入描述',
  `output_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '输出描述',
  `time_limit` int NOT NULL DEFAULT 1000 COMMENT '时间限制(ms)',
  `memory_limit` int NOT NULL DEFAULT 128 COMMENT '内存限制(MB)',
  `stack_limit` int NULL DEFAULT 128 COMMENT '栈限制(MB)',
  `sample_input` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '样例输入(展示用)',
  `sample_output` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '样例输出(展示用)',
  `hint` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '提示',
  `source` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-隐藏 1-正常',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `update_by` bigint NULL DEFAULT NULL COMMENT '更新人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`problem_id`) USING BTREE,
  INDEX `idx_difficulty`(`difficulty` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题目表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_problem_tag
-- ----------------------------
DROP TABLE IF EXISTS `tb_problem_tag`;
CREATE TABLE `tb_problem_tag`  (
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `tag_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `tag_color` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签颜色(如#FF0000)',
  PRIMARY KEY (`tag_id`) USING BTREE,
  UNIQUE INDEX `uk_tag_name`(`tag_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题目标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_problem_tag_relation
-- ----------------------------
DROP TABLE IF EXISTS `tb_problem_tag_relation`;
CREATE TABLE `tb_problem_tag_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_problem_tag`(`problem_id` ASC, `tag_id` ASC) USING BTREE,
  INDEX `idx_tag_id`(`tag_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 35 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题目标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_solution
-- ----------------------------
DROP TABLE IF EXISTS `tb_solution`;
CREATE TABLE `tb_solution`  (
  `solution_id` bigint NOT NULL COMMENT '题解ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `user_id` bigint NOT NULL COMMENT '发布用户ID',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题解标题',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题解内容(Markdown)',
  `cover` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '封面图',
  `visit_count` int NULL DEFAULT 0 COMMENT '浏览量',
  `like_count` int NULL DEFAULT 0 COMMENT '点赞数',
  `reply_count` int NULL DEFAULT 0 COMMENT '评论数',
  `type` tinyint NULL DEFAULT 0 COMMENT '类型：0-用户题解 1-官方题解',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：0-草稿 1-发布 2-下架',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`solution_id`) USING BTREE,
  INDEX `idx_problem_id`(`problem_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '题解表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_submit_record
-- ----------------------------
DROP TABLE IF EXISTS `tb_submit_record`;
CREATE TABLE `tb_submit_record`  (
  `submit_id` bigint NOT NULL COMMENT '提交ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `contest_id` bigint NULL DEFAULT 0 COMMENT '竞赛ID (0表示非竞赛提交)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `code` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交的代码',
  `language` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编程语言(Java/C++/Python)',
  `status` tinyint NOT NULL DEFAULT 10 COMMENT '判题状态: 10-待判题, 20-判题中, 30-结束',
  `judge_result` tinyint NULL DEFAULT NULL COMMENT '判题结果: 0-AC, 1-WA, 2-TLE, 3-MLE, 4-RE, 5-CE...',
  `time_cost` int NULL DEFAULT NULL COMMENT '最大耗时(ms)',
  `memory_cost` int NULL DEFAULT NULL COMMENT '最大内存(KB)',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息(编译错误/运行错误)',
  `case_result` json NULL COMMENT '每个测试点的详细结果JSON',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `score` int NULL DEFAULT 0 COMMENT '得分',
  `pass_case_count` int NULL DEFAULT 0 COMMENT '通过用例数',
  `total_case_count` int NULL DEFAULT 0 COMMENT '总用例数',
  PRIMARY KEY (`submit_id`) USING BTREE,
  INDEX `idx_problem_id`(`problem_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_contest_id`(`contest_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码提交记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_sys_user
-- ----------------------------
DROP TABLE IF EXISTS `tb_sys_user`;
CREATE TABLE `tb_sys_user`  (
  `user_id` bigint NOT NULL COMMENT '管理员ID',
  `user_account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '昵称',
  `create_by` bigint NULL DEFAULT NULL COMMENT '创建人',
  `update_by` bigint NULL DEFAULT NULL COMMENT '更新人',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `uk_user_account`(`user_account` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统管理员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_test_case
-- ----------------------------
DROP TABLE IF EXISTS `tb_test_case`;
CREATE TABLE `tb_test_case`  (
  `case_id` bigint NOT NULL COMMENT '测试用例ID',
  `problem_id` bigint NOT NULL COMMENT '题目ID',
  `input` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '输入数据(大文本)',
  `output` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '期望输出(大文本)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`case_id`) USING BTREE,
  INDEX `idx_problem_id`(`problem_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '测试用例表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tb_user
-- ----------------------------
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `user_account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像URL',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `school` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '学校',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：0-禁用 1-正常',
  `submitted_count` int NULL DEFAULT 0 COMMENT '提交次数',
  `accepted_count` int NULL DEFAULT 0 COMMENT '通过次数',
  `rating` int NULL DEFAULT 1500 COMMENT 'Rating分数',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  `password_version` bigint NULL DEFAULT 0 COMMENT '密码版本号',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `uk_user_account`(`user_account` ASC) USING BTREE,
  UNIQUE INDEX `uk_email`(`email` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'C端普通用户表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
