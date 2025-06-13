package cn.apmen.jsagent.example.configuration;

import cn.apmen.jsagent.framework.tool.ToolRegistry;
import cn.apmen.jsagent.example.tools.CalculatorTool;
import cn.apmen.jsagent.example.tools.WeatherTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 工具配置类 - 负责注册所有工具到ToolRegistry
 */
@Component
@Slf4j
public class ToolConfig implements CommandLineRunner {

    @Autowired
    private ToolRegistry toolRegistry;
    
    @Autowired
    private CalculatorTool calculatorTool;
    
    @Autowired
    private WeatherTool weatherTool;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始注册工具...");
        
        // 注册计算器工具
        toolRegistry.registerExecutor(calculatorTool);
        log.info("已注册工具: {}", calculatorTool.getToolName());
        
        // 注册天气查询工具
        toolRegistry.registerExecutor(weatherTool);
        log.info("已注册工具: {}", weatherTool.getToolName());
        
        log.info("工具注册完成，共注册 {} 个工具", toolRegistry.getRegisteredToolNames().size());
        log.info("已注册的工具列表: {}", toolRegistry.getRegisteredToolNames());
    }
}