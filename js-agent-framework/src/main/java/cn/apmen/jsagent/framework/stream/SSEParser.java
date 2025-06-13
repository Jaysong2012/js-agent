package cn.apmen.jsagent.framework.stream;

import cn.apmen.jsagent.framework.openaiunified.model.response.stream.ChatCompletionStreamResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * SSE (Server-Sent Events) 解析器
 * 正确处理流式数据格式
 */
@Slf4j
public class SSEParser {
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 解析SSE流数据
     * @param sseStream 原始SSE数据流
     * @return 解析后的ChatCompletionStreamResponse流
     */
    public Flux<ChatCompletionStreamResponse> parseSSEStream(Flux<String> sseStream) {
        return sseStream
                .doOnNext(line -> log.trace("Processing SSE line: {}", line))
                .filter(data -> data != null && !data.trim().isEmpty())
                .filter(data -> !"[DONE]".equals(data.trim()))
                .doOnNext(data -> log.trace("Extracted JSON data: {}", data))
                .flatMap(this::parseJsonSafely)
                .doOnNext(response -> log.trace("Parsed SSE response: {}", response))
                .doOnError(error -> log.error("SSE parsing error: {}", error.getMessage()))
                .onErrorContinue((error, item) -> 
                    log.warn("Failed to parse SSE item: {}, error: {}", item, error.getMessage()));
    }
    /**
     * 检查是否为有效的SSE数据行
     */
    private boolean isValidSSELine(String line) {
        return line != null && 
               line.startsWith("data: ") && 
               !line.equals("data: ");
    }
    /**
     * 从SSE行中提取数据部分
     */
    private String extractDataFromSSELine(String line) {
        if (line.startsWith("data: ")) {
            return line.substring(6); // 移除 "data: " 前缀
        }
        return null;
    }
    /**
     * 安全地解析JSON数据
     */
    private Flux<ChatCompletionStreamResponse> parseJsonSafely(String jsonData) {
        try {
            ChatCompletionStreamResponse response = objectMapper.readValue(jsonData, ChatCompletionStreamResponse.class);
            return Flux.just(response);
        } catch (Exception e) {
            log.error("Failed to parse JSON: {}, error: {}", jsonData, e.getMessage());
            return Flux.empty(); // 忽略无法解析的数据
        }
    }
    /**
     * 处理多行SSE数据
     * 某些SSE实现可能会发送多行数据
     */
    public Flux<ChatCompletionStreamResponse> parseMultiLineSSE(Flux<String> sseStream) {
        return sseStream
                .bufferUntil(line -> line.trim().isEmpty() || line.equals("data: [DONE]"))
                .flatMap(this::processSSEBlock)
                .onErrorContinue((error, item) -> 
                    log.warn("Failed to process SSE block: {}, error: {}", item, error.getMessage()));
    }
    /**
     * 处理SSE数据块
     */
    private Flux<ChatCompletionStreamResponse> processSSEBlock(java.util.List<String> lines) {
        StringBuilder dataBuilder = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if ("[DONE]".equals(data.trim())) {
                    break;
                }
                dataBuilder.append(data);
            }
        }
        String jsonData = dataBuilder.toString().trim();
        if (!jsonData.isEmpty()) {
            return parseJsonSafely(jsonData);
        }
        return Flux.empty();
    }
}