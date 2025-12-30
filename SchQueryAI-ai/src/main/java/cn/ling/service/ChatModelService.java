package cn.ling.service;

import cn.ling.domain.pojo.ChatModel;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 模型管理 Service
 */
public interface ChatModelService extends IService<ChatModel> {

    /**
     * 按分类获取最高优先级启用模型（priority 越大越优先）。
     */
    ChatModel getHighestPriorityEnabledByCategory(String category);

    /**
     * 按分类+名称查询模型。
     */
    ChatModel getByCategoryAndName(String category, String modelName);
}

