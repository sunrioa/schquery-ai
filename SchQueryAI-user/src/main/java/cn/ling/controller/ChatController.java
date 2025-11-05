package cn.ling.controller;

import cn.ling.domain.Result;
import cn.ling.domain.dto.ChatMessageDTO;
import cn.ling.domain.dto.ChatSessionDTO;
import cn.ling.domain.vo.ChatMessageVO;
import cn.ling.domain.vo.ChatSessionVO;
import cn.ling.service.ChatMessageService;
import cn.ling.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/user")
public class ChatController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    //新建chatsession
    @RequestMapping("/session/add")
    public Result<String> addSession() {
        return chatSessionService.addSession();
    }

    //删除chatsession
    @DeleteMapping("/session/delete")
    public Result<String> deleteSession(@RequestBody ChatSessionDTO chatSessionDTO) {
        return chatSessionService.deleteSession(chatSessionDTO);
    }

    //修改chatsession
    @PostMapping("/session/update")
    public Result<String> updateSession(@RequestBody ChatSessionDTO chatSessionDTO) {
        return chatSessionService.updateSession(chatSessionDTO);
    }

    //获取chatsession
    @RequestMapping("/session/get")
    public Result<List<ChatSessionVO>> getSession() {
        return chatSessionService.getSession();
    }


    //获取历史消息
    @RequestMapping("/message/get")
    public Result<List<ChatMessageVO>> getMessage(Long sessionId) {
        return chatMessageService.getMessage(sessionId);
    }

    //发送消息 (流式响应)
    @GetMapping(value = "/message/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(@RequestParam Long sessionId, @RequestParam String content) {
        ChatMessageDTO dto = new ChatMessageDTO();
        dto.setSessionId(sessionId);
        dto.setContent(content);
        return chatMessageService.sendMessage(dto);
    }

    //删除消息
    @RequestMapping("/message/delete")
    public Result<String> deleteMessage(@RequestBody ChatMessageDTO chatMessageDTO) {
        return chatMessageService.deleteMessage(chatMessageDTO);
    }

}
