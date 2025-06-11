package cn.apmen.jsagent.framework.plugin;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import lombok.Builder;
import lombok.Data;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件上下文
 * 为插件提供必要的运行环境和依赖
 */
@Data
@Builder
public class PluginContext {
    
    /**
     * Spring应用上下文
     */
    private ApplicationContext applicationContext;
    
    /**
     * 工具注册器
     */
    private ToolRegistry toolRegistry;
    
    /**
     * 会话服务
     */
    private ConversationService conversationService;
    
    /**
     * 插件配置
     */
    @Builder.Default
    private Map<String, Object> configuration = new ConcurrentHashMap<>();
    
    /**
     * 插件共享数据
     */
    @Builder.Default
    private Map<String, Object> sharedData = new ConcurrentHashMap<>();
    
    /**
     * 获取配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfiguration(String key, Class<T> type) {
        Object value = configuration.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 获取配置值（带默认值）
     */
    public <T> T getConfiguration(String key, Class<T> type, T defaultValue) {
        T value = getConfiguration(key, type);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 设置配置值
     */
    public void setConfiguration(String key, Object value) {
        configuration.put(key, value);
    }
    
    /**
     * 获取共享数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key, Class<T> type) {
        Object value = sharedData.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 设置共享数据
     */
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }
    
    /**
     * 获取Spring Bean
     */
    public <T> T getBean(Class<T> type) {
        if (applicationContext != null) {
            return applicationContext.getBean(type);
        }
        return null;
    }
    
    /**
     * 获取Spring Bean（按名称）
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name, Class<T> type) {
        if (applicationContext != null) {
            return (T) applicationContext.getBean(name);
        }
        return null;
    }
}