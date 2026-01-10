package com.liren.api.problem.dto.problem;

import lombok.Data;

/**
 * 题目提交后交给判题机的更新信息
 */
@Data
public class ProblemSubmitUpdateDTO {
    private Long submitId;

    /**
     * 判题状态 (对应 SubmitStatusEnum)
     * 通常回写时都是 30-SUCCEED (流程结束)
     */
    private Integer status;

    /**
     * 判题结果 (对应 JudgeResultEnum: AC/WA/TLE...)
     */
    private Integer judgeResult;

    private Integer timeCost; // 耗时 (ms)
    private Integer memoryCost; // 内存消耗 (KB)

    /**
     * 对应 Entity 中的 errorMessage
     * 如果是 CE/RE，这里存详细报错
     */
    private String errorMessage;

    private Integer passCaseCount;  // 通过用例数
    private Integer totalCaseCount; // 总用例数
}
