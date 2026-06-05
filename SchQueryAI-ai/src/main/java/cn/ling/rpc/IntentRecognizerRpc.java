package cn.ling.rpc;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.ForestClient;
import com.dtflys.forest.annotation.Header;
import com.dtflys.forest.annotation.Post;
import com.dtflys.forest.annotation.Body;
import lombok.Data;
import java.util.Map;

/**
 * 意图识别远程调用接口
 * 使用Forest HTTP客户端框架调用阿里云通义千问意图识别服务
 * 将用户输入分类为不同的业务意图类别，支持智能问答路由
 */
@ForestClient  // 标识该接口为Forest客户端，Forest框架会自动生成实现类处理HTTP请求
@BaseRequest(
        baseURL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",  // 阿里云通义千问意图识别接口地址
        headers = {
                "Content-Type: application/json"  // 请求内容类型
        }
)
public interface IntentRecognizerRpc {

    /**
     * 意图识别请求对象
     * 封装发送给阿里云服务的请求数据结构
     */
    @Data
    class IntentRequest {
        /** 意图识别模型名称 */
        private String model = "tongyi-intent-detect-v3";
        /** 对话消息数组，包含系统提示词和用户输入 */
        private Message[] messages;

        /**
         * 对话消息对象
         * 表示一条对话消息，包含角色和内容
         */
        @Data
        public static class Message {
            /** 消息角色：system(系统)或user(用户) */
            private String role;
            /** 消息内容 */
            private String content;

            public Message(String role, String content) {
                this.role = role;
                this.content = content;
            }
        }

        public IntentRequest(Message[] messages) {
            this.messages = messages;
        }
    }

    /**
     * 意图识别响应对象
     * 封装阿里云服务返回的识别结果数据结构
     */
    @Data
    class IntentResponse {
        /** 请求ID */
        private String id;
        /** 对象类型 */
        private String object;
        /** 创建时间戳 */
        private String created;
        /** 使用的模型名称 */
        private String model;
        /** 识别结果选项数组 */
        private Choice[] choices;
        /** Token使用情况 */
        private Usage usage;

        /**
         * 识别结果选项
         * 包含单个识别结果的详细信息
         */
        @Data
        public static class Choice {
            /** 结果索引 */
            private Integer index;
            /** 返回的消息对象 */
            private Message message;
            /** 结束原因（stop/length等） */
            private String finish_reason;

            /**
             * 响应消息对象
             * 包含意图识别结果
             */
            @Data
            public static class Message {
                /** 消息角色 */
                private String role;
                /** 识别到的意图内容 */
                private String content;
            }
        }

        /**
         * Token使用情况
         * 统计本次请求的Token消耗
         */
        @Data
        public static class Usage {
            /** 提示词Token数 */
            private Integer prompt_tokens;
            /** 完成Token数 */
            private Integer completion_tokens;
            /** 总 Token数 */
            private Integer total_tokens;
        }
    }

    /**
     * 调用意图识别接口
     * 发送POST请求到阿里云服务，进行意图识别
     * 
     * @param request 意图识别请求对象
     * @return 意图识别响应对象
     */
    @Post(
            readTimeout = 30 * 1000,  // 读取超时时间：30秒
            connectTimeout = 5 * 1000  // 连接超时时间：5秒
    )
    IntentResponse recognize(@Header("Authorization") String authorization, @Body IntentRequest request);

    /**
     * 获取用户意图
     * 封装意图识别的完整流程，包括构造请求、调用API、解析响应
     * 支持多种意图类别映射，将意图代码转换为业务意图名称
     * 
     * @param intentMap 意图映射表，键为意图代码，值为意图名称
     * @param userInput 用户输入文本
     * @return 识别到的意图名称，识别失败返回"UNKNOWN"
     */
    default String getIntent(Map<String,String> intentMap, String userInput, String authorization){
        try {
            // 构造系统提示词，告诉模型需要从哪些意图中选择
            String systemPrompt = String.format(
                    """
                            You are Qwen, created by Alibaba Cloud. You are a helpful assistant.
                            You should choose one tag from the tag list:
                            %s
                            Just reply with the chosen tag.""",
                intentMap.toString()
            );

            // 创建消息数组：系统提示词 + 用户输入
            IntentRequest.Message[] messages = new IntentRequest.Message[]{
                new IntentRequest.Message("system", systemPrompt),
                new IntentRequest.Message("user", userInput)
            };

            // 创建请求对象
            IntentRequest intentRequest = new IntentRequest(messages);

            // 调用意图识别API
            IntentResponse response = recognize(authorization, intentRequest);

            // 安全解析响应结果
            if (response != null && response.getChoices() != null && response.getChoices().length > 0) {
                String content = response.getChoices()[0].getMessage().getContent();
                return parseIntentResponse(content, intentMap);
            }

            // 响应为空或无有效选项，返回未知意图
            return "UNKNOWN";
        } catch (Exception e) {
            // 意图识别异常，返回默认值
            return "UNKNOWN";
        }
    }

    /**
     * 解析意图识别响应
     * 将模型返回的意图代码或描述转换为标准的业务意图名称
     * 支持多种匹配策略：直接匹配、完整匹配、模糊匹配
     * 
     * @param content 模型返回的内容
     * @param intentMap 意图映射表
     * @return 解析后的意图名称
     */
    private String parseIntentResponse(String content, Map<String, String> intentMap) {
        if (content == null || content.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String trimmedContent = content.trim();

        // 1. 直接匹配单个字符的意图代码（如"Q", "R"等）
        if (trimmedContent.length() == 1 && intentMap.containsKey(trimmedContent)) {
            return intentMap.get(trimmedContent);
        }

        // 2. 匹配完整的意图描述（如"专业信息", "招生计划"等）
        if (intentMap.containsValue(trimmedContent)) {
            return trimmedContent;
        }

        // 3. 尝试从复杂响应中提取意图（模糊匹配）
        for (Map.Entry<String, String> entry : intentMap.entrySet()) {
            if (trimmedContent.contains(entry.getKey()) || trimmedContent.contains(entry.getValue())) {
                return entry.getValue();
            }
        }

        // 所有匹配策略均失败，返回未知意图
        return "UNKNOWN";
    }
}
