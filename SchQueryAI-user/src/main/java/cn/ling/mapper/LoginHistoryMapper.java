package cn.ling.mapper;

import cn.ling.domain.pojo.LoginHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @description 针对表【login_history(用户登录历史记录表)】的数据库操作Mapper
 */
@Mapper
public interface LoginHistoryMapper extends BaseMapper<LoginHistory> {

}
