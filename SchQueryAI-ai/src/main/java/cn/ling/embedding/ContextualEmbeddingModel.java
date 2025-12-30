package cn.ling.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        String modelName = EmbeddingModelContext.getModelName();
        if (!StringUtils.hasText(modelName) || request == null) {
            return delegate.call(request);
        }

        EmbeddingOptions prev = request.getOptions();
        EmbeddingOptions options = EmbeddingOptionsBuilder.builder()
                .withModel(modelName.trim())
                .withDimensions(prev == null ? null : prev.getDimensions())
                .build();
        EmbeddingRequest overridden = new EmbeddingRequest(request.getInstructions(), options);
        return delegate.call(overridden);
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

