package cn.apmen.jsagent.example.configuration;

import cn.apmen.jsagent.framework.mcp.MCPTool;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class CommonToolConfiguration {

    /**
     * 创建Bing搜索MCPTool - 直接输出给用户
     */
    @Bean
    public MCPTool bingSearchMCPTool(McpSyncClient bingSearchMCPClient) {
        String description = """
            Bing网页搜索工具，支持以下工具：

            1. bing_search - Bing网页搜索
               参数: {
                 query: "搜索关键词",
                 top_k: 搜索结果数量(1-10，默认3),
                 is_fast: 是否快速模式(true/false，默认true)
               }

            使用方式: tool_name指定为"bing_search"，arguments包含搜索参数
            注意：快速模式返回摘要，非快速模式可获取完整网页内容
            """;

        return new MCPTool(bingSearchMCPClient, "bing_search", description);
    }

    /**
     * 创建高德地图MCPTool - 提供地图相关服务
     * 基于实际MCP服务器返回的工具列表创建
     */
    @Bean
    public MCPTool amapMCPTool(McpSyncClient amapMCPClient) {
        String description = """
            高德地图服务工具集合，支持以下工具：

            1. maps_geo - 地理编码，地址转坐标
               参数: {address: "地址", city: "城市(可选)"}

            2. maps_regeocode - 逆地理编码，坐标转地址
               参数: {location: "经度,纬度"}

            3. maps_text_search - 关键字搜索POI
               参数: {keywords: "搜索关键词", city: "城市(可选)", citylimit: false}

            4. maps_around_search - 周边搜索POI
               参数: {keywords: "搜索关键词", location: "经度,纬度", radius: "搜索半径(可选)"}

            5. maps_search_detail - 查询POI详细信息
               参数: {id: "POI ID"}

            6. maps_direction_driving - 驾车路径规划
               参数: {origin: "起点经度,纬度", destination: "终点经度,纬度"}

            7. maps_direction_walking - 步行路径规划
               参数: {origin: "起点经度,纬度", destination: "终点经度,纬度"}

            8. maps_direction_bicycling - 骑行路径规划
               参数: {origin: "起点经度,纬度", destination: "终点经度,纬度"}

            9. maps_direction_transit_integrated - 公交路径规划
               参数: {origin: "起点经度,纬度", destination: "终点经度,纬度", city: "起点城市", cityd: "终点城市"}

            10. maps_distance - 距离测量
                参数: {origins: "起点经度,纬度", destination: "终点经度,纬度", type: "距离类型(0直线,1驾车,3步行)"}

            11. maps_ip_location - IP定位
                参数: {ip: "IP地址"}

            12. maps_weather - 天气查询
                参数: {city: "城市名称或adcode"}

            13. maps_schema_navi - 唤醒导航
                参数: {lon: "终点经度", lat: "终点纬度"}

            14. maps_schema_take_taxi - 唤醒打车
                参数: {dlon: "终点经度", dlat: "终点纬度", dname: "终点名称", slon: "起点经度(可选)", slat: "起点纬度(可选)", sname: "起点名称(可选)"}

            15. maps_schema_personal_map - 个人地图展示
                参数: {orgName: "地图名称", lineList: [行程列表]}

            使用方式: tool_name指定具体工具名，arguments包含该工具的参数
            """;

        return new MCPTool(amapMCPClient, "amap_maps", description);
    }

    /**
     * 创建日期时间MCPTool - 提供日期时间查询与计算服务
     * 基于实际MCP服务器返回的工具列表创建
     */
    @Bean
    public MCPTool datetimeMCPTool(McpSyncClient datetimeMCPClient) {
        String description = """
            日期时间工具集合，支持以下工具：

            1. datetime_current_time - 获取当前时间
               参数: {timezone: "时区(可选)", format: "时间格式(可选)"}

            2. datetime_time_difference - 计算时间差
               参数: {start_time: "起始时间(可选)", end_time: "结束时间", unit: "单位(可选)", timezone: "时区(可选)", format: "时间格式(可选)"}

            3. datetime_human_readable_time_diff - 人类可读的时间差
               参数: {start_time: "起始时间(可选)", end_time: "结束时间", timezone: "时区(可选)", format: "时间格式(可选)", locale: "语言(可选)"}

            4. datetime_relative_time - 相对时间描述
               参数: {time: "目标时间", base_time: "参考时间(可选)", timezone: "时区(可选)", format: "时间格式(可选)", locale: "语言(可选)"}

            5. datetime_time_delta - 时间加减运算
               参数: {time: "基准时间", delta: "时间间隔", unit: "单位(可选)", timezone: "时区(可选)", format: "时间格式(可选)"}

            6. datetime_convert_time - 时间格式转换
               参数: {time: "原始时间", timezone: "原始时区", to_timezone: "目标时区", format: "原始格式(可选)", to_format: "目标格式(可选)"}

            7. datetime_timestamp - 时间戳转换
               参数: {time: "目标时间(可选)", format: "时间格式(可选)", timezone: "时区(可选)", unit: "时间戳单位(可选)"}

            8. datetime_weekday - 获取星期几
               参数: {date: "日期(可选)", timezone: "时区(可选)", format: "日期格式(可选)", locale: "语言(可选)"}

            9. datetime_is_leap_year - 判断闰年
               参数: {year: "年份"}

            10. datetime_days_of_month - 获取月份天数
                参数: {year: "年份", day: "月份"}

            11. datetime_start_end_of_period - 获取周期起止时间
                参数: {period: "周期类型", date: "指定日期(可选)", format: "日期格式(可选)", out_format: "输出格式(可选)"}

            12. datetime_is_valid_date - 验证日期
                参数: {date: "日期字符串", format: "日期格式(可选)"}

            13. datetime_holiday_info - 节假日信息
                参数: {date: "日期(可选)", format: "日期格式(可选)", region: "地区(可选)"}

            使用方式: tool_name指定具体工具名，arguments包含该工具的参数
            """;

        return new MCPTool(datetimeMCPClient, "datetime_tools", description);
    }

    /**
     * 创建天气MCPTool - 提供天气查询服务
     * 基于实际MCP服务器返回的工具列表创建
     */
    @Bean
    public MCPTool weatherMCPTool(McpSyncClient weatherMCPClient) {
        String description = """
            天气服务工具集合，支持以下工具：

            1. get_hourly_weather_by_city - 根据经纬度获取城市n小时的预报天气
               参数: {lat: "纬度", lng: "经度"}

            2. get_short_forecast_weather - 根据经纬度获取短时预报天气
               参数: {lat: "纬度", lng: "经度"}

            3. get_city_forecast_weather - 根据经纬度查询未来十五天天气预报信息
               参数: {lat: "纬度", lng: "经度"}

            4. get_daily_history_weather_by_city - 根据经纬度和日期的毫秒级时间戳获取历史天气
               参数: {lat: "纬度", lng: "经度", dateTime: "日期Unix的毫秒级时间戳"}

            5. get_city_real_time_weather - 根据经纬度查询实时的天气信息
               参数: {lat: "经度", lng: "纬度"}

            6. get_city_alert_weather - 根据经纬度查询当前天气预警信息
               参数: {lat: "纬度", lng: "经度"}

            使用方式: tool_name指定具体工具名，arguments包含该工具的参数
            注意：所有查询都基于经纬度坐标进行，请确保提供正确的经纬度参数
            """;

        return new MCPTool(weatherMCPClient, "weather_tools", description);
    }

}
