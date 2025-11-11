package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.domain.dto.SegmentationWordsDTO;
import cn.ling.domain.dto.SensitiveWordsDTO;
import cn.ling.domain.vo.SegmentationWordsVO;
import cn.ling.domain.vo.SensitiveWordsVO;
import cn.ling.service.UGCService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UGCServiceImpl implements UGCService {
    @Override
    public Result<String> addSensitiveWords(List<String> words) {
        return null;
    }

    @Override
    public Result<String> deleteSensitiveWords(List<Long> ids) {
        return null;
    }

    @Override
    public Result<String> updateSensitiveWords(List<SensitiveWordsDTO> sensitiveWordsDTOList) {
        return null;
    }

    @Override
    public Result<List<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO) {
        return null;
    }

    @Override
    public Result<String> addSegmentationWords(List<String> words) {
        return null;
    }

    @Override
    public Result<String> deleteSegmentationWords(List<Long> ids) {
        return null;
    }

    @Override
    public Result<String> updateSegmentationWords(List<SegmentationWordsDTO> segmentationWordsDTOList) {
        return null;
    }

    @Override
    public Result<List<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO sensitiveWordsDTO) {
        return null;
    }
}
