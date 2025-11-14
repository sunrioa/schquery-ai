package cn.ling.service;

import cn.ling.Result;
import cn.ling.dto.CustomerServiceDTO;

/**
 * 客服消息服务接口
 * 该接口定义在domain模块，供AI和User模块共同使用
 */
public interface ICustomerServiceBridge {
    /**
     * 用户发送客服消息
     */
    Result<Long> sendMessage(CustomerServiceDTO dto);

    /**
     * 管理员回复客服消息
     */
    Result<Long> replyMessage(CustomerServiceDTO dto);
}
