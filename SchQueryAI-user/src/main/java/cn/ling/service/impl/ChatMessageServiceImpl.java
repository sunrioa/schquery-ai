package cn.ling.service.impl;

import cn.ling.Result;
import cn.ling.context.ChatContext;
import cn.ling.domain.dto.ChatMessageDTO;
import cn.ling.domain.pojo.ChatMessage;
import cn.ling.domain.pojo.ChatPreset;
import cn.ling.domain.vo.ChatMessageVO;
import cn.ling.exception.CustomException;
import cn.ling.mapper.ChatMessageMapper;
import cn.ling.service.ChatMessageService;
import cn.ling.service.ChatPresetService;
import cn.ling.service.ChatSessionService;
import cn.ling.service.SysConfigService;
import cn.ling.service.chat.DynamicChatClientService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天消息服务实现类
 * 处理用户与AI之间的消息交互，包括消息的保存、查询、删除和AI响应生成
 * 支持流式响应以提高用户体验
 */
@Slf4j
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

    private static final String KEY_MODEL = "chat.default.model";
    private static final String KEY_MAX_TOKENS = "chat.default.max_tokens";
    private static final String KEY_SYSTEM_MESSAGE = "chat.default.systemMessage";
    private static final String KEY_TEMPERATURE = "chat.default.temperature";
    private static final String KEY_TOP_P = "chat.default.top_p";
    private static final String KEY_PRESENCE_PENALTY = "chat.default.presence_penalty";
    private static final String KEY_FREQUENCY_PENALTY = "chat.default.frequency_penalty";
    private static final String KEY_DEFAULT_PRESET_ID = "chat.preset.defaultId";

    /**
     * 系统配置（KV）
     */
    @Resource
    private SysConfigService sysConfigService;

    @Resource
    private ChatPresetService chatPresetService;

    @Resource
    private DynamicChatClientService dynamicChatClientService;

    /**
     * 聊天会话服务
     * 用于更新会话的最后消息时间
     */
    @Resource
    private ChatSessionService chatSessionService;

    /**
     * 获取指定会话的所有消息
     * 按消息创建时间升序排列，确保对话的时序性
     *
     * @param sessionId 会话ID
     * @return 包含消息列表的结果对象，如果没有消息则返回null
     */
    @Override
    public Result<List<ChatMessageVO>> getMessage(Long sessionId) {
        try {
            // 查询指定会话的所有消息
            List<ChatMessage> messages = lambdaQuery()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .orderByAsc(ChatMessage::getCreatedAt)
                    .list();

            if (messages != null && !messages.isEmpty()) {
                // 转换为VO对象
                List<ChatMessageVO> messageVOs = messages.stream()
                    .map(message -> {
                        ChatMessageVO vo = new ChatMessageVO();
                        vo.setId(message.getId());
                        vo.setMessageType(message.getMessageType());
                        vo.setContent(message.getContent());
                        vo.setCreatedAt(message.getCreatedAt());
                        return vo;
                    })
                    .collect(Collectors.toList());

                return Result.success(messageVOs);
            } else {
                return Result.success(null);
            }
        } catch (Exception e) {
            log.error("获取会话 {} 消息列表时发生异常: {}", sessionId, e.getMessage(), e);
            throw CustomException.error("获取消息列表失败：" + e.getMessage());
        }
    }

    /**
     * 发送消息并获取AI回复
     * 先保存用户消息，然后生成流式AI回复，最后保存完整的AI回复
     *
     * @param chatMessageDTO 包含会话ID和消息内容的数据传输对象
     * @return AI回复的流式数据流
     */
    @Override
    public Flux<String> sendMessage(ChatMessageDTO chatMessageDTO) {
        try {
            // 先保存用户消息
            ChatMessage userMessage = new ChatMessage();
            userMessage.setSessionId(chatMessageDTO.getSessionId());
            userMessage.setMessageType(0); // 用户消息
            userMessage.setContent(chatMessageDTO.getContent());
            userMessage.setCreatedAt(new Date());

            boolean saveSuccess = save(userMessage);

            if (!saveSuccess) {
                log.error("保存用户消息失败，会话ID: {}", chatMessageDTO.getSessionId());
                return Flux.error(new RuntimeException("保存用户消息失败"));
            }

            // 更新会话最后消息时间
            chatSessionService.updateLastMessageTime(chatMessageDTO.getSessionId());

            // 解析预设并设置到 ThreadLocal
            ChatPreset preset = resolveChatPreset();
            ChatContext.setPreset(preset);

            try {
                // 使用流式生成AI回复
                String modelName = resolveModelName(preset);
                ChatClient client = dynamicChatClientService.resolveClient(modelName);
                ChatClient.ChatClientRequestSpec promptSpec = client.prompt();
                OpenAiChatOptions dynamicOptions = buildChatOptions(preset);
                if (dynamicOptions != null) {
                    promptSpec = promptSpec.options(dynamicOptions);
                }
                String dynamicSystemMessage = resolveSystemMessage(preset);
                if (StringUtils.hasText(dynamicSystemMessage)) {
                    promptSpec = promptSpec.system(dynamicSystemMessage);
                }
                Flux<String> aiResponseStream = promptSpec.user(chatMessageDTO.getContent()).stream().content();

                // 关键修复：添加微小延迟（50ms）确保浏览器不缓冲 SSE 响应
                // 这使每个数据块间隔足够长，让浏览器识别为"流式"而非"缓冲"
                aiResponseStream = aiResponseStream.delayElements(java.time.Duration.ofMillis(50));

                // 用于累积完整的AI回复
                StringBuilder fullResponse = new StringBuilder();

                // 处理流式响应
                return aiResponseStream
                        .doOnNext(fullResponse::append)
                        .concatWith(Flux.defer(() -> {
                            // 流式传输完成后，获取意图并作为最后一个事件发送
                            String intent = ChatContext.getIntent();
                            if (intent != null && !intent.isEmpty()) {
                                // 发送意图信息，格式：event: intent\ndata: {"intent":"专业信息"}\n\n
                                String intentEvent = String.format("event: intent\ndata: {\"intent\":\"%s\"}\n\n", intent);
                                return Flux.just(intentEvent);
                            }
                            return Flux.empty();
                        }))
                        .doOnComplete(() -> {
                            // 流式传输完成时，保存完整的AI回复到数据库
                            if (!fullResponse.isEmpty()) {
                                ChatMessage aiMessage = new ChatMessage();
                                aiMessage.setSessionId(chatMessageDTO.getSessionId());
                                aiMessage.setMessageType(1); // AI消息
                                aiMessage.setContent(fullResponse.toString());
                                aiMessage.setCreatedAt(new Date());
                                boolean aiSaveSuccess = this.save(aiMessage);

                                if (!aiSaveSuccess) {
                                    log.error("AI消息保存失败，会话ID: {}", chatMessageDTO.getSessionId());
                                }
                            }
                        })
                        .doOnError(error -> log.error("AI流式回复过程中发生错误: {}", error.getMessage(), error))
                        .doFinally(signalType -> {
                            // 无论成功还是失败，都要清理 ThreadLocal
                            ChatContext.clear();
                        });
            } catch (Exception e) {
                // 发生异常时也要清理 ThreadLocal
                ChatContext.clear();
                throw e;
            }
        } catch (Exception e) {
            log.error("处理用户消息时发生异常，会话ID: {}, 异常信息: {}",
                chatMessageDTO.getSessionId(), e.getMessage(), e);
            return Flux.error(new RuntimeException("消息发送失败：" + e.getMessage()));
        }
    }

    private OpenAiChatOptions buildChatOptions(ChatPreset preset) {
        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder().build();

            String model = preset != null ? trimToNull(preset.getModel()) : trimToNull(sysConfigService.getConfigValue(KEY_MODEL));
            if (StringUtils.hasText(model)) {
                options.setModel(model);
            }

            Integer maxTokens = preset != null ? preset.getMaxTokens() : parseInt(sysConfigService.getConfigValue(KEY_MAX_TOKENS));
            if (maxTokens != null && maxTokens > 0) {
                options.setMaxTokens(maxTokens);
            } else {
                // 未配置 maxTokens 时使用默认值 4096，避免输出被截断
                options.setMaxTokens(4096);
            }

            Double temperature = preset != null ? preset.getTemperature() : parseDouble(sysConfigService.getConfigValue(KEY_TEMPERATURE));
            if (temperature != null) {
                options.setTemperature(temperature);
            }

            Double topP = preset != null ? preset.getTopP() : parseDouble(sysConfigService.getConfigValue(KEY_TOP_P));
            if (topP != null) {
                options.setTopP(topP);
            }

            Double presencePenalty = preset != null ? preset.getPresencePenalty() : parseDouble(sysConfigService.getConfigValue(KEY_PRESENCE_PENALTY));
            if (presencePenalty != null) {
                options.setPresencePenalty(presencePenalty);
            }

            Double frequencyPenalty = preset != null ? preset.getFrequencyPenalty() : parseDouble(sysConfigService.getConfigValue(KEY_FREQUENCY_PENALTY));
            if (frequencyPenalty != null) {
                options.setFrequencyPenalty(frequencyPenalty);
            }

            return options;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveModelName(ChatPreset preset) {
        String fromPreset = preset == null ? null : trimToNull(preset.getModel());
        if (StringUtils.hasText(fromPreset)) {
            return fromPreset;
        }
        return trimToNull(sysConfigService.getConfigValue(KEY_MODEL));
    }

    private ChatPreset resolveChatPreset() {
        try {
            // Chat UI no longer supports per-session model switching: always follow the global default preset.
            Long presetId = parseLong(sysConfigService.getConfigValue(KEY_DEFAULT_PRESET_ID));
            if (presetId == null) {
                return null;
            }

            ChatPreset preset = chatPresetService.getById(presetId);
            if (preset == null) {
                return null;
            }
            if (preset.getStatus() != null && preset.getStatus() == 0) {
                return null;
            }
            return preset;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveSystemMessage(ChatPreset preset) {
        String fromPreset = preset == null ? null : trimToNull(preset.getSystemMessage());
        if (StringUtils.hasText(fromPreset)) {
            return fromPreset;
        }
        return trimToNull(sysConfigService.getConfigValue(KEY_SYSTEM_MESSAGE));
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer parseInt(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
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

    /**
     * 删除指定消息
     * 执行物理删除操作，从数据库中彻底移除消息记录
     * 注意：在生产环境中应增加权限验证，确保用户只能删除自己的消息
     *
     * @param chatMessageDTO 包含消息ID的数据传输对象
     * @return 删除操作的结果对象
     */
    @Override
    public Result<String> deleteMessage(ChatMessageDTO chatMessageDTO) {
        try {
            // 验证消息是否存在
            ChatMessage existingMessage = getById(chatMessageDTO.getId());
            if (existingMessage == null) {
                log.warn("尝试删除不存在的消息，消息ID: {}", chatMessageDTO.getId());
                return Result.error("消息不存在");
            }

            boolean success = removeById(chatMessageDTO.getId());

            if (success) {
                return Result.success("删除消息成功");
            } else {
                log.error("消息删除失败，消息ID: {}", chatMessageDTO.getId());
                throw CustomException.error("删除消息失败");
            }
        } catch (Exception e) {
            log.error("删除消息时发生异常，消息ID: {}, 异常信息: {}",
                chatMessageDTO.getId(), e.getMessage(), e);
            throw CustomException.error("删除消息失败：" + e.getMessage());
        }
    }
}
