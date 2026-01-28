package cn.ling.service;

import cn.ling.domain.pojo.AsrModel;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * ASR 模型管理 Service
 */
public interface AsrModelService extends IService<AsrModel> {

    /**
     * 获取最高优先级的启用模型（priority 越大越优先）。
     */
    AsrModel getHighestPriorityEnabled();
}
