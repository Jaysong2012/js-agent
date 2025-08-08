package cn.apmen.jsagent.framework.paradigm.manus;

import cn.apmen.jsagent.framework.core.AgentRequest;
import cn.apmen.jsagent.framework.core.AgentResponse;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ReactAgent extends BaseAgent {

    @Override
    public Flux<AgentResponse> step(AgentRequest agentRequest) {
        AtomicBoolean act = new AtomicBoolean(false);
        Flux<AgentResponse> think = think(agentRequest).doOnNext(response -> {
            if (CollectionUtils.isEmpty(response.getToolCalls())) {
                act.set(true);
            }
        });
        if (act.get()) {
            return think.concatWith(act(agentRequest));
        }

        return think;
    }


    public abstract Mono<AgentResponse> act(AgentRequest agentRequest);

    public abstract Flux<AgentResponse> think(AgentRequest agentRequest);

}
