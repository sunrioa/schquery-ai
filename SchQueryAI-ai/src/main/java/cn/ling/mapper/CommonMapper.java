package cn.ling.mapper;

import cn.ling.domain.vo.SuggestVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 通用Mapper - 避免模块循环依赖
 * 使用原生SQL进行数据库操作
 */
@Mapper
public interface CommonMapper {

    /**
     * 获取会话的历史消息
     *
     * @param sessionId 会话ID
     * @param limit 限制数量
     * @return 消息列表，包含 message_type 和 content
     */
    @Select("SELECT message_type, content FROM chat_message " +
            "WHERE session_id = #{sessionId} " +
            "ORDER BY created_at DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> getChatMessages(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /**
     * 获取会话的最新一条消息的意图（如果存入了单独的表）
     * 目前意图通过SSE返回，这里预留接口
     */
    @Select("SELECT content FROM chat_message " +
            "WHERE session_id = #{sessionId} AND message_type = 1 " +
            "ORDER BY created_at DESC " +
            "LIMIT 1")
    Map<String, Object> getLastAIMessage(@Param("sessionId") Long sessionId);
}
