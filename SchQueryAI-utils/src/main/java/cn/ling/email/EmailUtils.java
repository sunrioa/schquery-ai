package cn.ling.email;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import java.util.Properties;

@Component
@Data
@ConfigurationProperties(prefix = "email")
public class EmailUtils {
    String Host;
    Integer Port;
    String Username;
    String Password;
    String From;
//    String To;
//    String Subject;

    private JavaMailSenderImpl mailSender;

//    @Resource
//    private MailNotifyProperties mailNotifyProperties;

    @PostConstruct
    public void init() {
        mailSender = new JavaMailSenderImpl();
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp"); // 协议
        props.put("mail.smtp.auth", "true"); // 开启认证
        props.put("mail.smtp.ssl.enable", "true"); // 开启SSL加密
        props.put("mail.debug", "false"); // 是否开启调试模式（调试用）
        mailSender.setHost(Host); // 邮件服务器地址（如QQ邮箱SMTP服务器）
        mailSender.setPort(Port); // 端口（SSL端口通常为465）
        mailSender.setUsername(Username); // 发件人邮箱账号
        mailSender.setPassword(Password); // SMTP授权码（非登录密码）
    }

    public Boolean sendEmail(String to,String subject,String content){
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(From);
        simpleMailMessage.setTo(to);
        simpleMailMessage.setSubject(subject);
        simpleMailMessage.setText(content);
        mailSender.send(simpleMailMessage);
        return true;
    }
}
