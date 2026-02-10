package cn.ling.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 追问建议响应VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SuggestVO {
    /**
     * 是否启用追问建议功能
     */
    private Boolean enabled;

    /**
     * 追问建议列表（最多3条）
     */
    private List<String> suggestions;
}
