package cn.ling.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.ling.domain.pojo.ImageStore;
import cn.ling.service.ImageStoreService;
import cn.ling.mapper.ImageStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Date;

/**
 * 图片存储服务实现类
 * 管理用户上传的图片和头像文件，支持图片的上传、存储和检索
 * 使用Base64编码直接存储图片数据到数据库
 */
@Slf4j
@Service
public class ImageStoreServiceImpl extends ServiceImpl<ImageStoreMapper, ImageStore>
    implements ImageStoreService{

    /**
     * 上传图片并存储到数据库
     * 将上传的图片文件转换为Base64编码并存储到数据库中
     * 支持常见图片格式：JPEG、PNG、GIF等
     *
     * @param imageFile 上传的图片文件
     * @return 存储的图片实体对象，包含ID、Base64编码数据等信息
     * @throws RuntimeException 当文件处理失败或存储失败时抛出异常
     */
    @Override
    public ImageStore uploadImage(MultipartFile imageFile) {
        log.info("开始处理图片上传请求，文件名: {}, 文件大小: {} bytes",
            imageFile.getOriginalFilename(), imageFile.getSize());

        try {
            // 验证文件是否存在
            if (imageFile == null || imageFile.isEmpty()) {
                log.warn("上传的图片文件为空");
                throw new RuntimeException("上传的图片文件不能为空");
            }

            // 验证文件大小（限制为10MB）
            final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
            if (imageFile.getSize() > MAX_FILE_SIZE) {
                log.warn("图片文件过大，当前大小: {} bytes, 最大允许大小: {} bytes",
                    imageFile.getSize(), MAX_FILE_SIZE);
                throw new RuntimeException("图片文件大小不能超过10MB");
            }

            // 验证文件类型
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                log.warn("上传的文件不是图片类型，实际类型: {}", contentType);
                throw new RuntimeException("只能上传图片文件");
            }

            // 获取文件扩展名
            String originalFilename = imageFile.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
                log.debug("图片文件扩展名: {}", fileExtension);
            }

            // 转换为Base64编码
            log.debug("开始将图片转换为Base64编码");
            byte[] imageBytes = imageFile.getBytes();
            String base64Data = java.util.Base64.getEncoder().encodeToString(imageBytes);

            // 创建图片存储对象
            log.debug("创建图片存储对象并准备保存到数据库");
            ImageStore imageStore = new ImageStore();
            imageStore.setImageName(originalFilename);
            imageStore.setImageBase64(base64Data);
                imageStore.setCreatedAt(new Date());

            // 保存到数据库
            boolean saveSuccess = save(imageStore);
            if (saveSuccess) {
                log.info("图片上传成功，文件名: {}, 存储ID: {}, 文件大小: {} bytes",
                    originalFilename, imageStore.getId(), imageFile.getSize());
                return imageStore;
            } else {
                log.error("图片保存到数据库失败，文件名: {}", originalFilename);
                throw new RuntimeException("图片保存失败");
            }
        } catch (Exception e) {
            log.error("图片上传过程中发生异常，文件名: {}, 异常信息: {}",
                imageFile.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("图片上传失败：" + e.getMessage());
        }
    }

    /**
     * 根据ID获取图片信息
     * 从数据库中检索指定ID的图片记录，包括Base64编码数据
     *
     * @param id 图片的数据库记录ID
     * @return 图片存储实体对象，如果不存在则返回null
     * @throws IllegalArgumentException 当ID为null或无效时抛出异常
     */
    @Override
    public ImageStore getImageById(Integer id) {
        log.info("开始查询图片信息，图片ID: {}", id);

        try {
            // 验证ID参数
            if (id == null || id <= 0) {
                log.warn("无效的图片ID: {}", id);
                throw new IllegalArgumentException("图片ID必须为正整数");
            }

            // 从数据库查询图片
            log.debug("从数据库查询图片ID: {}", id);
            ImageStore imageStore = getById(id);

            if (imageStore != null) {
                log.info("成功查询到图片信息，ID: {}, 文件名: {}",
                    id, imageStore.getImageName());
                return imageStore;
            } else {
                log.warn("未找到指定ID的图片，ID: {}", id);
                return null;
            }
        } catch (IllegalArgumentException e) {
            log.error("图片ID参数无效: {}", id, e);
            throw e;
        } catch (Exception e) {
            log.error("查询图片信息时发生异常，图片ID: {}, 异常信息: {}", id, e.getMessage(), e);
            throw new RuntimeException("查询图片信息失败：" + e.getMessage());
        }
    }
}




