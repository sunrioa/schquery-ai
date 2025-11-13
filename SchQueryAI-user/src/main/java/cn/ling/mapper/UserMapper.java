package cn.ling.mapper;

import cn.ling.domain.pojo.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层接口
 * 提供用户表的数据库操作方法，继承MyBatis Plus的BaseMapper获得基础CRUD功能
 * 支持用户信息的增删改查、条件查询、分页查询等操作
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}




