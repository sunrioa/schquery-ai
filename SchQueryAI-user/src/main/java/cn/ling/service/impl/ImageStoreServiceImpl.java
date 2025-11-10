package cn.ling.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.ImageStore;
import cn.ling.service.ImageStoreService;
import cn.ling.mapper.ImageStoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
* @author Administrator
* @description 针对表【image_store(图片/头像存储表（base64直接存储）)】的数据库操作Service实现
* @createDate 2025-11-07 00:23:52
*/
@Service
public class ImageStoreServiceImpl extends ServiceImpl<ImageStoreMapper, ImageStore>
    implements ImageStoreService{

    @Override
    public ImageStore uploadImage(MultipartFile imageFile) {
        return null;
    }

    @Override
    public ImageStore getImageById(Integer id) {
        return null;
    }
}




