package com.liren.problem;

import com.liren.problem.entity.TestCaseEntity;
import com.liren.problem.mapper.TestCaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class testBatchInsert {
    @Autowired
    private TestCaseMapper testCaseMapper;

    @Test
    public void testBatchInsert() {
        List<TestCaseEntity> testCaseEntities = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestCaseEntity testCaseEntity = new TestCaseEntity();
            testCaseEntity.setProblemId(1L);
            testCaseEntity.setInput("input" + i);
            testCaseEntity.setOutput("output" + i);
            testCaseEntities.add(testCaseEntity);
        }
        testCaseMapper.saveBatch(testCaseEntities);
    }
}
