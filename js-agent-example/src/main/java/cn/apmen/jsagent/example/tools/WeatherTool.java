package cn.apmen.jsagent.example.tools;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.AbstractToolExecutor;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 天气查询工具 - 模拟天气查询功能
 */
@Component
@Slf4j
public class WeatherTool extends AbstractToolExecutor {

    private final Random random = new Random();
    
    // 模拟天气数据
    private final Map<String, String[]> cityWeatherData = new HashMap<>();
    
    public WeatherTool() {
        // 初始化一些城市的模拟天气数据
        cityWeatherData.put("北京", new String[]{"晴天", "多云", "小雨", "雾霾"});
        cityWeatherData.put("上海", new String[]{"多云", "小雨", "晴天", "阴天"});
        cityWeatherData.put("广州", new String[]{"晴天", "多云", "雷阵雨", "高温"});
        cityWeatherData.put("深圳", new String[]{"晴天", "多云", "雷阵雨", "台风"});
        cityWeatherData.put("杭州", new String[]{"多云", "小雨", "晴天", "阴天"});
    }

    @Override
    public String getToolName() {
        return "weather_query";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气情况";
    }

    @Override
    public Map<String, Object> getParametersDefinition() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
            
        Map<String, Object> properties = new HashMap<>();
            
        // city 参数定义
        Map<String, Object> cityProperty = new HashMap<>();
        cityProperty.put("type", "string");
        cityProperty.put("description", "要查询天气的城市名称，例如：北京、上海、广州");
        properties.put("city", cityProperty);
            
        parameters.put("properties", properties);
        parameters.put("required", new String[]{"city"});
            
        return parameters;
    }
            
    @Override
    public String[] getRequiredParameters() {
        return new String[]{"city"};
    }

    @Override
    protected Mono<ToolResult> doExecute(ToolCall toolCall, ToolContext context, Map<String, Object> arguments) {
        String city = getStringParameter(arguments, "city");
        log.info("查询城市天气: {}", city);

        // 模拟天气查询
        String weather = getWeatherForCity(city);
        int temperature = 15 + random.nextInt(20); // 15-35度随机温度

        String weatherReport = String.format(
            "城市: %s\n天气: %s\n温度: %d°C\n湿度: %d%%\n风力: %d级",
            city, weather, temperature,
            40 + random.nextInt(40), // 40-80%湿度
            1 + random.nextInt(5)    // 1-5级风力
        );

        return Mono.just(success(toolCall.getId(), weatherReport));
    }

    /**
     * 获取城市天气（模拟）
     */
    private String getWeatherForCity(String city) {
        String[] weatherOptions = cityWeatherData.get(city);
        if (weatherOptions == null) {
            // 如果城市不在预设列表中，返回随机天气
            weatherOptions = new String[]{"晴天", "多云", "小雨", "阴天"};
        }
        
        return weatherOptions[random.nextInt(weatherOptions.length)];
    }
}