package cn.ling.rpc;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.ForestClient;
import com.dtflys.forest.annotation.Post;
import com.dtflys.forest.annotation.Body;
import lombok.Data;
import java.util.Map;

@ForestClient
// 基础 URL 直接写完整接口地址，无需后续拼接
@BaseRequest(
        baseURL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        headers = {
                "Authorization: Bearer ${DASHSCOPE_API_KEY}",
                "Content-Type: application/json"
        }
)
public interface IntentRecognizerRpc {

    @Data
    class IntentRequest {
        private String model = "tongyi-intent-detect-v3";
        private Message[] messages;

        @Data
        public static class Message {
            private String role;
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

    @Data
    class IntentResponse {
        private String id;
        private String object;
        private String created;
        private String model;
        private Choice[] choices;
        private Usage usage;

        @Data
        public static class Choice {
            private Integer index;
            private Message message;
            private String finish_reason;

            @Data
            public static class Message {
                private String role;
                private String content;
            }
        }

        @Data
        public static class Usage {
            private Integer prompt_tokens;
            private Integer completion_tokens;
            private Integer total_tokens;
        }
    }

    @Post(
            readTimeout = 30 * 1000,
            connectTimeout = 5 * 1000
    )
    IntentResponse recognize(@Body IntentRequest request);

    default String getIntent(Map<String,String> intentMap,String userInput){
        try {
            // 构建系统提示词
            String systemPrompt = String.format(
                    """
                            You are Qwen, created by Alibaba Cloud. You are a helpful assistant.
                            You should choose one tag from the tag list:
                            %s
                            Just reply with the chosen tag.""",
                intentMap.toString()
            );


            // 创建消息数组
            IntentRequest.Message[] messages = new IntentRequest.Message[]{
                new IntentRequest.Message("system", systemPrompt),
                new IntentRequest.Message("user", userInput)
            };

            // 创建请求
            IntentRequest intentRequest = new IntentRequest(messages);

            // 调用API
            IntentResponse response = recognize(intentRequest);

            // 安全返回结果
            if (response != null && response.getChoices() != null && response.getChoices().length > 0) {
                String content = response.getChoices()[0].getMessage().getContent();
                return parseIntentResponse(content, intentMap);
            }

            return "UNKNOWN";
        } catch (Exception e) {
            // 异常处理，返回默认值
            return "UNKNOWN";
        }
    }

    /**
     * 解析意图识别响应
     */
    private String parseIntentResponse(String content, Map<String, String> intentMap) {
        if (content == null || content.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String trimmedContent = content.trim();

        // 1. 直接匹配单个字符的意图代码
        if (trimmedContent.length() == 1 && intentMap.containsKey(trimmedContent)) {
            return intentMap.get(trimmedContent);
        }

        // 2. 匹配完整的意图描述
        if (intentMap.containsValue(trimmedContent)) {
            return trimmedContent;
        }

        // 3. 尝试从复杂响应中提取
        for (Map.Entry<String, String> entry : intentMap.entrySet()) {
            if (trimmedContent.contains(entry.getKey()) || trimmedContent.contains(entry.getValue())) {
                return entry.getValue();
            }
        }

        return "UNKNOWN";
    }

}
