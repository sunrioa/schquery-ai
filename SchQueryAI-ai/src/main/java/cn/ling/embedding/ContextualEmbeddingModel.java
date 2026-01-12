package cn.ling.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 支持按 ThreadLocal 上下文覆盖 embedding model 的 EmbeddingModel 包装器。
 * <p>
 * 用途：
 * - 知识库（knowledge_info.embedding_model_name）希望影响向量化/检索时的 embedding 模型选择
 * - QdrantVectorStore 默认不会感知 knowledgeId，需要通过上下文传递到 EmbeddingModel.call()
 */
public class ContextualEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;

    public ContextualEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Embedding API 的最大批量大小限制
     */
    private static final int MAX_BATCH_SIZE = 10;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        String modelName = EmbeddingModelContext.getModelName();
        if (request == null) {
            return delegate.call(request);
        }

        List<String> instructions = request.getInstructions();
        if (instructions == null || instructions.isEmpty()) {
            return delegate.call(request);
        }

        EmbeddingOptions prev = request.getOptions();
        EmbeddingOptions options = StringUtils.hasText(modelName)
                ? EmbeddingOptionsBuilder.builder()
                    .withModel(modelName.trim())
                    .withDimensions(prev == null ? null : prev.getDimensions())
                    .build()
                : prev;

        // 如果批量大小不超过限制，直接调用
        if (instructions.size() <= MAX_BATCH_SIZE) {
            EmbeddingRequest overridden = new EmbeddingRequest(instructions, options);
            return delegate.call(overridden);
        }

        // 分批处理，每批最多 MAX_BATCH_SIZE 个
        List<Embedding> allEmbeddings = new ArrayList<>();
        EmbeddingResponseMetadata lastMetadata = null;

        for (int i = 0; i < instructions.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, instructions.size());
            List<String> batch = instructions.subList(i, end);
            EmbeddingRequest batchRequest = new EmbeddingRequest(batch, options);
            EmbeddingResponse batchResponse = delegate.call(batchRequest);

            // 合并 embeddings，并调整 index
            for (Embedding embedding : batchResponse.getResults()) {
                allEmbeddings.add(new Embedding(embedding.getOutput(), allEmbeddings.size()));
            }
            lastMetadata = batchResponse.getMetadata();
        }

        return new EmbeddingResponse(allEmbeddings, lastMetadata);
    }

    @Override
    public float[] embed(Document document) {
        Assert.notNull(document, "Document must not be null");
        String text = document.getText();
        if (!StringUtils.hasText(text)) {
            return new float[0];
        }
        return this.embed(text);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }
}

