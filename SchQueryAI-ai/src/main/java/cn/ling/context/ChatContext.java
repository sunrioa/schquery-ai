package cn.ling.context;

import cn.ling.domain.pojo.ChatPreset;

/**
 * 聊天上下文 - 用于在请求处理过程中传递预设信息和意图识别结果
 */
public class ChatContext {

    private static final ThreadLocal<ChatPreset> PRESET_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> INTENT_HOLDER = new ThreadLocal<>();

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
     * 设置意图识别结果
     */
    public static void setIntent(String intent) {
        INTENT_HOLDER.set(intent);
    }

    /**
     * 获取意图识别结果
     */
    public static String getIntent() {
        return INTENT_HOLDER.get();
    }

    /**
     * 清除当前请求的所有上下文信息
     */
    public static void clear() {
        PRESET_HOLDER.remove();
        INTENT_HOLDER.remove();
    }
}
