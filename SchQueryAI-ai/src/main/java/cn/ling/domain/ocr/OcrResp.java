package cn.ling.domain.ocr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrResp {

    String code;

    String msg;

    String data;

}