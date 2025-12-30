package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.pojo.DocumentChunks;
import cn.ling.domain.pojo.Documents;
import cn.ling.domain.pojo.KnowledgeInfo;
import cn.ling.service.DocumentChunksService;
import cn.ling.service.DocumentsService;
import cn.ling.service.KnowledgeInfoService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库管理（最小版，对齐 rin-ai 的 /knowledge/* 口径）
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgeInfoService knowledgeInfoService;

    @Resource
    private DocumentsService documentsService;

    @Resource
    private DocumentChunksService documentChunksService;

    @Resource(name = "qdrantVectorStore")
    private VectorStore qdrantVectorStore;

    @GetMapping("/list")
    public Result<IPage<KnowledgeInfo>> list(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String kname,
            @RequestParam(required = false) Integer status
    ) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }

        Page<KnowledgeInfo> page = new Page<>(pageNum, pageSize);
        IPage<KnowledgeInfo> result = knowledgeInfoService.lambdaQuery()
                .like(StringUtils.hasText(kname), KnowledgeInfo::getKname, StringUtils.hasText(kname) ? kname.trim() : null)
                .eq(status != null, KnowledgeInfo::getStatus, status)
                .orderByDesc(KnowledgeInfo::getUpdateTime)
                .page(page);

        return Result.success(result);
    }

    @GetMapping("/detail/{id}")
    public Result<KnowledgeInfo> detail(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }
        return Result.success(knowledgeInfoService.getById(id));
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody KnowledgeInfo bo) {
        if (bo == null) {
            return Result.error(400, "参数不能为空");
        }
        if (!StringUtils.hasText(bo.getKname())) {
            return Result.error(400, "kname不能为空");
        }
        if (bo.getStatus() == null) {
            bo.setStatus(1);
        }
        if (!StringUtils.hasText(bo.getVectorModelName())) {
            bo.setVectorModelName("qdrant");
        }
        if (bo.getRetrieveLimit() == null) {
            bo.setRetrieveLimit(6);
        }
        if (bo.getCandidateCount() == null) {
            bo.setCandidateCount(20);
        }
        if (bo.getUseRerank() == null) {
            bo.setUseRerank(0);
        }

        boolean ok = bo.getId() == null ? knowledgeInfoService.save(bo) : knowledgeInfoService.updateById(bo);
        return ok ? Result.success("保存成功") : Result.error("保存失败");
    }

    @PostMapping("/remove/{id}")
    public Result<String> remove(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }
        try {
            // 1) 删除向量（按 metadata.knowledge_id 过滤）
            try {
                List<String> pointIds = documentChunksService.lambdaQuery()
                        .select(DocumentChunks::getQdrantPointId)
                        .eq(DocumentChunks::getKnowledgeId, id)
                        .list()
                        .stream()
                        .map(DocumentChunks::getQdrantPointId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.toList());
                if (!pointIds.isEmpty()) {
                    qdrantVectorStore.delete(pointIds);
                }
            } catch (Exception e) {
                // 向量删除失败会阻塞DB删除，避免产生“DB已删但向量仍可检索”的脏数据
                return Result.error("删除知识库向量失败：" + e.getMessage());
            }

            // 2) 删除分块与文档（DB）
            documentChunksService.lambdaUpdate()
                    .eq(DocumentChunks::getKnowledgeId, id)
                    .remove();
            documentsService.lambdaUpdate()
                    .eq(Documents::getKnowledgeId, id)
                    .remove();

            // 3) 删除知识库
            boolean ok = knowledgeInfoService.removeById(id);
            if (!ok) {
                return Result.error("删除失败");
            }

            // 4) 如果删除的是默认知识库，确保系统仍有默认值
            knowledgeInfoService.ensureDefaultKnowledgeId();
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败：" + e.getMessage());
        }
    }
}
