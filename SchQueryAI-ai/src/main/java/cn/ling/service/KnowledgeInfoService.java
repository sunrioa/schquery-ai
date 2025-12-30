package cn.ling.service;

import cn.ling.domain.pojo.KnowledgeInfo;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 知识库管理 Service
 */
public interface KnowledgeInfoService extends IService<KnowledgeInfo> {

    /**
     * 确保存在默认知识库，并返回其 ID。
     * 默认知识库ID会同步写入 sys_config：chat.default.kid / chat.default.kName
     */
    Long ensureDefaultKnowledgeId();
}

