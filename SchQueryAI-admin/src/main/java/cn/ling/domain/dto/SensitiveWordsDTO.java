package cn.ling.domain.dto;

import lombok.Data;

@Data
public class SensitiveWordsDTO {
    private Long id;

    private String word;

    private Integer status;
}