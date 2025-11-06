package cn.ling.service;


import cn.ling.Result;
import org.springframework.web.multipart.MultipartFile;


public interface AIService {

    Result<String> audioToText(MultipartFile multipartFile);



}
