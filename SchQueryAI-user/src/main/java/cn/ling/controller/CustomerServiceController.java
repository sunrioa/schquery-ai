package cn.ling.controller;

import cn.ling.dto.CustomerServiceDTO;
import cn.ling.domain.vo.CustomerServiceVO;
import cn.ling.service.CustomerServiceService;
import cn.ling.utils.Response;
import cn.ling.utils.JwtUtils;
import cn.ling.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;

/**
 * 客服消息管理控制器
 */
@RestController
@RequestMapping("/customer-service")
public class CustomerServiceController {

    @Autowired
    private CustomerServiceService customerServiceService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 用户发送客服消息
     */
    @PostMapping("/send")
    public Response<?> sendMessage(
            @RequestHeader("Authorization") String token,
            @RequestBody CustomerServiceDTO dto
    ) {
        try {
            // 从token中提取用户ID
            String jwt = token.replace("Bearer ", "");
            HashMap<String, Object> claims = JwtUtils.getAllClaimsAsMap(jwt);
            Long userId = Long.parseLong(claims.get("userId").toString());
            dto.setUserId(userId);
            dto.setSenderType(1);  // 1-用户

            // 调用服务发送消息
            Result<Long> result = customerServiceService.sendMessage(dto);
            if (result.getCode() == 200) {
                return Response.success(result.getData());
            } else {
                return Response.error(result.getCode(), result.getMsg());
            }
        } catch (Exception e) {
            return Response.error("发送消息失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户的客服消息历史
     */
    @GetMapping("/history")
    public Response<?> getMessageHistory(
            @RequestHeader("Authorization") String token
    ) {
        try {
            // 从token中提取用户ID
            String jwt = token.replace("Bearer ", "");
            HashMap<String, Object> claims = JwtUtils.getAllClaimsAsMap(jwt);
            Long userId = Long.parseLong(claims.get("userId").toString());

            // 调用服务获取消息历史
            Result<java.util.List<CustomerServiceVO>> result = customerServiceService.getMessagesByUserId(userId);
            if (result.getCode() == 200) {
                return Response.success(result.getData());
            } else {
                return Response.error(result.getCode(), result.getMsg());
            }
        } catch (Exception e) {
            return Response.error("获取消息历史失败：" + e.getMessage());
        }
    }

    /**
     * 管理员回复客服消息
     */
    @PostMapping("/reply")
    public Response<?> replyMessage(
            @RequestHeader("Authorization") String token,
            @RequestBody CustomerServiceDTO dto
    ) {
        try {
            // 从token中提取管理员ID
            String jwt = token.replace("Bearer ", "");
            HashMap<String, Object> claims = JwtUtils.getAllClaimsAsMap(jwt);
            Long adminId = Long.parseLong(claims.get("userId").toString());
            dto.setSenderId(adminId);  // 发送者ID为管理员ID
            dto.setSenderType(2);  // 2-管理员

            // 调用服务发送回复
            Result<Long> result = customerServiceService.replyMessage(dto);
            if (result.getCode() == 200) {
                return Response.success(result.getData());
            } else {
                return Response.error(result.getCode(), result.getMsg());
            }
        } catch (Exception e) {
            return Response.error("发送回复失败：" + e.getMessage());
        }
    }

    /**
     * 获取待处理的客服会话（仅管理员）
     */
    @GetMapping("/pending-sessions")
    public Response<?> getPendingSessions(
            @RequestHeader("Authorization") String token
    ) {
        try {
            // 调用服务获取待处理的会话
            Result<java.util.List<CustomerServiceVO.UserSessionVO>> result = customerServiceService.getPendingSessions();
            if (result.getCode() == 200) {
                return Response.success(result.getData());
            } else {
                return Response.error(result.getCode(), result.getMsg());
            }
        } catch (Exception e) {
            return Response.error("获取待处理会话失败：" + e.getMessage());
        }
    }

    /**
     * 获取客服统计信息
     */
    @GetMapping("/stats")
    public Response<?> getStats(
            @RequestHeader("Authorization") String token
    ) {
        try {
            // 调用服务获取统计信息
            Result<CustomerServiceVO.StatsVO> result = customerServiceService.getStats();
            if (result.getCode() == 200) {
                return Response.success(result.getData());
            } else {
                return Response.error(result.getCode(), result.getMsg());
            }
        } catch (Exception e) {
            return Response.error("获取统计信息失败：" + e.getMessage());
        }
    }

    /**
     * 标记消息为已读
     */
    @PutMapping("/{messageId}/mark-read")
    public Response<?> markAsRead(
            @PathVariable Long messageId,
            @RequestHeader("Authorization") String token
    ) {
        try {
            Result<String> result = customerServiceService.markAsRead(messageId);
            if (result.getCode() == 200) {
                return Response.success(result.getData());
            } else {
                return Response.error(result.getCode(), result.getMsg());
            }
        } catch (Exception e) {
            return Response.error("标记失败：" + e.getMessage());
        }
    }

    /**
     * 完成客服会话
     */
    @PutMapping("/session/{sessionId}/complete")
    public Response<?> completeSession(
            @PathVariable Long sessionId,
            @RequestHeader("Authorization") String token
    ) {
        try {
            Result<String> result = customerServiceService.completeSession(sessionId);
            if (result.getCode() == 200) {
                return Response.success(result.getData());
            } else {
                return Response.error(result.getCode(), result.getMsg());
            }
        } catch (Exception e) {
            return Response.error("完成会话失败：" + e.getMessage());
        }
    }
}
