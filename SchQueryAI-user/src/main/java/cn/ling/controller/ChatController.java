package cn.ling.controller;

import cn.ling.Result;
import cn.ling.domain.dto.ChatMessageDTO;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.vo.ChatMessageVO;
import cn.ling.domain.vo.ChatSessionVO;
import cn.ling.service.ChatMessageService;
import cn.ling.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.util.List;

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
     * @return Flux流式响应对象，逐步返回AI生成的内容
     */
    @GetMapping(value = "/message/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(@RequestParam Long sessionId, @RequestParam String content) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setSessionId(sessionId);
        dto.setContent(content);
        return chatMessageService.sendMessage(dto);
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

}