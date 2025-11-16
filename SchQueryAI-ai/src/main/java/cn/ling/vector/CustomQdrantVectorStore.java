package cn.ling.vector;

import com.google.gson.Gson;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * 自定义Qdrant向量存储类
 * 提供文档的向量化存储、检索和删除功能
 *
 * @author Administrator
 */
@Slf4j
public class CustomQdrantVectorStore {

    private static final String CONTENT_FIELD_NAME = "doc_content"; // 存储文档内容的payload键名
    private final QdrantClient qdrantClient;
    private final EmbeddingModel embeddingModel;
    private final String collectionName;
    private final boolean initializeSchema;
    private static final Gson GSON = new Gson(); // 复用Gson实例，避免重复创建

    public CustomQdrantVectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel, String collectionName, Boolean initializeSchema){
        this.qdrantClient=qdrantClient;
        this.embeddingModel=embeddingModel;
        this.collectionName=collectionName;
        this.initializeSchema=initializeSchema;
        initializeCollection();
    }

    /**
     * 初始化Qdrant集合：若集合不存在则创建，确保向量维度与embedding模型一致
     */
    private void initializeCollection() {
        if (!initializeSchema) {
            log.debug("跳过集合初始化 - 集合名称: {}", collectionName);
            return;
        }
        try {
            log.info("开始初始化Qdrant集合 - 集合名称: {}", collectionName);

            // 检查集合是否已存在
            boolean exists = qdrantClient.listCollectionsAsync().get().contains(collectionName);
            if (!exists) {
                log.info("集合不存在，开始创建新集合 - 集合名称: {}, 向量维度: {}", collectionName, embeddingModel.dimensions());
                // 创建集合：指定向量维度（与embedding模型匹配）和余弦距离
                Collections.VectorParams vectorParams = Collections.VectorParams.newBuilder()
                        .setSize(embeddingModel.dimensions())
                        .setDistance(Collections.Distance.Cosine)
                        .build();
                qdrantClient.createCollectionAsync(collectionName, vectorParams).get();
                log.info("Qdrant集合创建成功 - 集合名称: {}", collectionName);
            } else {
                log.info("Qdrant集合已存在 - 集合名称: {}", collectionName);
            }
        } catch (Exception e) {
            log.error("Qdrant集合初始化失败 - 集合名称: {}, 错误信息: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Qdrant集合初始化失败：" + e.getMessage(), e);
        }
    }

    /**
     * 添加文档到Qdrant，返回每个文档对应的qdrant_point_id（用于同步到MySQL）
     * @param documents 待添加的文档列表（每个文档对应一个拆分后的片段）
     * @return 每个文档在Qdrant中的唯一标识（point_id）
     */
    public List<String> addDocuments(List<Document> documents) {
        Assert.notEmpty(documents, "文档列表不能为空");
        log.info("开始添加文档到Qdrant - 集合名称: {}, 文档数量: {}", collectionName, documents.size());

        try {
            // 1. 生成文档向量（批量调用embedding模型）
            List<String> texts = new ArrayList<>(documents.size());
            for (Document doc : documents) {
                String text = doc.getText();
                Assert.notNull(text, "文档内容不能为空（document.id=" + doc.getId() + "）");
                texts.add(text);
            }
            log.debug("开始生成文档向量 - 集合名称: {}, 文档数量: {}", collectionName, documents.size());
            List<float[]> embeddings = embeddingModel.embed(texts);
            log.debug("文档向量生成完成 - 集合名称: {}, 向量维度: {}", collectionName, embeddings.get(0).length);

            // 2. 构造Qdrant的Point列表，记录每个文档的point_id
            List<Points.PointStruct> points = new ArrayList<>(documents.size());
            List<String> qdrantPointIds = new ArrayList<>(documents.size());

            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                // 生成或使用文档自带的ID作为Qdrant的point_id（确保唯一）
                String pointId = doc.getId();
                qdrantPointIds.add(pointId);

                // 构造Point：包含ID、向量、元数据（含文档内容）
                Points.PointStruct point = Points.PointStruct.newBuilder()
                        .setId(PointIdFactory.id(UUID.fromString(pointId))) // 转换为UUID格式
                        .setVectors(VectorsFactory.vectors(embeddings.get(i))) // 设置向量
                        .putAllPayload(buildPayload(doc)) // 设置元数据和内容
                        .build();
                points.add(point);
            }

            // 3. 写入Qdrant并校验结果
            log.debug("开始写入Qdrant - 集合名称: {}, 点数量: {}", collectionName, points.size());
            Points.UpdateResult result = qdrantClient.upsertAsync(collectionName, points).get();
            if (result.getStatus() != Points.UpdateStatus.Completed) {
                log.error("文档添加失败 - 集合名称: {}, Qdrant状态: {}", collectionName, result.getStatus());
                throw new RuntimeException("文档添加失败，Qdrant状态：" + result.getStatus());
            }

            log.info("文档添加成功 - 集合名称: {}, 添加数量: {}", collectionName, qdrantPointIds.size());
            return qdrantPointIds;

        } catch (ExecutionException | InterruptedException e) {
            log.error("添加文档到Qdrant失败 - 集合名称: {}, 错误信息: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("添加文档到Qdrant失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从Qdrant删除文档，返回成功删除的point_id（用于同步MySQL删除）
     * @param pointIds 待删除的文档在Qdrant中的point_id列表
     * @return 成功删除的point_id列表
     */
    public List<String> deleteDocuments(List<String> pointIds) {
        Assert.notEmpty(pointIds, "待删除的point_id列表不能为空");
        log.info("开始从Qdrant删除文档 - 集合名称: {}, 待删除数量: {}", collectionName, pointIds.size());

        try {
            // 转换ID格式为Qdrant要求的PointId类型
            List<Points.PointId> ids = new ArrayList<>(pointIds.size());
            for (String id : pointIds) {
                ids.add(PointIdFactory.id(UUID.fromString(id)));
            }

            // 执行删除并校验结果
            log.debug("开始执行Qdrant删除操作 - 集合名称: {}, 点数量: {}", collectionName, ids.size());
            Points.UpdateResult result = qdrantClient.deleteAsync(collectionName, ids).get();
            if (result.getStatus() != Points.UpdateStatus.Completed) {
                log.error("文档删除失败 - 集合名称: {}, Qdrant状态: {}", collectionName, result.getStatus());
                throw new RuntimeException("文档删除失败，Qdrant状态：" + result.getStatus());
            }

            log.info("文档删除成功 - 集合名称: {}, 删除数量: {}", collectionName, pointIds.size());
            return pointIds;

        } catch (Exception e) {
            log.error("从Qdrant删除文档失败 - 集合名称: {}, 错误信息: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("从Qdrant删除文档失败：" + e.getMessage(), e);
        }
    }

    /**
     * 相似性检索：根据查询词返回最相似的文档片段
     * @param request 检索请求（包含查询词、返回数量、相似度阈值等）
     * @return 匹配的文档列表（含相似度分数）
     */
    public List<Document> similaritySearch(SearchRequest request) {
        log.debug("开始相似性检索 - 集合名称: {}, 查询词: {}, 返回数量: {}, 相似度阈值: {}",
                collectionName, request.getQuery(), request.getTopK(), request.getSimilarityThreshold());

        try {
            // 1. 生成查询向量
            log.debug("生成查询向量 - 集合名称: {}, 查询词: {}", collectionName, request.getQuery());
            float[] queryEmbedding = embeddingModel.embed(request.getQuery());
            // 手动转换float[]为List<Float>（避免流操作依赖）
            List<Float> vectorList = new ArrayList<>(queryEmbedding.length);
            for (float vec : queryEmbedding) {
                vectorList.add(vec);
            }

            // 2. 构造Qdrant检索请求
            Points.SearchPoints searchRequest = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setLimit((long) request.getTopK()) // 返回数量
                    .setWithPayload(WithPayloadSelectorFactory.enable(true)) // 返回元数据
                    .addAllVector(vectorList) // 查询向量
                    .setScoreThreshold((float) request.getSimilarityThreshold()) // 相似度阈值
                    .build();

            // 3. 执行检索并转换结果
            log.debug("执行Qdrant检索 - 集合名称: {}", collectionName);
            List<Points.ScoredPoint> scoredPoints = qdrantClient.searchAsync(searchRequest).get();
            List<Document> results = new ArrayList<>(scoredPoints.size());
            for (Points.ScoredPoint point : scoredPoints) {
                results.add(convertToDocument(point));
            }

            log.debug("相似性检索完成 - 集合名称: {}, 返回结果数: {}", collectionName, results.size());
            return results;

        } catch (Exception e) {
            log.error("相似性检索失败 - 集合名称: {}, 查询词: {}, 错误信息: {}",
                    collectionName, request.getQuery(), e.getMessage(), e);
            throw new RuntimeException("相似性检索失败：" + e.getMessage(), e);
        }
    }

    /**
     * 将Qdrant返回的ScoredPoint转换为Spring AI的Document对象
     */
    private Document convertToDocument(Points.ScoredPoint scoredPoint) {
        // 解析payload（元数据+内容）
        Map<String, Object> metadata = new HashMap<>();
        for (Map.Entry<String, JsonWithInt.Value> entry : scoredPoint.getPayloadMap().entrySet()) {
            metadata.put(entry.getKey(), convertFromQdrantValue(entry.getValue()));
        }
        // 提取文档内容（从payload中移除单独存储）
        String content = (String) metadata.remove(CONTENT_FIELD_NAME);
        // 构造Document对象
        return Document.builder()
                .id(scoredPoint.getId().getUuid()) // Qdrant的point_id
                .text(content) // 文档内容
                .metadata(metadata) // 元数据
                .score((double) scoredPoint.getScore()) // 相似度分数
                .build();
    }

    /**
     * 构建Qdrant的payload（元数据+文档内容）
     */
    private Map<String, JsonWithInt.Value> buildPayload(Document document) {
        Map<String, JsonWithInt.Value> payload = new HashMap<>();

        // 1. 添加文档元数据
        for (Map.Entry<String, Object> entry : document.getMetadata().entrySet()) {
            payload.put(entry.getKey(), convertToQdrantValue(entry.getValue()));
        }

        // 2. 添加文档内容（单独存储，便于检索时提取）
        payload.put(CONTENT_FIELD_NAME, convertToQdrantValue(document.getText()));

        return payload;
    }

    /**
     * 将Java对象转换为Qdrant的JsonWithInt.Value格式（适配枚举类型）
     */
    private JsonWithInt.Value convertToQdrantValue(Object value) {
        if (value == null) {
            return JsonWithInt.Value.newBuilder().setNullValue(JsonWithInt.NullValue.NULL_VALUE).build();
        }
        if (value instanceof String) {
            return JsonWithInt.Value.newBuilder().setStringValue((String) value).build();
        }
        if (value instanceof Integer) {
            return JsonWithInt.Value.newBuilder().setIntegerValue((Integer) value).build();
        }
        if (value instanceof Double || value instanceof Float) {
            return JsonWithInt.Value.newBuilder().setDoubleValue(((Number) value).doubleValue()).build();
        }
        if (value instanceof Boolean) {
            return JsonWithInt.Value.newBuilder().setBoolValue((Boolean) value).build();
        }
        // 复杂类型（列表、Map等）转换为JSON字符串
        return JsonWithInt.Value.newBuilder().setStringValue(GSON.toJson(value)).build();
    }

    /**
     * 将Qdrant的JsonWithInt.Value转换为Java对象（适配枚举类型）
     */
    private Object convertFromQdrantValue(JsonWithInt.Value qdrantValue) {
        return switch (qdrantValue.getKindCase()) {
            case STRING_VALUE -> qdrantValue.getStringValue();
            case INTEGER_VALUE -> qdrantValue.getIntegerValue();
            case DOUBLE_VALUE -> qdrantValue.getDoubleValue();
            case BOOL_VALUE -> qdrantValue.getBoolValue();
            case NULL_VALUE, KIND_NOT_SET -> null;
            case STRUCT_VALUE -> GSON.fromJson(qdrantValue.getStructValue().toString(), Object.class);
            case LIST_VALUE -> GSON.fromJson(qdrantValue.getListValue().toString(), Object.class);
        };
    }
}