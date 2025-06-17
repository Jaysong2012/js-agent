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
                    }
                })
                .doOnError(error -> log.error("Error calling OpenAI API: {}", error.getMessage()));
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
                .doOnError(error -> log.error("Error calling OpenAI Stream API: {}", error.getMessage()))
                .doOnComplete(() -> log.debug("Stream completed"))
                .onErrorResume(throwable -> {
                    log.error("Stream error, attempting to recover: {}", throwable.getMessage());
                    return Flux.empty();
                });
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