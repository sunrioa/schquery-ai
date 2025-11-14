package cn.ling.mapper;

import cn.ling.domain.pojo.CustomerServiceSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客服会话 Mapper
 */
@Mapper
public interface CustomerServiceSessionMapper extends BaseMapper<CustomerServiceSession> {
    /**
     * 获取所有客服会话（包括已完成）
     */
    @Select("SELECT * FROM customer_service_session ORDER BY update_time DESC")
    List<CustomerServiceSession> selectPendingSessions();

    /**
     * 根据用户ID获取会话
     */
    @Select("SELECT * FROM customer_service_session WHERE user_id = #{userId}")
    CustomerServiceSession selectByUserId(@Param("userId") Long userId);

    /**
     * 获取待处理的会话数量
     */
    @Select("SELECT COUNT(*) FROM customer_service_session WHERE session_status = 0")
    Integer getPendingCount();

    /**
     * 获取未读消息总数
     */
    @Select("SELECT SUM(unread_count) FROM customer_service_session WHERE session_status = 0")
    Integer getTotalUnreadCount();
}
