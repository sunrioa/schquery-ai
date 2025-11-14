package cn.ling.rpc;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.ForestClient;
import com.dtflys.forest.annotation.Post;
import com.dtflys.forest.annotation.Body; // 注意：是 Forest 的 @Body
import lombok.Data;

@ForestClient
// 基础 URL 直接写完整接口地址，无需后续拼接
@BaseRequest(
        baseURL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank",
        headers = {
                "Authorization: Bearer ${DASHSCOPE_API_KEY}",
                "Content-Type: application/json"
        }
)
public interface RerankRpc {

    @Data
    class RerankRequest {
        // 固定模型名，可默认赋值，避免调用时遗漏
        private String model = "qwen3-rerank";
        private Input input;
        private Parameters parameters;

        @Data
        public static class Input {
            private String query;
            // 用 List<String> 或 String[] 都可以，Forest 会自动序列化
            private String[] documents;

            public Input(String query, String[] documents) {
                this.query = query;
                this.documents = documents;
            }
        }

        @Data
        public static class Parameters {
            private Boolean return_documents;
            private Integer top_n;
            private String instruct;

            public Parameters(Boolean return_documents, Integer top_n, String instruct) {
                this.return_documents = return_documents;
                this.top_n = top_n;
                this.instruct = instruct;
            }
        }
    }

    @Data
    class RerankResponse {
        private Output output;
        private Usage usage;
        private String request_id;
        private String code;
        private String message;

        @Data
        public static class Output {
            private Result[] results;
        }

        @Data
        public static class Result {
            private Document document;
            private Integer index;
            private Double relevance_score; // API 返回的相关性分数字段，正确
        }

        @Data
        public static class Document {
            private String text; // 对应返回的文档内容，正确
        }

        @Data
        public static class Usage {
            private Integer total_tokens;
        }
    }

    // 1. url 留空（复用 BaseRequest 的完整地址）；2. 用 @Body 注解请求体
    @Post(
            readTimeout = 30 * 1000,
            connectTimeout = 5 * 1000
    )
    RerankResponse rerank(@Body RerankRequest request);
}