package cn.ling.service.impl;

import cn.ling.domain.pojo.KnowledgeInfo;
import cn.ling.service.KnowledgeInfoService;
import cn.ling.mapper.KnowledgeInfoMapper;
import cn.ling.service.SysConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 知识库管理 Service 实现
 */
@Slf4j
@Service
public class KnowledgeInfoServiceImpl extends ServiceImpl<KnowledgeInfoMapper, KnowledgeInfo> implements KnowledgeInfoService {

    private static final String KEY_KID = "chat.default.kid";
    private static final String KEY_KNAME = "chat.default.kName";

    private final SysConfigService sysConfigService;

    public KnowledgeInfoServiceImpl(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Override
    public Long ensureDefaultKnowledgeId() {
        try {
            String configuredKid = sysConfigService.getConfigValue(KEY_KID);
            Long kid = parseLong(configuredKid);
            if (kid != null) {
                KnowledgeInfo existing = getById(kid);
                if (existing != null) {
                    if (!StringUtils.hasText(sysConfigService.getConfigValue(KEY_KNAME))) {
                        sysConfigService.upsertConfig(KEY_KNAME, existing.getKname(), "默认知识库名称", "默认对话参数");
                    }
                    return kid;
                }
            }

            KnowledgeInfo first = lambdaQuery()
                    .orderByAsc(KnowledgeInfo::getId)
                    .last("limit 1")
                    .one();
            if (first != null) {
                sysConfigService.upsertConfig(KEY_KID, String.valueOf(first.getId()), "默认知识库ID", "默认对话参数");
                sysConfigService.upsertConfig(KEY_KNAME, first.getKname(), "默认知识库名称", "默认对话参数");
                return first.getId();
            }

            KnowledgeInfo created = KnowledgeInfo.builder()
                    .kname("默认知识库")
                    .description("系统自动创建的默认知识库")
                    .vectorModelName("qdrant")
                    .retrieveLimit(6)
                    .candidateCount(20)
                    .useRerank(0)
                    .status(1)
                    .build();
            boolean ok = save(created);
            if (!ok || created.getId() == null) {
                log.warn("创建默认知识库失败");
                return null;
            }
            sysConfigService.upsertConfig(KEY_KID, String.valueOf(created.getId()), "默认知识库ID", "默认对话参数");
            sysConfigService.upsertConfig(KEY_KNAME, created.getKname(), "默认知识库名称", "默认对话参数");
            return created.getId();
        } catch (Exception e) {
            log.warn("确保默认知识库失败: {}", e.getMessage());
            return null;
        }
    }

    private static Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}

