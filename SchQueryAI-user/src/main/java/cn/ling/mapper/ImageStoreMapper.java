package cn.ling.mapper;

import cn.ling.domain.pojo.ImageStore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author Administrator
* @description 针对表【image_store(图片/头像存储表（base64直接存储）)】的数据库操作Mapper
* @createDate 2025-11-07 00:23:52
* @Entity cn.ling.domain.pojo.ImageStore
*/
@Mapper
public interface ImageStoreMapper extends BaseMapper<ImageStore> {

}




