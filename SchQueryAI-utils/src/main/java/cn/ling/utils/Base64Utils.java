package cn.ling.utils;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Base64;

public class Base64Utils {

    /**
     * 将图片文件转换为base64字符串
     * @param imageFile 图片文件
     * @return base64字符串，包含格式前缀
     */
    public static String imageToBase64(MultipartFile imageFile){
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return null;
            }

            // 获取文件MIME类型
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return null;
            }

            // 转换为base64
            byte[] imageBytes = imageFile.getBytes();
            String base64String = Base64.getEncoder().encodeToString(imageBytes);

            // 添加格式前缀
            return "data:" + contentType + ";base64," + base64String;

        } catch (IOException e) {
            throw new RuntimeException("图片转换为base64失败", e);
        }
    }

    /**
     * 从base64字符串中提取纯base64数据
     * @param base64Image 带格式前缀的base64字符串
     * @return 纯base64数据
     */
    public static String extractBase64(String base64Image) {
        if (base64Image == null || !base64Image.startsWith("data:")) {
            return base64Image;
        }

        int commaIndex = base64Image.indexOf(",");
        if (commaIndex == -1) {
            return base64Image;
        }

        return base64Image.substring(commaIndex + 1);
    }

}
