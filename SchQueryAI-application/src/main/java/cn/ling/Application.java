package cn.ling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SchQueryAI 应用程序主入口类
 * 这是整个Spring Boot应用程序的启动点，负责初始化和启动应用上下文
 */
@Slf4j // 启用SLF4J日志功能
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class Application {

    /**
     * 应用程序主方法
     * Spring Boot应用程序的入口点，负责启动整个应用
     *
     * @param args 命令行参数，可用于配置应用启动时的各种选项
     */
    public static void main(String[] args) {
        log.info("正在启动SchQueryAI应用程序...");

        try {
            // 启动Spring Boot应用
            SpringApplication.run(Application.class, args);
            log.info("SchQueryAI应用程序启动成功！");
        } catch (Exception e) {
            log.error("SchQueryAI应用程序启动失败: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
