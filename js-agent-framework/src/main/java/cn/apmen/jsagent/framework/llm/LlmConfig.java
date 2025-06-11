package cn.apmen.jsagent.framework.llm;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class LlmConfig {

    private String model;
    private Double temperature;
    private Double topP;
    private Integer n;
    private List<String> stop;
    private Integer maxTokens;
    private Integer maxCompletionTokens;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private Map<String, Integer> logitBias;
    private Long seed;
}
