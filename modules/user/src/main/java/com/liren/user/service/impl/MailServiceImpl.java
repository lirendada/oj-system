package com.liren.user.service.impl;

import com.liren.common.core.result.ResultCode;
import com.liren.user.exception.UserException;
import com.liren.user.service.IMailService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailServiceImpl implements IMailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public void sendSimpleMail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("邮件发送成功: {} -> {}", from, to);
        } catch (Exception e) {
            log.error("邮件发送失败", e);
            throw new UserException(ResultCode.MAIL_SEND_ERROR);
        }
    }
}