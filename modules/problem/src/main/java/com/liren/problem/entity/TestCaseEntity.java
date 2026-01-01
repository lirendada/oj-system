package com.liren.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.liren.common.core.base.BaseTimeEntity;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("tb_test_case")
public class TestCaseEntity extends BaseTimeEntity {
    @TableId(value = "case_id", type = IdType.ASSIGN_ID)
    private Long caseId;

    private Long problemId;

    private String input; // 输入数据

    private String output; // 期望输出
}