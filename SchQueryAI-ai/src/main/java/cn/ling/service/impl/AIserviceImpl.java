package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.service.AIService;
import cn.ling.service.SttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AIserviceImpl implements AIService {

    @Autowired
    private SttService sttService;

    @Override
    public Result<String> audioToText(MultipartFile multipartFile) {
        String sttResult = sttService.getSttResult(multipartFile);
        return Result.success(sttResult);
    }
}
