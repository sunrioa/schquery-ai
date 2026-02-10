package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.dto.ChatMessageDTO;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.vo.ChatMessageVO;
import cn.ling.domain.vo.ChatSessionVO;
import cn.ling.domain.vo.SuggestVO;
import cn.ling.service.ChatMessageService;
import cn.ling.service.ChatSessionService;
import cn.ling.service.SuggestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天功能控制器
 * 提供用户聊天会话和消息管理的REST API接口
 * 支持聊天会话的创建、查询、更新、删除以及消息的发送和获取
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class ChatController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private SuggestService suggestService;

    /**
     * 创建新的聊天会话
     * 为当前用户创建一个新的聊天会话，默认会话名称为"新会话"
     *
     * @return 包含新创建会话ID的结果对象
     */
    @RequestMapping("/session/add")
    public Result<String> addSession() {
        return chatSessionService.addSession();
    }

    /**
     * 删除指定的聊天会话
     * 根据会话ID删除用户指定的聊天会话，同时会删除该会话下的所有消息
     *
     * @param chatSessionDTO 包含会话ID的数据传输对象
     * @return 删除操作的结果
     */
    @DeleteMapping("/session/delete")
    public Result<String> deleteSession(@RequestBody ChatSessionDTO chatSessionDTO) {
        return chatSessionService.deleteSession(chatSessionDTO);
    }

    /**
     * 更新聊天会话信息
     * 主要用于更新会话名称，用户可以自定义会话名称便于管理
     *
     * @param chatSessionDTO 包含会话ID和新会话名称的数据传输对象
     * @return 更新操作的结果
     */
    @PostMapping("/session/update")
    public Result<String> updateSession(@RequestBody ChatSessionDTO chatSessionDTO) {
        return chatSessionService.updateSession(chatSessionDTO);
    }

    /**
     * 获取当前用户的所有聊天会话
     * 返回当前用户创建的所有聊天会话列表，按创建时间倒序排列
     *
     * @return 包含会话列表的结果对象
     */
    @RequestMapping("/session/get")
    public Result<List<ChatSessionVO>> getSession() {
        return chatSessionService.getSession();
    }

    /**
     * 获取指定会话的历史消息
     * 根据会话ID获取该会话下的所有消息记录，按时间顺序排列
     *
     * @param sessionId 会话ID
     * @return 包含消息列表的结果对象
     */
    @RequestMapping("/message/get")
    public Result<List<ChatMessageVO>> getMessage(Long sessionId) {
        return chatMessageService.getMessage(sessionId);
    }

    /**
     * 发送消息并获取AI流式响应
     * 接收用户发送的消息，保存到数据库，并通过流式方式返回AI的响应
     * 使用Server-Sent Events (SSE)技术实现实时流式响应
     *
     * @param sessionId 会话ID
     * @param content 消息内容
     * @return ResponseBodyEmitter流式响应对象
     */
    @GetMapping(value = "/message/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseBodyEmitter sendMessage(
            @RequestParam Long sessionId,
            @RequestParam String content) {

        // 创建 ResponseBodyEmitter
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(30 * 60 * 1000L);

        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setSessionId(sessionId);
        dto.setContent(content);

        // 获取 Flux 流
        Flux<String> aiResponseStream = chatMessageService.sendMessage(dto);

        AtomicReference<String> fullResponse = new AtomicReference<>("");
        AtomicLong chunkCount = new AtomicLong(0);

        // 直接订阅并发送，不使用新线程
        aiResponseStream
                .doOnNext(chunk -> {
                    try {
                        long count = chunkCount.incrementAndGet();
                        fullResponse.updateAndGet(prev -> prev + chunk);

                        // 构建 SSE 事件并立即发送
                        String sseEvent = "data: " + chunk + "\n\n";
                        emitter.send(sseEvent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        log.error("发送 SSE 数据失败: {}", e.getMessage(), e);
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(error -> {
                    log.error("AI流式响应发生错误: {}", error.getMessage(), error);
                    emitter.completeWithError(error);
                })
                .doOnComplete(() -> {
                    emitter.complete();
                })
                .subscribe();

        return emitter;
    }

    /**
     * 删除指定的聊天消息
     * 根据消息ID删除用户指定的聊天消息记录
     *
     * @param chatMessageDTO 包含消息ID的数据传输对象
     * @return 删除操作的结果
     */
    @RequestMapping("/message/delete")
    public Result<String> deleteMessage(@RequestBody ChatMessageDTO chatMessageDTO) {
        return chatMessageService.deleteMessage(chatMessageDTO);
    }

    /**
     * 获取追问建议
     * 根据最后一条消息的意图返回预设追问问题
     *
     * @param sessionId 会话ID
     * @return 追问建议列表
     */
    @RequestMapping("/suggest/get")
    public Result<SuggestVO> getSuggest(Long sessionId) {
        try {
            // 获取会话的最后一条用户消息的意图
            // 由于意图识别结果保存在消息处理流程中，这里简化处理
            // 直接基于上下文生成追问建议
            SuggestVO suggest = suggestService.generateSuggestByContext(sessionId);
            return Result.success(suggest);
        } catch (Exception e) {
            log.error("获取追问建议失败: {}", e.getMessage(), e);
            return Result.error("获取追问建议失败");
        }
    }

    /**
     * 根据意图获取追问建议
     * 用于前端在接收AI回复后，基于识别的意图快速返回预设追问
     *
     * @param intent 意图名称（如：专业信息、招生计划等）
     * @return 追问建议列表
     */
    @RequestMapping("/suggest/by-intent")
    public Result<SuggestVO> getSuggestByIntent(@RequestParam String intent) {
        try {
            SuggestVO suggest = suggestService.getSuggestByIntent(intent);
            System.err.println(suggest);
            return Result.success(suggest);
        } catch (Exception e) {
            log.error("根据意图获取追问建议失败: {}", e.getMessage(), e);
            return Result.error("获取追问建议失败");
        }
    }

}