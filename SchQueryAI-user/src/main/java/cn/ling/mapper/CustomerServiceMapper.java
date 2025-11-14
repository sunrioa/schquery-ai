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
}
