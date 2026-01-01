package cn.ling.service.impl;

import cn.ling.domain.pojo.ChatPreset;
import cn.ling.mapper.ChatPresetMapper;
import cn.ling.service.ChatPresetService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话预设（角色/参数预设）Service 实现
 */
@Slf4j
@Service
public class ChatPresetServiceImpl extends ServiceImpl<ChatPresetMapper, ChatPreset> implements ChatPresetService {
}

