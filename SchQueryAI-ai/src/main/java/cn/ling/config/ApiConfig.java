package cn.ling.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ApiConfig {
    /**
     * 配置OpenAI API
     * 创建OpenAI API实例，用于与AI模型服务进行通信
     *
     * @param baseUrl API基础地址（从配置文件读取）
     * @param apiKey API访问密钥（从配置文件读取）
     * @return OpenAiApi实例
     */
    @Bean
    public OpenAiApi openAiApi(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey ) {
        log.info("开始配置OpenAI API，基础地址: {}", baseUrl);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        log.info("OpenAI API配置完成，基础地址: {}", baseUrl);
        return openAiApi;
    }
}
