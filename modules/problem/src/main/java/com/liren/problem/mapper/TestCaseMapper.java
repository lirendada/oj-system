package com.liren.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liren.problem.entity.TestCaseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TestCaseMapper extends BaseMapper<TestCaseEntity> {
    /**
     * 批量保存测试用例
     */
    void saveBatch(@Param("testCaseEntities") List<TestCaseEntity> testCaseEntities);
}
