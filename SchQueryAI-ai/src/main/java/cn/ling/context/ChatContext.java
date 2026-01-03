package cn.ling.context;

import cn.ling.domain.pojo.ChatPreset;

/**
 * 聊天上下文 - 用于在请求处理过程中传递预设信息
 */
public class ChatContext {

    private static final ThreadLocal<ChatPreset> PRESET_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前请求的预设配置
     */
    public static void setPreset(ChatPreset preset) {
        PRESET_HOLDER.set(preset);
    }

    /**
     * 获取当前请求的预设配置
     */
    public static ChatPreset getPreset() {
        return PRESET_HOLDER.get();
    }

    /**
     * 清除当前请求的预设配置
     */
    public static void clear() {
        PRESET_HOLDER.remove();
    }
}
