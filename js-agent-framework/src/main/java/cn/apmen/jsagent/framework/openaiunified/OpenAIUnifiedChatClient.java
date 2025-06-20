package cn.apmen.jsagent.framework.openaiunified;

import cn.apmen.jsagent.framework.openaiunified.model.request.ChatCompletionRequest;
import cn.apmen.jsagent.framework.openaiunified.model.response.ChatCompletionResponse;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * OpenAI ChatCompletions API客户端
 * 展示如何使用WebFlux的WebClient调用OpenAI大模型接口
 */
@Slf4j
public class OpenAIUnifiedChatClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;

    // Builder构造函数
    public OpenAIUnifiedChatClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.apiKey = apiKey != null ? apiKey : "your-api-key-here";
        // 创建新的WebClient，增加超时配置
        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Authorization", "Bearer " + this.apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    /**
     * 调用ChatCompletions API
     * @param request 请求参数
     * @return 包含响应的Mono
     */
    public Mono<ChatCompletionResponse> createChatCompletion(ChatCompletionRequest request) {
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .doOnNext(response -> {
                    try {
                        log.debug("Received response: {}", objectMapper.writeValueAsString(response));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to serialize response for logging", e);
                    }
                })
                .doOnError(error -> {
                    log.error("Error calling OpenAI API: {}", error.getMessage());
                    logDetailedError(error, request);
                });
    }

    /**
     * 调用ChatCompletions流式API
     * @param request 请求参数
     * @return 包含流式响应的Flux
     */
    public Flux<String> createChatCompletionStream(ChatCompletionRequest request) {
        // 设置流式请求参数
        request.setStream(true);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM) // 明确指定接受SSE
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(line -> log.trace("Raw SSE line: {}", line))
                .filter(line -> line != null && !line.trim().isEmpty())
                .filter(line -> !"[DONE]".equals(line.trim())) // 过滤结束标记
                .doOnNext(data -> log.debug("Received stream data: {}", data))
                .doOnError(error -> {
                    log.error("Error calling OpenAI Stream API: {}", error.getMessage());
                    logDetailedError(error, request);
                })
                .doOnComplete(() -> log.debug("Stream completed"))
                .onErrorResume(throwable -> {
                    log.error("Stream error, attempting to recover: {}", throwable.getMessage());
                    logDetailedError(throwable, request);
                    return Flux.empty();
                });
    }

    /**
     * 记录详细的错误信息
     */
    private void logDetailedError(Throwable error, ChatCompletionRequest request) {
        try {
            log.error("=== 详细错误信息 ===");
            log.error("请求URL: {}/chat/completions", baseUrl);
            log.error("错误类型: {}", error.getClass().getSimpleName());
            log.error("错误消息: {}", error.getMessage());

            // 记录请求信息
            if (request != null) {
                log.error("请求模型: {}", request.getModel());
                log.error("请求消息数量: {}", request.getMessages() != null ? request.getMessages().size() : 0);
                log.error("工具数量: {}", request.getTools() != null ? request.getTools().size() : 0);

                // 记录完整请求体（敏感信息已脱敏）
                try {
                    String requestJson = objectMapper.writeValueAsString(request);
                    // 脱敏处理：隐藏API密钥等敏感信息
                    String sanitizedRequest = requestJson.replaceAll("\"api[_-]?key\"\\s*:\\s*\"[^\"]+\"", "\"api_key\":\"***\"");
                    log.error("请求体: {}", sanitizedRequest);
                } catch (JsonProcessingException e) {
                    log.error("无法序列化请求体: {}", e.getMessage());
                }
            }

            // 如果是WebClientResponseException，记录响应详情
            if (error instanceof WebClientResponseException) {
                WebClientResponseException webError = (WebClientResponseException) error;
                log.error("HTTP状态码: {}", webError.getStatusCode());
                log.error("HTTP状态文本: {}", webError.getStatusText());
                log.error("响应头: {}", webError.getHeaders());
                log.error("响应体: {}", webError.getResponseBodyAsString());

                // 尝试解析错误响应
                try {
                    String responseBody = webError.getResponseBodyAsString();
                    if (responseBody != null && !responseBody.isEmpty()) {
                        log.error("原始响应体: {}", responseBody);
                        // 如果是JSON格式，尝试格式化
                        if (responseBody.trim().startsWith("{")) {
                            Object jsonObject = objectMapper.readValue(responseBody, Object.class);
                            String formattedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
                            log.error("格式化响应体: {}", formattedJson);
                        }
                    }
                } catch (Exception e) {
                    log.error("解析响应体失败: {}", e.getMessage());
                }
            }

            // 记录堆栈跟踪
            log.error("堆栈跟踪:", error);
            log.error("=== 错误信息结束 ===");

        } catch (Exception e) {
            log.error("记录详细错误信息时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 简单的聊天请求
     * @param prompt 用户输入的提示
     * @param model 模型名称
     * @return 包含响应的Mono
     */
    public Mono<String> simpleChatCompletion(String prompt, String model) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model != null ? model : "gpt-3.5-turbo");
        request.setMessages(List.of(new Message("user", prompt)));
        return createChatCompletion(request)
                .map(response -> {
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        return response.getChoices().get(0).getMessage().getContent();
                    }
                    return "No response generated";
                });
    }

    /**
     * 简单的流式聊天请求
     * @param prompt 用户输入的提示
     * @param model 模型名称
     * @return 包含流式响应的Flux
     */
    public Flux<String> simpleChatCompletionStream(String prompt, String model) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model != null ? model : "gpt-3.5-turbo");
        request.setMessages(List.of(new Message("user", prompt)));
        return createChatCompletionStream(request);
    }

    /**
     * 使用工具的聊天请求
     * @param prompt 用户输入的提示
     * @param tools 可用工具列表
     * @param model 模型名称
     * @return 包含响应的Mono
     */
    public Mono<ChatCompletionResponse> chatCompletionWithTools(String prompt, List<Tool> tools, String model) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model != null ? model : "gpt-3.5-turbo");
        request.setMessages(List.of(new Message("user", prompt)));
        request.setTools(tools);
        request.setToolChoice("auto");
        return createChatCompletion(request);
    }

    /**
     * 使用工具的流式聊天请求
     * @param prompt 用户输入的提示
     * @param tools 可用工具列表
     * @param model 模型名称
     * @return 包含流式响应的Flux
     */
    public Flux<String> chatCompletionWithToolsStream(String prompt, List<Tool> tools, String model) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model != null ? model : "gpt-3.5-turbo");
        request.setMessages(List.of(new Message("user", prompt)));
        request.setTools(tools);
        request.setToolChoice("auto");
        return createChatCompletionStream(request);
    }
}