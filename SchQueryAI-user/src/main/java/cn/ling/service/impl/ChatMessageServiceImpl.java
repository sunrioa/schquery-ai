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
     * AI聊天客户端
     * 用于生成AI回复
     */
    @Resource(name="openAiChatClient")
    private ChatClient openAiChatClient;

    @Resource
    private SysConfigService sysConfigService;

    @Resource
    private ChatPresetService chatPresetService;

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
        log.info("开始获取会话 {} 的消息列表", sessionId);

        try {
            // 查询指定会话的所有消息
            log.debug("查询会话 {} 的所有消息", sessionId);
            List<ChatMessage> messages = lambdaQuery()
                    .eq(ChatMessage::getSessionId, sessionId)
                    .orderByAsc(ChatMessage::getCreatedAt)
                    .list();

            if (messages != null && !messages.isEmpty()) {
                log.info("查询到会话 {} 的 {} 条消息", sessionId, messages.size());

                // 转换为VO对象
                log.debug("开始转换为VO对象");
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

                log.info("成功获取会话 {} 的 {} 条消息", sessionId, messageVOs.size());
                return Result.success(messageVOs);
            } else {
                log.info("会话 {} 没有消息记录", sessionId);
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
        log.info("开始处理用户消息，会话ID: {}, 内容长度: {}",
            chatMessageDTO.getSessionId(), chatMessageDTO.getContent().length());

        try {
            // 先保存用户消息
            log.debug("保存用户消息到数据库");
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
            log.info("用户消息保存成功，消息ID: {}", userMessage.getId());

            // 更新会话最后消息时间
            log.debug("更新会话 {} 的最后消息时间", chatMessageDTO.getSessionId());
            chatSessionService.updateLastMessageTime(chatMessageDTO.getSessionId());

            // 解析预设并设置到 ThreadLocal
            ChatPreset preset = resolveChatPreset(chatMessageDTO.getSessionId());
            ChatContext.setPreset(preset);

            try {
                // 使用流式生成AI回复
                log.info("开始生成AI回复");
                ChatClient.ChatClientRequestSpec promptSpec = openAiChatClient.prompt();
                OpenAiChatOptions dynamicOptions = buildChatOptions(preset);
                if (dynamicOptions != null) {
                    promptSpec = promptSpec.options(dynamicOptions);
                }
                String dynamicSystemMessage = resolveSystemMessage(preset);
                if (StringUtils.hasText(dynamicSystemMessage)) {
                    promptSpec = promptSpec.system(dynamicSystemMessage);
                }
                Flux<String> aiResponseStream = promptSpec.user(chatMessageDTO.getContent()).stream().content();

                // 用于累积完整的AI回复
                StringBuilder fullResponse = new StringBuilder();

                // 处理流式响应
                return aiResponseStream
                        .doOnNext(chunk -> {
                            log.debug("收到AI回复片段，长度: {}", chunk.length());
                            fullResponse.append(chunk);
                        })
                        .doOnComplete(() -> {
                            // 流式传输完成时，保存完整的AI回复到数据库
                            log.info("AI流式回复完成，开始保存完整回复");
                            if (!fullResponse.isEmpty()) {
                                ChatMessage aiMessage = new ChatMessage();
                                aiMessage.setSessionId(chatMessageDTO.getSessionId());
                                aiMessage.setMessageType(1); // AI消息
                                aiMessage.setContent(fullResponse.toString());
                                aiMessage.setCreatedAt(new Date());
                                boolean aiSaveSuccess = this.save(aiMessage);

                                if (aiSaveSuccess) {
                                    log.info("AI消息保存成功，消息ID: {}, 回复长度: {}",
                                        aiMessage.getId(), fullResponse.length());
                                } else {
                                    log.error("AI消息保存失败，会话ID: {}", chatMessageDTO.getSessionId());
                                }
                            } else {
                                log.warn("AI回复内容为空，会话ID: {}", chatMessageDTO.getSessionId());
                            }
                        })
                        .doOnError(error -> {
                            log.error("AI流式回复过程中发生错误: {}", error.getMessage(), error);
                        })
                        .doFinally(signalType -> {
                            // 无论成功还是失败，都要清理 ThreadLocal
                            ChatContext.clear();
                            log.debug("已清理 ChatContext");
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
            boolean hasAny = false;
            OpenAiChatOptions options = OpenAiChatOptions.builder().build();

            String model = preset != null ? trimToNull(preset.getModel()) : trimToNull(sysConfigService.getConfigValue(KEY_MODEL));
            if (StringUtils.hasText(model)) {
                options.setModel(model);
                hasAny = true;
            }

            Integer maxTokens = preset != null ? preset.getMaxTokens() : parseInt(sysConfigService.getConfigValue(KEY_MAX_TOKENS), null);
            if (maxTokens != null && maxTokens > 0) {
                options.setMaxTokens(maxTokens);
                hasAny = true;
            }

            Double temperature = preset != null ? preset.getTemperature() : parseDouble(sysConfigService.getConfigValue(KEY_TEMPERATURE), null);
            if (temperature != null) {
                options.setTemperature(temperature);
                hasAny = true;
            }

            Double topP = preset != null ? preset.getTopP() : parseDouble(sysConfigService.getConfigValue(KEY_TOP_P), null);
            if (topP != null) {
                options.setTopP(topP);
                hasAny = true;
            }

            Double presencePenalty = preset != null ? preset.getPresencePenalty() : parseDouble(sysConfigService.getConfigValue(KEY_PRESENCE_PENALTY), null);
            if (presencePenalty != null) {
                options.setPresencePenalty(presencePenalty);
                hasAny = true;
            }

            Double frequencyPenalty = preset != null ? preset.getFrequencyPenalty() : parseDouble(sysConfigService.getConfigValue(KEY_FREQUENCY_PENALTY), null);
            if (frequencyPenalty != null) {
                options.setFrequencyPenalty(frequencyPenalty);
                hasAny = true;
            }

            return hasAny ? options : null;
        } catch (Exception e) {
            log.debug("读取默认对话参数失败，将使用模型默认配置: {}", e.getMessage());
            return null;
        }
    }

    private ChatPreset resolveChatPreset(Long sessionId) {
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
            log.debug("读取对话预设失败，将使用默认配置: {}", e.getMessage());
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

    private static Integer parseInt(String raw, Integer def) {
        if (!StringUtils.hasText(raw)) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private static Double parseDouble(String raw, Double def) {
        if (!StringUtils.hasText(raw)) {
            return def;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return def;
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
        log.info("开始删除消息，消息ID: {}", chatMessageDTO.getId());

        try {
            // 验证消息是否存在
            log.debug("验证消息存在性");
            ChatMessage existingMessage = getById(chatMessageDTO.getId());
            if (existingMessage == null) {
                log.warn("尝试删除不存在的消息，消息ID: {}", chatMessageDTO.getId());
                return Result.error("消息不存在");
            }

            log.info("找到待删除消息，会话ID: {}, 消息类型: {}, 创建时间: {}",
                existingMessage.getSessionId(),
                existingMessage.getMessageType() == 0 ? "用户" : "AI",
                existingMessage.getCreatedAt());

            // 验证消息是否属于当前用户（通过检查会话所有权）
            // 这是一个安全检查，防止用户删除其他用户会话中的消息
            // 注意：当前实现允许删除任何存在的消息，在生产环境中应增加更严格的权限验证
            log.debug("验证消息删除权限");

            // 当前版本允许删除任何存在的消息
            // 在更安全的实现中，应在此处验证会话所有权
            log.warn("注意：当前实现允许删除任何存在的消息，建议在生产环境中增加权限验证");

            boolean success = removeById(chatMessageDTO.getId());

            if (success) {
                log.info("消息删除成功，消息ID: {}", chatMessageDTO.getId());
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



