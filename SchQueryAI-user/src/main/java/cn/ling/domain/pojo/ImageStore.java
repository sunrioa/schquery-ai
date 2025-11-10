package cn.ling.domain.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 图片/头像存储表（base64直接存储）
 * @TableName image_store
 */
@TableName(value ="image_store")
@Data
public class ImageStore {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 图片名称
     */
    private String imageName;

    /**
     * base64编码的图片数据（建议包含格式前缀，如data:image/png;base64,...）
     */
    private String imageBase64;

    /**
     * 上传时间
     */
    private Date createdAt;
}