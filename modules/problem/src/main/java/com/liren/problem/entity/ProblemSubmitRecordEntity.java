package com.liren.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.liren.common.core.base.BaseTimeEntity;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("tb_submit_record")
public class ProblemSubmitRecordEntity extends BaseTimeEntity {
    @TableId(value = "submit_id", type = IdType.ASSIGN_ID)
    private Long submitId;
    private Long problemId;
    private Long contestId;
    private Long userId;

    private String code;         // 用户提交的代码
    private String language;     // 用户选择的编程语言
    private Integer status;      // 判题状态: 10-待判题, 20-判题中, 30-结束
    private Integer judgeResult; // 判题结果: 0-AC, 1-WA, 2-TLE, 3-MLE, 4-RE, 5-CE...
    private Integer timeCost;    // 最大耗时（ms）
    private Integer memoryCost;  // 最大内存（KB）
    private String errorMessage; // 错误信息
    private String case_result;  // 每个测试点的详细结果（JSON格式）
}
