package cn.ling.utils;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import java.util.Properties;

/**
 * 邮件发送工具类
 * 提供基于Spring Mail的邮件发送功能，支持SMTP协议和SSL加密
 * 通过配置文件自动注入邮件服务器相关参数，实现邮件的统一发送管理
 */

@Slf4j // 启用SLF4J日志功能
@Component
@Data
@ConfigurationProperties(prefix = "email")
public class EmailUtils {
    /**
     * 邮件服务器主机地址
     */
    private String host;

    /**
     * 邮件服务器端口号
     */
    private Integer port;

    /**
     * 邮件服务器用户名
     */
    private String username;

    /**
     * 邮件服务器密码（SMTP授权码）
     */
    private String password;

    /**
     * 发件人邮箱地址
     */
    private String from;

    /**
     * Spring邮件发送器实例
     */
    private JavaMailSenderImpl mailSender;

    /**
     * 初始化邮件发送器
     * 在Bean创建完成后自动调用，配置SMTP连接参数
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化邮件发送器");

        try {
            mailSender = new JavaMailSenderImpl();

            // 配置邮件服务器属性
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp"); // 设置协议为SMTP
            props.put("mail.smtp.auth", "true"); // 开启SMTP认证
            props.put("mail.smtp.ssl.enable", "true"); // 开启SSL加密传输
            props.put("mail.debug", "false"); // 关闭调试模式（生产环境建议关闭）

            // 设置邮件服务器连接参数
            mailSender.setHost(host);
            mailSender.setPort(port);
            mailSender.setUsername(username);
            mailSender.setPassword(password);

            log.info("邮件发送器初始化成功 - 服务器: {}:{} - 用户：{}", host, port, username);
        } catch (Exception e) {
            log.error("邮件发送器初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("邮件发送器初始化失败", e);
        }
    }

    /**
     * 发送简单文本邮件
     * 发送纯文本格式的邮件到指定的收件人
     *
     * @param to 收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件内容（纯文本）
     * @return 发送成功返回true，失败返回false
     */
    public Boolean sendEmail(String to, String subject, String content) {
        log.info("开始发送邮件 - 收件人: {}, 主题: {}", to, subject);

        try {
            // 参数校验
            if (to == null || to.trim().isEmpty()) {
                log.error("邮件发送失败：收件人地址不能为空");
                return false;
            }

            if (subject == null) {
                subject = "无主题";
                log.debug("邮件主题为空，使用默认主题: {}", subject);
            }

            if (content == null) {
                content = "";
                log.debug("邮件内容为空，发送空内容邮件");
            }

            // 创建邮件消息对象
            SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
            simpleMailMessage.setFrom(from); // 设置发件人
            simpleMailMessage.setTo(to); // 设置收件人
            simpleMailMessage.setSubject(subject); // 设置邮件主题
            simpleMailMessage.setText(content); // 设置邮件内容

            log.debug("邮件信息配置完成，开始发送 - 发件人: {}, 收件人: {}, 主题: {}", from, to, subject);

            // 发送邮件
            mailSender.send(simpleMailMessage);

            log.info("邮件发送成功 - 收件人: {}, 主题: {}, 发件人: {}", to, subject, from);
            return true;

        } catch (Exception e) {
            log.error("邮件发送失败 - 收件人: {}, 主题: {}, 错误信息: {}", to, subject, e.getMessage(), e);
            return false;
        }
    }
}
