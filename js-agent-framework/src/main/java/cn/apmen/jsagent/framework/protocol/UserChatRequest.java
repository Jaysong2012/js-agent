package cn.apmen.jsagent.framework.protocol;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserChatRequest {

    private String userId;

    private String conversationId;

    private UserChatMessage message;

}
