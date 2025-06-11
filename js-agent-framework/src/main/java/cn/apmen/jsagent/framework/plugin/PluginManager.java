package cn.apmen.jsagent.framework.plugin;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 插件管理器
 * 负责插件的注册、生命周期管理和协调
 */
@Slf4j
public class PluginManager {
    private final List<AgentPlugin> plugins = new CopyOnWriteArrayList<>();
    private final Map<String, AgentPlugin> pluginMap = new ConcurrentHashMap<>();
    private final PluginContext pluginContext;

    public PluginManager(ToolRegistry toolRegistry,
                        ConversationService conversationService) {
        this.pluginContext = PluginContext.builder()
                .toolRegistry(toolRegistry)
                .conversationService(conversationService)
                .build();
    }
    /**
     * 注册插件
     */
    public Mono<Void> registerPlugin(AgentPlugin plugin) {
        return Mono.fromRunnable(() -> {
            if (pluginMap.containsKey(plugin.getName())) {
                log.warn("Plugin {} already registered, skipping", plugin.getName());
                return;
            }
            plugins.add(plugin);
            pluginMap.put(plugin.getName(), plugin);
            // 按优先级排序
            plugins.sort(Comparator.comparingInt(AgentPlugin::getPriority));

            log.info("Registered plugin: {} v{} with priority {}",
                plugin.getName(), plugin.getVersion(), plugin.getPriority());
        });
    }
    /**
     * 批量注册插件
     */
    public Mono<Void> registerPlugins(List<AgentPlugin> pluginList) {
        return Flux.fromIterable(pluginList)
                .flatMap(this::registerPlugin)
                .then();
    }
    /**
     * 移除插件
     */
    public Mono<Void> unregisterPlugin(String pluginName) {
        return Mono.fromRunnable(() -> {
            AgentPlugin plugin = pluginMap.remove(pluginName);
            if (plugin != null) {
                plugins.remove(plugin);
                log.info("Unregistered plugin: {}", pluginName);
            }
        });
    }
    /**
     * 初始化所有插件
     */
    public Mono<Void> initializeAllPlugins() {
        log.info("Initializing {} plugins", plugins.size());
        return Flux.fromIterable(plugins)
                .flatMap(plugin -> initializePlugin(plugin)
                    .onErrorResume(error -> {
                        log.error("Failed to initialize plugin {}: {}", plugin.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then()
                .doOnSuccess(v -> log.info("All plugins initialized"));
    }
    /**
     * 启动所有插件
     */
    public Mono<Void> startAllPlugins() {
        log.info("Starting {} plugins", plugins.size());
        return Flux.fromIterable(plugins)
                .flatMap(plugin -> startPlugin(plugin)
                    .onErrorResume(error -> {
                        log.error("Failed to start plugin {}: {}", plugin.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then()
                .doOnSuccess(v -> log.info("All plugins started"));
    }
    /**
     * 停止所有插件
     */
    public Mono<Void> stopAllPlugins() {
        log.info("Stopping {} plugins", plugins.size());
        // 按相反顺序停止插件
        List<AgentPlugin> reversedPlugins = plugins.stream()
                .sorted(Comparator.comparingInt(AgentPlugin::getPriority).reversed())
                .toList();
        return Flux.fromIterable(reversedPlugins)
                .flatMap(plugin -> stopPlugin(plugin)
                    .onErrorResume(error -> {
                        log.error("Failed to stop plugin {}: {}", plugin.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then()
                .doOnSuccess(v -> log.info("All plugins stopped"));
    }
    /**
     * 销毁所有插件
     */
    public Mono<Void> destroyAllPlugins() {
        log.info("Destroying {} plugins", plugins.size());
        // 按相反顺序销毁插件
        List<AgentPlugin> reversedPlugins = plugins.stream()
                .sorted(Comparator.comparingInt(AgentPlugin::getPriority).reversed())
                .toList();
        return Flux.fromIterable(reversedPlugins)
                .flatMap(plugin -> destroyPlugin(plugin)
                    .onErrorResume(error -> {
                        log.error("Failed to destroy plugin {}: {}", plugin.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then()
                .doOnSuccess(v -> log.info("All plugins destroyed"));
    }
    /**
     * 初始化单个插件
     */
    public Mono<Void> initializePlugin(AgentPlugin plugin) {
        return plugin.initialize(pluginContext)
                .doOnSuccess(v -> log.debug("Plugin {} initialized", plugin.getName()))
                .doOnError(error -> log.error("Plugin {} initialization failed", plugin.getName(), error));
    }
    /**
     * 启动单个插件
     */
    public Mono<Void> startPlugin(AgentPlugin plugin) {
        return plugin.start(pluginContext)
                .doOnSuccess(v -> log.debug("Plugin {} started", plugin.getName()))
                .doOnError(error -> log.error("Plugin {} start failed", plugin.getName(), error));
    }
    /**
     * 停止单个插件
     */
    public Mono<Void> stopPlugin(AgentPlugin plugin) {
        return plugin.stop(pluginContext)
                .doOnSuccess(v -> log.debug("Plugin {} stopped", plugin.getName()))
                .doOnError(error -> log.error("Plugin {} stop failed", plugin.getName(), error));
    }
    /**
     * 销毁单个插件
     */
    public Mono<Void> destroyPlugin(AgentPlugin plugin) {
        return plugin.destroy(pluginContext)
                .doOnSuccess(v -> log.debug("Plugin {} destroyed", plugin.getName()))
                .doOnError(error -> log.error("Plugin {} destroy failed", plugin.getName(), error));
    }
    /**
     * 获取插件
     */
    public AgentPlugin getPlugin(String name) {
        return pluginMap.get(name);
    }
    /**
     * 获取支持指定Agent的插件
     */
    public List<AgentPlugin> getPluginsForAgent(String agentId) {
        return plugins.stream()
                .filter(plugin -> plugin.supports(agentId))
                .toList();
    }
    /**
     * 获取所有插件名称
     */
    public List<String> getAllPluginNames() {
        return plugins.stream()
                .map(AgentPlugin::getName)
                .toList();
    }
    /**
     * 获取插件状态信息
     */
    public Map<String, AgentPlugin.PluginStatus> getPluginStatuses() {
        Map<String, AgentPlugin.PluginStatus> statuses = new ConcurrentHashMap<>();
        plugins.forEach(plugin -> statuses.put(plugin.getName(), plugin.getStatus()));
        return statuses;
    }
    /**
     * 设置插件配置
     */
    public void setPluginConfiguration(String key, Object value) {
        pluginContext.setConfiguration(key, value);
    }
    /**
     * 获取插件上下文
     */
    public PluginContext getPluginContext() {
        return pluginContext;
    }
}