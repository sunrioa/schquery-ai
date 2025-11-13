package cn.ling.mapper;

import cn.ling.domain.pojo.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话数据访问层接口
 * 提供聊天会话表的数据库操作方法，继承MyBatis Plus的BaseMapper获得基础CRUD功能
 * 支持用户聊天会话的创建、查询、更新、删除等操作
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

}




