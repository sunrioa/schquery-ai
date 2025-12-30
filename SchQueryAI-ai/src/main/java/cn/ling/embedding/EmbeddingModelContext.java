package cn.ling.embedding;

import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Embedding 模型选择上下文（线程隔离）
 * - 通过 ThreadLocal 将 “本次向量化/检索要使用的 embedding_model_name” 传递给 EmbeddingModel
 */
public final class EmbeddingModelContext {

    private static final ThreadLocal<String> MODEL_NAME = new ThreadLocal<>();

    private EmbeddingModelContext() {
    }

    public static String getModelName() {
        return MODEL_NAME.get();
    }

    public static <T> T withModel(String modelName, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");

        String prev = MODEL_NAME.get();
        if (StringUtils.hasText(modelName)) {
            MODEL_NAME.set(modelName.trim());
        } else {
            MODEL_NAME.remove();
        }

        try {
            return supplier.get();
        } finally {
            if (StringUtils.hasText(prev)) {
                MODEL_NAME.set(prev);
            } else {
                MODEL_NAME.remove();
            }
        }
    }

    public static void runWithModel(String modelName, Runnable runnable) {
        withModel(modelName, () -> {
            runnable.run();
            return null;
        });
    }
}

