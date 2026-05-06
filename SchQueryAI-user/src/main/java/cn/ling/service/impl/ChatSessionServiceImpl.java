package cn.ling.service.impl;

import cn.ling.exception.CustomException;
import cn.ling.service.ChatMessageService;
import cn.ling.service.ConversationMemoryService;
import cn.ling.utils.ContextUtils;
import cn.ling.Result;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.pojo.ChatSession;
import cn.ling.domain.vo.ChatSessionVO;
import cn.ling.mapper.ChatSessionMapper;
import cn.ling.service.ChatSessionService;
import cn.ling.service.SysConfigService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 聊天会话服务实现类
 * 管理用户与AI/客服的对话会话，包括会话的创建、更新、删除和查询功能
 * 每个会话对应一个用户的连续对话记录
 */
@Slf4j // 启用SLF4J日志功能
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
    implements ChatSessionService{

    private static final String KEY_DEFAULT_PRESET_ID = "chat.preset.defaultId";
    private static final String KEY_WELCOME_MESSAGE = "chat.default.message";
    private static final String DEFAULT_SESSION_NAME = "新会话";
    private static final int MAX_AUTO_SESSION_NAME_LENGTH = 12;
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(你好|您好|hello|hi|哈喽|嗨|在吗|有人吗|早上好|中午好|下午好|晚上好)[!！,，。.？?~～ ]*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SCHOOL_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{2,30}(大学|学院|学校))");
    private static final Pattern MAJOR_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{2,20}专业)");

    @Resource
    private SysConfigService sysConfigService;

    @Lazy
    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ConversationMemoryService conversationMemoryService;

    @Resource(name = "sessionTitleChatClient")
    private ChatClient sessionTitleChatClient;

    /**
     * 创建新的聊天会话
     * 为当前登录用户创建一个新的对话会话，默认会话名称为"新会话"
     *
     * @return 包含新创建会话ID的结果对象
     */
    @Override
    public Result<String> addSession() {
        log.info("开始创建新的聊天会话");

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 创建新的聊天会话对象
            Date currentTime = new Date();
            ChatSession chatSession = new ChatSession();
            chatSession.setUserId(userId);
            chatSession.setPresetId(parseLong(sysConfigService.getConfigValue(KEY_DEFAULT_PRESET_ID)));
            chatSession.setSessionName(DEFAULT_SESSION_NAME);
            chatSession.setStatus(1); // 活跃状态
            chatSession.setCreatedAt(currentTime);
            chatSession.setUpdatedAt(currentTime);
            chatSession.setLastMessageAt(currentTime);

            // 保存到数据库
            log.debug("开始保存会话到数据库");
            boolean success = save(chatSession);

            if (success) {
                conversationMemoryService.clearMemory(chatSession.getId());
                log.info("聊天会话创建成功，会话ID: {}, 用户ID: {}", chatSession.getId(), userId);

                // 添加欢迎消息
                addWelcomeMessage(chatSession.getId());

                return Result.success(chatSession.getId().toString());
            } else {
                log.error("聊天会话创建失败，用户ID: {}", userId);
                return Result.error("创建会话失败");
            }
        } catch (Exception e) {
            log.error("创建聊天会话时发生异常: {}", e.getMessage(), e);
            throw CustomException.error("创建会话失败：" + e.getMessage());
        }
    }

    /**
     * 删除聊天会话
     * 执行软删除操作，将会话状态设置为0，保留历史记录但不显示
     *
     * @param chatSessionDTO 包含会话ID的数据传输对象
     * @return 删除操作的结果对象
     */
    @Override
    public Result<String> deleteSession(ChatSessionDTO chatSessionDTO) {
        log.info("开始删除聊天会话，会话ID: {}", chatSessionDTO.getId());

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 验证会话是否存在且属于当前用户
            log.debug("验证会话存在性和权限");
            ChatSession existingSession = getById(chatSessionDTO.getId());
            if (existingSession == null) {
                log.warn("会话不存在，会话ID: {}", chatSessionDTO.getId());
                return Result.error("会话不存在");
            }

            if (!existingSession.getUserId().equals(userId)) {
                log.warn("用户无权限删除此会话，用户ID: {}, 会话所有者ID: {}", userId, existingSession.getUserId());
                return Result.error("无权限删除此会话");
            }

            // 软删除：设置状态为0
            log.debug("执行软删除操作");
            existingSession.setStatus(0); // 非活跃状态
            existingSession.setUpdatedAt(new Date());

            boolean success = updateById(existingSession);

            if (success) {
                conversationMemoryService.clearMemory(chatSessionDTO.getId());
                log.info("聊天会话删除成功，会话ID: {}, 用户ID: {}", chatSessionDTO.getId(), userId);
                return Result.success();
            } else {
                log.error("聊天会话删除失败，会话ID: {}, 用户ID: {}", chatSessionDTO.getId(), userId);
                return Result.error("删除会话失败");
            }
        } catch (Exception e) {
            log.error("删除聊天会话时发生异常，会话ID: {}, 异常信息: {}", chatSessionDTO.getId(), e.getMessage(), e);
            throw CustomException.error("删除会话失败：" + e.getMessage());
        }
    }

    /**
     * 更新聊天会话
     * 允许用户修改会话名称，会话的其他属性保持不变
     *
     * @param chatSessionDTO 包含会话ID和新会话名称的数据传输对象
     * @return 更新操作的结果对象
     */
    @Override
    public Result<String> updateSession(ChatSessionDTO chatSessionDTO) {
        log.info("开始更新聊天会话，会话ID: {}, 新会话名称: {}", chatSessionDTO.getId(), chatSessionDTO.getSessionName());

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 验证会话是否存在且属于当前用户
            log.debug("验证会话存在性和权限");
            ChatSession existingSession = getById(chatSessionDTO.getId());
            if (existingSession == null) {
                log.warn("会话不存在，会话ID: {}", chatSessionDTO.getId());
                throw CustomException.error("会话不存在");
            }

            if (!existingSession.getUserId().equals(userId)) {
                log.warn("用户无权限修改此会话，用户ID: {}, 会话所有者ID: {}", userId, existingSession.getUserId());
                throw CustomException.error("无权限修改此会话");
            }

            boolean changed = false;

            if (StringUtils.hasText(chatSessionDTO.getSessionName())) {
                String newName = chatSessionDTO.getSessionName().trim();
                log.debug("更新会话名称从 '{}' 到 '{}'", existingSession.getSessionName(), newName);
                existingSession.setSessionName(newName);
                changed = true;
            }

            if (chatSessionDTO.getPresetId() != null) {
                existingSession.setPresetId(chatSessionDTO.getPresetId());
                changed = true;
            }

            if (!changed) {
                return Result.success();
            }
            existingSession.setUpdatedAt(new Date());

            boolean success = updateById(existingSession);

            if (success) {
                log.info("聊天会话更新成功，会话ID: {}, 用户ID: {}, 新名称: {}",
                    chatSessionDTO.getId(), userId, chatSessionDTO.getSessionName());
                return Result.success();
            } else {
                log.error("聊天会话更新失败，会话ID: {}, 用户ID: {}", chatSessionDTO.getId(), userId);
                return Result.error("更新会话失败");
            }
        } catch (Exception e) {
            log.error("更新聊天会话时发生异常，会话ID: {}, 异常信息: {}", chatSessionDTO.getId(), e.getMessage(), e);
            throw CustomException.error("更新会话失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前用户的所有聊天会话
     * 查询当前用户的所有活跃会话，按最后消息时间降序排列
     *
     * @return 包含会话列表的结果对象，如果没有会话则返回null
     */
    @Override
    public Result<List<ChatSessionVO>> getSession() {
        log.info("开始获取当前用户的聊天会话列表");

        try {
            // 从上下文中获取当前用户ID
            Long userId = ContextUtils.getUserId();
            log.debug("获取到当前用户ID: {}", userId);

            // 查询当前用户的所有活跃会话
            log.debug("查询用户 {} 的活跃会话列表", userId);
            List<ChatSession> sessions = lambdaQuery()
                    .eq(ChatSession::getUserId, userId)
                    .eq(ChatSession::getStatus, 1) // 仅活跃状态
                    .orderByDesc(ChatSession::getLastMessageAt).list();

            if (sessions != null && !sessions.isEmpty()) {
                log.info("查询到 {} 个活跃会话", sessions.size());

                // 转换为VO对象
                log.debug("开始转换为VO对象");
                List<ChatSessionVO> sessionVOs = sessions.stream()
                    .map(session -> {
                        ChatSessionVO vo = new ChatSessionVO();
                        vo.setId(session.getId());
                        vo.setSessionName(session.getSessionName());
                        vo.setPresetId(session.getPresetId());
                        vo.setLastMessageAt(session.getLastMessageAt());
                        return vo;
                    })
                    .collect(Collectors.toList());

                log.info("成功获取用户 {} 的 {} 个会话", userId, sessionVOs.size());
                return Result.success(sessionVOs);
            } else {
                log.info("用户 {} 没有活跃的聊天会话", userId);
                return Result.success(null);
            }
        } catch (Exception e) {
            log.error("获取聊天会话列表时发生异常: {}", e.getMessage(), e);
            throw CustomException.error("获取会话列表失败：" + e.getMessage());
        }
    }

    /**
     * 更新会话的最后消息时间
     * 在有新消息发送时调用此方法，用于保持会话列表的实时排序
     *
     * @param sessionId 会话ID
     */
    @Override
    public void updateLastMessageTime(Long sessionId) {
        log.debug("开始更新会话 {} 的最后消息时间", sessionId);

        try {
            ChatSession session = getById(sessionId);
            if (session != null) {
                Date currentTime = new Date();
                log.debug("更新会话 {} 的最后消息时间为 {}", sessionId, currentTime);

                session.setLastMessageAt(currentTime);
                session.setUpdatedAt(currentTime);

                boolean success = updateById(session);
                if (success) {
                    log.debug("会话 {} 的最后消息时间更新成功", sessionId);
                } else {
                    log.warn("会话 {} 的最后消息时间更新失败", sessionId);
                }
            } else {
                log.warn("尝试更新不存在的会话 {} 的最后消息时间", sessionId);
            }
        } catch (Exception e) {
            log.error("更新会话 {} 最后消息时间时发生异常: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public void refreshSessionNameFromFirstQuestion(Long sessionId, String firstQuestion) {
        if (sessionId == null || !StringUtils.hasText(firstQuestion)) {
            return;
        }

        try {
            ChatSession session = getById(sessionId);
            if (session == null) {
                return;
            }

            if (!isDefaultSessionName(session.getSessionName())) {
                return;
            }

            long userMessageCount = chatMessageService.lambdaQuery()
                    .eq(cn.ling.domain.pojo.ChatMessage::getSessionId, sessionId)
                    .eq(cn.ling.domain.pojo.ChatMessage::getMessageType, 0)
                    .count();
            if (userMessageCount != 1) {
                return;
            }

            String summarizedName = generateSessionTitle(firstQuestion);
            if (!StringUtils.hasText(summarizedName) || DEFAULT_SESSION_NAME.equals(summarizedName)) {
                return;
            }

            session.setSessionName(summarizedName);
            session.setUpdatedAt(new Date());
            updateById(session);
        } catch (Exception e) {
            log.warn("根据首问自动更新会话标题失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    private boolean isDefaultSessionName(String sessionName) {
        return !StringUtils.hasText(sessionName) || DEFAULT_SESSION_NAME.equals(sessionName.trim());
    }

    private String generateSessionTitle(String firstQuestion) {
        String normalized = normalizeQuestion(firstQuestion);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_SESSION_NAME;
        }

        try {
            String prompt = """
                    用户首问：
                    %s

                    请直接输出一个会话标题：
                    """.formatted(normalized);

            String aiTitle = sessionTitleChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            String sanitizedTitle = sanitizeSessionTitle(aiTitle);
            if (StringUtils.hasText(sanitizedTitle)) {
                return sanitizedTitle;
            }
        } catch (Exception e) {
            log.warn("AI 生成会话标题失败，回退本地规则: {}", e.getMessage());
        }

        return summarizeSessionNameFallback(firstQuestion);
    }

    private String summarizeSessionNameFallback(String firstQuestion) {
        String normalized = normalizeQuestion(firstQuestion);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_SESSION_NAME;
        }

        if (GREETING_PATTERN.matcher(normalized).matches()) {
            return "简单的问候";
        }

        String schoolName = findMatchedGroup(SCHOOL_PATTERN, normalized);
        String majorName = findMatchedGroup(MAJOR_PATTERN, normalized);

        if (containsAny(normalized, "录取分数", "分数线", "位次", "投档", "录取")) {
            return limitSessionName(StringUtils.hasText(schoolName) ? schoolName + "录取咨询" : "录取分数咨询");
        }
        if (containsAny(normalized, "学费", "住宿", "宿舍", "奖学金", "助学金", "生活费")) {
            return limitSessionName(StringUtils.hasText(schoolName) ? schoolName + "生活费用" : "费用与生活咨询");
        }
        if (StringUtils.hasText(majorName) && containsAny(normalized, "介绍", "怎么样", "就业", "课程", "前景", "学什么", "好不好")) {
            return limitSessionName(majorName + "咨询");
        }
        if (StringUtils.hasText(schoolName) && containsAny(normalized, "介绍", "简介", "了解", "怎么样", "是什么", "招生", "地址", "官网", "专业")) {
            return limitSessionName(schoolName + "介绍");
        }

        String stripped = stripQuestionPrefix(normalized);
        if (!StringUtils.hasText(stripped)) {
            return DEFAULT_SESSION_NAME;
        }
        return limitSessionName(stripped);
    }

    private String sanitizeSessionTitle(String aiTitle) {
        if (!StringUtils.hasText(aiTitle)) {
            return null;
        }

        String normalized = aiTitle
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        normalized = normalized.replaceFirst("^(标题|会话标题)[:：]\\s*", "");
        normalized = normalized.replaceAll("^[\"'“”‘’《》【】\\[\\]（）()]+", "");
        normalized = normalized.replaceAll("[\"'“”‘’《》【】\\[\\]（）()]+$", "");
        normalized = normalized.replaceAll("[。！？!?,，；;：:]+$", "");
        normalized = normalized.trim();

        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() < 2) {
            return null;
        }
        return limitSessionName(normalized);
    }

    private String normalizeQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        return question
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("`+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String stripQuestionPrefix(String text) {
        String stripped = text;
        String[] prefixes = {
                "请问一下", "请问", "我想问一下", "我想问", "想问一下", "想问",
                "帮我看看", "帮我介绍一下", "帮我介绍", "麻烦帮我", "麻烦", "请你", "介绍一下", "介绍"
        };
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : prefixes) {
                if (stripped.startsWith(prefix)) {
                    stripped = stripped.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        }

        stripped = stripped.replaceAll("[？?！!。；;，,：:、~～]+$", "").trim();
        return stripped;
    }

    private String findMatchedGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String limitSessionName(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_SESSION_NAME;
        }
        String normalized = value.trim();
        if (normalized.length() <= MAX_AUTO_SESSION_NAME_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_AUTO_SESSION_NAME_LENGTH);
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
     * 添加欢迎消息到新创建的会话
     *
     * @param sessionId 会话ID
     */
    private void addWelcomeMessage(Long sessionId) {
        try {
            // 从数据库配置读取欢迎消息，如果未配置则使用默认值
            String welcomeContent = sysConfigService.getConfigValue(KEY_WELCOME_MESSAGE);
            if (!StringUtils.hasText(welcomeContent)) {
                welcomeContent =
                        """
                                # 欢迎使用 SchQueryAI 广州航海学院智能招生助手！
                        
                                我是专为广州航海学院打造的智能招生服务助手，能为你提供以下核心支持：
                        
                                - **招生政策解读**：最新招生计划、报考条件、录取规则等权威信息
                                - **专业深度介绍**：特色专业、课程设置、师资力量、就业前景等详细解析
                                - **志愿填报指导**：结合分数与兴趣，提供科学的志愿填报建议
                                - **校园生活咨询**：校园设施、住宿条件、奖助政策、社团活动等全方位介绍
                                - **职业规划参考**：航海类专业及其他专业的行业发展趋势与就业方向
                        
                                无论你是想了解报考流程、专业选择，还是对未来的职业发展有疑问，都可以随时向我提问。我会以专业、准确的信息，助力你做出最适合自己的选择。
                        """;
            }

            cn.ling.domain.pojo.ChatMessage welcomeMessage = new cn.ling.domain.pojo.ChatMessage();
            welcomeMessage.setSessionId(sessionId);
            welcomeMessage.setMessageType(1); // AI消息
            welcomeMessage.setCreatedAt(new Date());
            welcomeMessage.setContent(welcomeContent);

            chatMessageService.save(welcomeMessage);
            log.debug("欢迎消息添加成功，会话ID: {}", sessionId);
        } catch (Exception e) {
            log.error("添加欢迎消息失败，会话ID: {}, 异常信息: {}", sessionId, e.getMessage(), e);
        }
    }
}




