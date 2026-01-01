package com.liren.judge.mq;

import com.liren.common.core.constant.Constants;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class JudgeReceiver {

    @RabbitListener(queues = Constants.JUDGE_QUEUE, ackMode = "MANUAL")
    public void receiveJudgeMessage(Long submitId, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到判题任务, submitId: {}", submitId);

        try {
            // === 模拟判题过程 ===
            // 1. 根据 submitId 去查 db (这一步需要 Feign 调用 oj-problem，目前还没写，先 Mock)
            log.info("正在调用沙箱进行判题...");
            Thread.sleep(2000); // 模拟耗时

            // 2. 得到结果: AC
            log.info("判题完成，结果: AC");

            // 3. 更新数据库状态 (也需要调 Feign，先 Mock)
            log.info("已更新数据库状态为: 成功");

            // 4. 手动确认消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("接收到判题任务失败, submitId: {}", submitId, e);
            try {
                // 失败重试或丢弃 (根据业务需求，这里先 requeue=false 丢弃防止死循环)
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
