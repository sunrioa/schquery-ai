package cn.ling.service.impl;

import cn.ling.domain.pojo.ChatModel;
import cn.ling.mapper.ChatModelMapper;
import cn.ling.service.ChatModelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 模型管理 Service 实现
 */
@Slf4j
@Service
public class ChatModelServiceImpl extends ServiceImpl<ChatModelMapper, ChatModel> implements ChatModelService {

    @Override
    public ChatModel getHighestPriorityEnabledByCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String normalized = category.trim();
        return lambdaQuery()
                .eq(ChatModel::getCategory, normalized)
                .eq(ChatModel::getModelShow, 1)
                .orderByDesc(ChatModel::getPriority)
                .orderByDesc(ChatModel::getUpdateTime)
                .last("limit 1")
                .one();
    }

    @Override
    public ChatModel getByCategoryAndName(String category, String modelName) {
        if (!StringUtils.hasText(category) || !StringUtils.hasText(modelName)) {
            return null;
        }
        return lambdaQuery()
                .eq(ChatModel::getCategory, category.trim())
                .eq(ChatModel::getModelName, modelName.trim())
                .last("limit 1")
                .one();
    }
}

