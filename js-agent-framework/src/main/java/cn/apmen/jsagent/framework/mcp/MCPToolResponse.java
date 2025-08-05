package cn.apmen.jsagent.framework.mcp;

import cn.apmen.jsagent.framework.tool.BaseToolResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * MCP工具响应 - 继承BaseToolResponse
 * 用于封装MCP工具调用的响应结果
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MCPToolResponse extends BaseToolResponse {
    
    private String mcpToolName;
    
    public MCPToolResponse() {
        super();
    }
    
    public MCPToolResponse(String toolCallId, String mcpToolName, String content, boolean success) {
        super(toolCallId, success, content, null, LocalDateTime.now(), false, 
              success ? ResponseType.TEXT : ResponseType.ERROR);
        this.mcpToolName = mcpToolName;
    }
    
    @Override
    public ResponseType getResponseType() {
        // 直接根据当前状态返回响应类型，不调用抽象的super方法
        return isSuccess() ? ResponseType.TEXT : ResponseType.ERROR;
    }
    
    /**
     * 创建成功响应
     */
    public static MCPToolResponse success(String toolCallId, String mcpToolName, String content) {
        return new MCPToolResponse(toolCallId, mcpToolName, content, true);
    }
    
    /**
     * 创建错误响应
     */
    public static MCPToolResponse error(String toolCallId, String error) {
        MCPToolResponse response = new MCPToolResponse(toolCallId, "", "", false);
        response.setError(error);
        return response;
    }
    
    /**
     * 创建流式内容响应
     */
    public static MCPToolResponse streamContent(String toolCallId, String content) {
        MCPToolResponse response = new MCPToolResponse(toolCallId, "", content, true);
        response.setResponseType(ResponseType.STREAM);
        return response;
    }
    
    /**
     * 创建判定片段响应
     */
    public static MCPToolResponse createDecisionFragment(String toolCallId) {
        MCPToolResponse response = new MCPToolResponse(toolCallId, "", "", true);
        response.setDirectOutput(false);
        response.setResponseType(ResponseType.STREAM);
        return response;
    }
    
    /**
     * 设置直接输出标志
     */
    public MCPToolResponse withDirectOutput() {
        this.setDirectOutput(true);
        return this;
    }
}