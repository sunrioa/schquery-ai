package cn.ling.service;

import cn.ling.Result;
import cn.ling.dto.CustomerServiceDTO;
import cn.ling.domain.pojo.CustomerServiceMessage;
import cn.ling.domain.pojo.CustomerServiceSession;
import cn.ling.domain.vo.CustomerServiceVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 客服消息服务接口
 */
public interface CustomerServiceService extends IService<CustomerServiceMessage> {
    /**
     * 用户发送客服消息
     */
    Result<Long> sendMessage(CustomerServiceDTO dto);

    /**
     * 管理员回复客服消息
     */
    Result<Long> replyMessage(CustomerServiceDTO dto);

    /**
     * 获取用户的客服消息列表
     */
    Result<List<CustomerServiceVO>> getMessagesByUserId(Long userId);

    /**
     * 标记消息为已读
     */
    Result<String> markAsRead(Long userId);

    /**
     * 获取所有待处理的客服会话
     */
    Result<List<CustomerServiceVO.UserSessionVO>> getPendingSessions();

    /**
     * 获取客服统计信息
     */
    Result<CustomerServiceVO.StatsVO> getStats();

    /**
     * 分配客服会话给管理员
     */
    Result<String> assignSession(Long userId, Long adminId);

    /**
     * 标记特定用户的消恫为已读
     * @param userId 用户ID
     * @param senderType 发送者类型（0-所有, 1-用户, 2-管理员）
     */
    Result<String> markAsReadByUser(Long userId, Integer senderType);

    /**
     * 完成客服会话
     */
    Result<String> completeSession(Long userId);
}