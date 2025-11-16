package cn.ling.service;

import cn.ling.domain.pojo.DocumentChunks;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
* @author Administrator
* @description 针对表【document_chunks】的数据库操作Service
* @createDate 2025-11-16 00:26:43
*/
public interface DocumentChunksService extends IService<DocumentChunks> {

    void saveDocument(Long id, Map<String,Object> metadata, String content);
}
