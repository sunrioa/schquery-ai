package cn.ling.rpc;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.ForestClient;
import com.dtflys.forest.annotation.Post;
import com.dtflys.forest.annotation.Body;
import lombok.Data;

/**
 * 文档重排序远程调用接口
 * 使用Forest HTTP客户端框架调用阿里云通义千问文本重排序服务
 * 对检索到的文档进行相关性重新排序，提高RAG检索质量
 * Rerank功能可以在向量相似度搜索后，进一步精排最相关的文档
 */
@ForestClient  // 标识该接口为Forest客户端
@BaseRequest(
        baseURL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank",  // 阿里云重排序服务地址
        headers = {
                "Authorization: Bearer ${DASHSCOPE_API_KEY}",  // API访问密钥
                "Content-Type: application/json"  // 请求内容类型
        }
)
public interface RerankRpc {

    /**
     * 重排序请求对象
     * 封装发送给阿里云服务的重排序请求数据结构
     */
    @Data
    class RerankRequest {
        /** 重排序模型名称，默认使用qwen3-rerank */
        private String model = "qwen3-rerank";
        /** 输入数据，包含查询和待排序文档 */
        private Input input;
        /** 重排序参数配置 */
        private Parameters parameters;

        /**
         * 输入数据对象
         * 包含用户查询和待重排序的文档列表
         */
        @Data
        public static class Input {
            /** 用户查询文本 */
            private String query;
            /** 待重排序的文档文本数组 */
            private String[] documents;

            public Input(String query, String[] documents) {
                this.query = query;
                this.documents = documents;
            }
        }

        /**
         * 重排序参数配置
         * 控制重排序的输出结果格式和数量
         */
        @Data
        public static class Parameters {
            /** 是否返回文档内容 */
            private Boolean return_documents;
            /** 返回的最相关文档数量 */
            private Integer top_n;
            /** 自定义指令，用于引导重排序策略 */
            private String instruct;

            public Parameters(Boolean return_documents, Integer top_n, String instruct) {
                this.return_documents = return_documents;
                this.top_n = top_n;
                this.instruct = instruct;
            }
        }
    }

    /**
     * 重排序响应对象
     * 封装阿里云服务返回的重排序结果数据结构
     */
    @Data
    class RerankResponse {
        /** 输出结果 */
        private Output output;
        /** Token使用情况 */
        private Usage usage;
        /** 请求ID */
        private String request_id;
        /** 响应代码 */
        private String code;
        /** 响应消息 */
        private String message;

        /**
         * 输出结果对象
         * 包含重排序后的文档列表
         */
        @Data
        public static class Output {
            /** 重排序结果数组 */
            private Result[] results;
        }

        /**
         * 单个重排序结果
         * 包含文档内容、索引和相关性分数
         */
        @Data
        public static class Result {
            /** 文档对象 */
            private Document document;
            /** 文档在原始列表中的索引 */
            private Integer index;
            /** 相关性分数，值越大表示与查询越相关 */
            private Double relevance_score;
        }

        /**
         * 文档对象
         * 包含文档的文本内容
         */
        @Data
        public static class Document {
            /** 文档文本内容 */
            private String text;
        }

        /**
         * Token使用情况
         * 统计本次请求的Token消耗
         */
        @Data
        public static class Usage {
            /** 总Token数 */
            private Integer total_tokens;
        }
    }

    /**
     * 调用文档重排序接口
     * 发送POST请求到阿里云服务，进行文档重排序
     * 
     * @param request 重排序请求对象
     * @return 重排序响应对象，包含按相关性排序后的文档列表
     */
    @Post(
            readTimeout = 30 * 1000,  // 读取超时时间：30秒
            connectTimeout = 5 * 1000  // 连接超时时间：5秒
    )
    RerankResponse rerank(@Body RerankRequest request);
}