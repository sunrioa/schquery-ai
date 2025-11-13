package cn.ling.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Base64;

/**
 * Base64工具类
 * 提供Base64编码解码功能，主要用于图片文件的Base64转换
 * 支持图片文件转换为带格式前缀的Base64字符串，以及从Base64字符串中提取纯数据
 */
@Slf4j
public class Base64Utils {

    /**
     * 将图片文件转换为Base64字符串
     * 将上传的图片文件转换为Base64编码格式，并添加相应的MIME类型前缀
     * 转换后的格式为：data:image/[type];base64,[data]
     *
     * @param imageFile 图片文件对象
     * @return Base64字符串，包含格式前缀；如果转换失败返回null
     * @throws RuntimeException 当文件读取或编码过程发生异常时抛出
     */
    public static String imageToBase64(MultipartFile imageFile) {
        log.debug("开始将图片文件转换为Base64格式");

        try {
            // 参数验证：检查文件是否存在且不为空
            if (imageFile == null || imageFile.isEmpty()) {
                log.warn("图片文件为空或不存在，转换失败");
                return null;
            }

            log.debug("图片文件信息 - 文件名: {}, 大小: {}字节, 内容类型: {}",
                    imageFile.getOriginalFilename(), imageFile.getSize(), imageFile.getContentType());

            // 验证文件MIME类型是否为图片格式
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                log.warn("文件不是有效的图片格式，内容类型: {}", contentType);
                return null;
            }

            // 将图片文件读取为字节数组
            log.debug("开始读取图片文件字节数据");
            byte[] imageBytes = imageFile.getBytes();

            // 转换为Base64编码字符串
            log.debug("开始Base64编码转换，原始数据大小: {}字节", imageBytes.length);
            String base64String = Base64.getEncoder().encodeToString(imageBytes);
            log.debug("Base64编码完成，编码后长度: {}字符", base64String.length());

            // 添加格式前缀，用于在HTML中直接显示
            String result = "data:" + contentType + ";base64," + base64String;
            log.info("图片文件Base64转换成功 - 文件名: {}, 输出长度: {}字符",
                    imageFile.getOriginalFilename(), result.length());

            return result;

        } catch (IOException e) {
            log.error("图片文件转换为Base64失败 - 文件名: {}, 错误信息: {}",
                    imageFile.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("图片转换为base64失败", e);
        }
    }

    /**
     * 从Base64字符串中提取纯Base64数据
     * 去除格式前缀，只保留Base64编码的纯数据部分
     * 用于需要纯Base64数据进行解码或存储的场景
     *
     * @param base64Image 带格式前缀的Base64字符串（如：data:image/png;base64,xxx）
     * @return 纯Base64数据字符串；如果输入不带前缀则直接返回原字符串
     */
    public static String extractBase64(String base64Image) {
        log.debug("开始从Base64字符串中提取纯数据，输入长度: {}字符",
                base64Image != null ? base64Image.length() : 0);

        // 参数验证：检查输入是否有效
        if (base64Image == null) {
            log.debug("输入字符串为null，直接返回null");
            return null;
        }

        // 检查是否包含格式前缀
        if (!base64Image.startsWith("data:")) {
            log.debug("输入字符串不包含格式前缀，直接返回原字符串");
            return base64Image;
        }

        // 查找逗号分隔符，分隔符后面是纯Base64数据
        int commaIndex = base64Image.indexOf(",");
        if (commaIndex == -1) {
            log.warn("Base64字符串格式异常，未找到分隔符，返回原字符串");
            return base64Image;
        }

        // 提取逗号后面的纯Base64数据
        String pureBase64 = base64Image.substring(commaIndex + 1);
        log.debug("Base64数据提取完成 - 原长度: {}字符, 纯数据长度: {}字符",
                base64Image.length(), pureBase64.length());

        return pureBase64;
    }
}