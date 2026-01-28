package cn.ling.service.impl;

import cn.ling.domain.pojo.AsrModel;
import cn.ling.mapper.AsrModelMapper;
import cn.ling.service.AsrModelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ASR 模型管理 Service 实现
 */
@Slf4j
@Service
public class AsrModelServiceImpl extends ServiceImpl<AsrModelMapper, AsrModel> implements AsrModelService {

    @Override
    public AsrModel getHighestPriorityEnabled() {
        return lambdaQuery()
                .eq(AsrModel::getModelShow, 1)
                .orderByDesc(AsrModel::getPriority)
                .orderByDesc(AsrModel::getUpdateTime)
                .last("limit 1")
                .one();
    }
}
