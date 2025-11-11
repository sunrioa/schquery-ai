package cn.ling.service;

import cn.ling.Result;
import cn.ling.domain.dto.SegmentationWordsDTO;
import cn.ling.domain.dto.SensitiveWordsDTO;
import cn.ling.domain.vo.SegmentationWordsVO;
import cn.ling.domain.vo.SensitiveWordsVO;

import java.util.List;

public interface UGCService {
    Result<String> addSensitiveWords(List<String> words);

    Result<String> deleteSensitiveWords(List<Long> ids);

    Result<String> updateSensitiveWords(List<SensitiveWordsDTO> sensitiveWordsDTOList);

    Result<List<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO);

    Result<String> addSegmentationWords(List<String> words);

    Result<String> deleteSegmentationWords(List<Long> ids);

    Result<String> updateSegmentationWords(List<SegmentationWordsDTO> segmentationWordsDTOList);

    Result<List<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO sensitiveWordsDTO);
}
