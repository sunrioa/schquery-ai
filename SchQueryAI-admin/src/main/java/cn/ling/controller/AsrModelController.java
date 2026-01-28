package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.pojo.AsrModel;
import cn.ling.service.AsrModelService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 语音识别模型管理
 */
@Slf4j
@RestController
@RequestMapping("/asr/model")
public class AsrModelController {

    @Resource
    private AsrModelService asrModelService;

    @GetMapping("/list")
    public Result<IPage<AsrModel>> list(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer modelShow,
            @RequestParam(required = false) String modelName
    ) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > 200) {
            pageSize = 200;
        }

        Page<AsrModel> page = new Page<>(pageNum, pageSize);
        IPage<AsrModel> result = asrModelService.lambdaQuery()
                .eq(modelShow != null, AsrModel::getModelShow, modelShow)
                .like(StringUtils.hasText(modelName), AsrModel::getModelName, StringUtils.hasText(modelName) ? modelName.trim() : null)
                .orderByDesc(AsrModel::getPriority)
                .orderByDesc(AsrModel::getUpdateTime)
                .page(page);

        return Result.success(result);
    }

    @GetMapping("/detail/{id}")
    public Result<AsrModel> detail(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }
        AsrModel model = asrModelService.getById(id);
        return Result.success(model);
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody AsrModel model) {
        if (model == null) {
            return Result.error(400, "参数不能为空");
        }
        if (!StringUtils.hasText(model.getModelName())) {
            return Result.error(400, "modelName不能为空");
        }
        if (model.getModelShow() == null) {
            model.setModelShow(1);
        }
        if (model.getPriority() == null) {
            model.setPriority(1);
        }

        boolean ok = model.getId() == null ? asrModelService.save(model) : asrModelService.updateById(model);
        return ok ? Result.success("保存成功") : Result.error("保存失败");
    }

    @PostMapping("/remove/{id}")
    public Result<String> remove(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }
        boolean ok = asrModelService.removeById(id);
        return ok ? Result.success("删除成功") : Result.error("删除失败");
    }
}
