package com.liren.user.service;

public interface IMailService {
    /**
     * 发送简单文本邮件
     * @param to 接收者邮箱
     * @param subject 主题
     * @param content 内容
     */
    void sendSimpleMail(String to, String subject, String content);
}