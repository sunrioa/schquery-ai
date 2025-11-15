package cn.ling.mapper;

import cn.ling.domain.pojo.CustomerServiceMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客服消息 Mapper
 */
@Mapper
public interface CustomerServiceMapper extends BaseMapper<CustomerServiceMessage> {
    /**
     * 根据用户ID获取所有客服消息
     */
    @Select("SELECT * FROM customer_service_message WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<CustomerServiceMessage> selectByUserId(@Param("userId") Long userId);

    /**
     * 获取未读消息数量
     */
    @Select("SELECT COUNT(*) FROM customer_service_message WHERE user_id = #{userId} AND read_status = 0 AND sender_type = 2")
    Integer getUnreadCount(@Param("userId") Long userId);

    /**
     * 标记消息为已读
     */
    @Select("UPDATE customer_service_message SET read_status = 1 WHERE user_id = #{userId} AND sender_type = #{senderType}")
    void markAsRead(@Param("userId") Long userId, @Param("senderType") Integer senderType);

    /**
     * 统计待处理会话中来自用户且未读的消恫数
     */
    @Select("SELECT COUNT(*) FROM customer_service_message m " +
            "WHERE m.sender_type = 1 AND m.read_status = 0 " +
            "AND m.user_id IN (SELECT user_id FROM customer_service_session WHERE session_status = 0)")
    Integer countUnreadMessagesByPendingSessions();

    /**
     * 统计指定用户且指定类型且未读的消恫数
     */
    @Select("SELECT COUNT(*) FROM customer_service_message WHERE user_id = #{userId} AND sender_type = #{senderType} AND read_status = 0")
    Integer countUnreadByUserAndType(@Param("userId") Long userId, @Param("senderType") Integer senderType);
}
