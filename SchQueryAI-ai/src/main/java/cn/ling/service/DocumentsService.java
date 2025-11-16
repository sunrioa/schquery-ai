package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.dto.DocumentsDTO;
import cn.ling.domain.pojo.Documents;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
* @author Administrator
* @description 针对表【documents】的数据库操作Service
* @createDate 2025-11-16 00:26:43
*/
public interface DocumentsService extends IService<Documents> {

    Result<String> upload(DocumentsDTO documentsDTO, MultipartFile file);
}
