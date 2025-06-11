package cn.apmen.jsagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@Slf4j
public class JsAgentApplication {

	public static void main(String[] args) {
		log.info("启动 JS Agent 应用...");
		SpringApplication.run(JsAgentApplication.class, args);
		log.info("JS Agent 应用启动完成");
	}

}
