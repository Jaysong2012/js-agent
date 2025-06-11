package cn.apmen.jsagent.framework.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserChatMessage {

    private String type;

    private String message;

    public UserChatMessage(String message) {
        this.type = "text";
        this.message = message;
    }
}
