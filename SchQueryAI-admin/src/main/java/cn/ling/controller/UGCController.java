package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.dto.SegmentationWordsDTO;
import cn.ling.domain.dto.SensitiveWordsDTO;
import cn.ling.domain.vo.SegmentationWordsVO;
import cn.ling.domain.vo.SensitiveWordsVO;
import cn.ling.service.UGCService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/UGC")
public class UGCController {

    @Resource
    private UGCService ugcService;

    @PostMapping("/sensitive/add")
    public Result<String> addSensitiveWords(@RequestBody List<String> words){
        return ugcService.addSensitiveWords(words);
    }

    @DeleteMapping("/sensitive/delete")
    public Result<String> deleteSensitiveWords(@RequestBody List<Long> ids){
        return ugcService.deleteSensitiveWords(ids);
    }

    @PutMapping("/sensitive/update")
    public Result<String> updateSensitiveWords(@RequestBody List<SensitiveWordsDTO> sensitiveWordsDTOList){
        return ugcService.updateSensitiveWords(sensitiveWordsDTOList);
    }

    @GetMapping("/sensitive/query")
    public Result<IPage<SensitiveWordsVO>> querySensitiveWords(SensitiveWordsDTO sensitiveWordsDTO){
        return ugcService.querySensitiveWords(sensitiveWordsDTO);
    }

    @PostMapping("/segmentation/add")
    public Result<String> addSegmentationWords(@RequestBody List<String> words){
        return ugcService.addSegmentationWords(words);
    }

    @DeleteMapping("/segmentation/delete")
    public Result<String> deleteSegmentationWords(@RequestBody List<Long> ids){
        return ugcService.deleteSegmentationWords(ids);
    }

    @PutMapping("/segmentation/update")
    public Result<String> updateSegmentationWords(@RequestBody List<SegmentationWordsDTO> segmentationWordsDTOList){
        return ugcService.updateSegmentationWords(segmentationWordsDTOList);
    }

    @GetMapping("/segmentation/query")
    public Result<IPage<SegmentationWordsVO>> querySegmentationWords(SegmentationWordsDTO segmentationWordsDTO){
        return ugcService.querySegmentationWords(segmentationWordsDTO);
    }

}