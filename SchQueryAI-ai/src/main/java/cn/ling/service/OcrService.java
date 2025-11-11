package cn.ling.service;

import org.springframework.web.multipart.MultipartFile;

public interface OcrService {

    String doOcr(MultipartFile pdf_File);

}
