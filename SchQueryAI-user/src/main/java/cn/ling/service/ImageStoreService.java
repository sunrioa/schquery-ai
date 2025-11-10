package cn.ling.service;

import cn.ling.domain.pojo.ImageStore;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
* @author Administrator
* @description 针对表【image_store(图片/头像存储表（base64直接存储）)】的数据库操作Service
* @createDate 2025-11-07 00:23:52
*/
public interface ImageStoreService extends IService<ImageStore> {

    ImageStore uploadImage(MultipartFile imageFile);

    ImageStore getImageById(Integer id);
}
