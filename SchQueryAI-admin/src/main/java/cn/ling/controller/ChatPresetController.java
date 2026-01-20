package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.pojo.ChatPreset;
import cn.ling.service.ChatPresetService;
import cn.ling.service.SysConfigService;
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
 * 对话预设（角色/参数预设）
 */
@Slf4j
@RestController
@RequestMapping("/chat/preset")
public class ChatPresetController {

    private static final String KEY_DEFAULT_PRESET_ID = "chat.preset.defaultId";

    @Resource
    private ChatPresetService chatPresetService;

    @Resource
    private SysConfigService sysConfigService;

    @GetMapping("/list")
    public Result<IPage<ChatPreset>> list(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String presetName,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer status
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

        Page<ChatPreset> page = new Page<>(pageNum, pageSize);
        IPage<ChatPreset> result = chatPresetService.lambdaQuery()
                .like(StringUtils.hasText(presetName), ChatPreset::getPresetName, StringUtils.hasText(presetName) ? presetName.trim() : null)
                .eq(StringUtils.hasText(model), ChatPreset::getModel, StringUtils.hasText(model) ? model.trim() : null)
                .eq(status != null, ChatPreset::getStatus, status)
                .orderByDesc(ChatPreset::getUpdateTime)
                .orderByDesc(ChatPreset::getId)
                .page(page);

        return Result.success(result);
    }

    @GetMapping("/detail/{id}")
    public Result<ChatPreset> detail(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }
        return Result.success(chatPresetService.getById(id));
    }

    @PostMapping("/save")
    public Result<String> save(@RequestBody ChatPreset preset) {
        if (preset == null) {
            return Result.error(400, "参数不能为空");
        }
        if (!StringUtils.hasText(preset.getPresetName())) {
            return Result.error(400, "presetName不能为空");
        }
        if (!StringUtils.hasText(preset.getModel())) {
            return Result.error(400, "model不能为空");
        }
        preset.setPresetName(preset.getPresetName().trim());
        preset.setModel(preset.getModel().trim());
        if (preset.getStatus() == null) {
            preset.setStatus(1);
        }
        preset.setPromptMergeMode(normalizePromptMergeModeOrNull(preset.getPromptMergeMode()));
        preset.setWeightUser(normalizeNullableWeight(preset.getWeightUser()));
        preset.setWeightKnowledge(normalizeNullableWeight(preset.getWeightKnowledge()));
        preset.setWeightMcp(normalizeNullableWeight(preset.getWeightMcp()));

        if ("balanced".equals(preset.getPromptMergeMode())) {
            Double u = preset.getWeightUser();
            Double k = preset.getWeightKnowledge();
            Double m = preset.getWeightMcp();
            boolean allNull = u == null && k == null && m == null;
            boolean anyNonNull = u != null || k != null || m != null;
            if (anyNonNull && (u == null || k == null || m == null)) {
                return Result.error(400, "balanced 模式下如需覆盖权重，请同时设置 用户/知识库/MCP 三项权重，或全部留空以跟随全局。");
            }
            if (!allNull) {
                double sum = u + k + m;
                if (!Double.isFinite(sum) || Math.abs(sum - 1.0) > 0.01) {
                    return Result.error(400, "balanced 模式下三项权重之和需为 1.00（允许误差 ±0.01）。");
                }
            }
        }

        boolean ok = preset.getId() == null ? chatPresetService.save(preset) : chatPresetService.updateById(preset);
        return ok ? Result.success("保存成功") : Result.error("保存失败");
    }

    @PostMapping("/remove/{id}")
    public Result<String> remove(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }

        Long defaultId = parseLong(sysConfigService.getConfigValue(KEY_DEFAULT_PRESET_ID));
        if (defaultId != null && defaultId.equals(id)) {
            sysConfigService.upsertConfig(KEY_DEFAULT_PRESET_ID, null, "默认对话预设ID", "对话预设");
        }

        boolean ok = chatPresetService.removeById(id);
        return ok ? Result.success("删除成功") : Result.error("删除失败");
    }

    @GetMapping("/default")
    public Result<Long> getDefaultPresetId() {
        Long id = parseLong(sysConfigService.getConfigValue(KEY_DEFAULT_PRESET_ID));
        return Result.success(id);
    }

    @PostMapping("/default/{id}")
    public Result<String> setDefaultPresetId(@PathVariable("id") Long id) {
        if (id == null) {
            return Result.error(400, "id不能为空");
        }
        ChatPreset preset = chatPresetService.getById(id);
        if (preset == null) {
            return Result.error(404, "预设不存在");
        }
        if (preset.getStatus() != null && preset.getStatus() == 0) {
            return Result.error(400, "预设未启用，不能设为默认");
        }
        sysConfigService.upsertConfig(KEY_DEFAULT_PRESET_ID, String.valueOf(id), "默认对话预设ID", "对话预设");
        return Result.success("设置成功");
    }

    private static Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizePromptMergeModeOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "user_first", "user", "userfirst" -> "user_first";
            case "knowledge_first", "knowledge", "knowledgefirst" -> "knowledge_first";
            case "balanced", "balance" -> "balanced";
            case "layered", "layer" -> "layered";
            default -> null;
        };
    }

    private static Double normalizeNullableWeight(Double raw) {
        if (raw == null || !Double.isFinite(raw)) {
            return null;
        }
        if (raw < 0) {
            return 0.0;
        }
        if (raw > 1) {
            return 1.0;
        }
        return raw;
    }
}
