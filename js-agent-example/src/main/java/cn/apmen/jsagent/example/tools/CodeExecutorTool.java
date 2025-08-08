package cn.apmen.jsagent.example.tools;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.AbstractToolExecutor;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 代码执行工具 - 支持多种编程语言的代码执行
 * 支持的语言：JavaScript, Python, Java, Shell
 */
@Component
@Slf4j
public class CodeExecutorTool extends AbstractToolExecutor {

    private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
    private static final int EXECUTION_TIMEOUT_SECONDS = 30;
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    @Override
    public String getToolName() {
        return "code_executor";
    }

    @Override
    public String getDescription() {
        return "执行代码片段，支持JavaScript、Python、Java、Shell等多种编程语言";
    }

    @Override
    public Map<String, Object> getParametersDefinition() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // language 参数定义
        Map<String, Object> languageProperty = new HashMap<>();
        languageProperty.put("type", "string");
        languageProperty.put("description", "编程语言类型");
        languageProperty.put("enum", Arrays.asList("javascript", "python", "java", "shell", "bash"));
        properties.put("language", languageProperty);

        // code 参数定义
        Map<String, Object> codeProperty = new HashMap<>();
        codeProperty.put("type", "string");
        codeProperty.put("description", "要执行的代码内容");
        properties.put("code", codeProperty);

        // timeout 参数定义（可选）
        Map<String, Object> timeoutProperty = new HashMap<>();
        timeoutProperty.put("type", "integer");
        timeoutProperty.put("description", "执行超时时间（秒），默认30秒");
        timeoutProperty.put("default", 30);
        properties.put("timeout", timeoutProperty);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"language", "code"});

        return parameters;
    }

    @Override
    public String[] getRequiredParameters() {
        return new String[]{"language", "code"};
    }

    @Override
    protected Mono<ToolResult> doExecute(ToolCall toolCall, ToolContext context, Map<String, Object> arguments) {
        String language = getStringParameter(arguments, "language");
        String code = getStringParameter(arguments, "code");
        int timeout = getIntParameter(arguments, "timeout", EXECUTION_TIMEOUT_SECONDS);

        log.info("执行代码: language={}, timeout={}s", language, timeout);
        log.debug("代码内容: {}", code);

        try {
            String result = executeCode(language, code, timeout);
            return Mono.just(success(toolCall.getId(), result));
        } catch (Exception e) {
            log.error("代码执行失败", e);
            return Mono.just(error(toolCall.getId(), "代码执行失败: " + e.getMessage()));
        }
    }

    /**
     * 执行代码
     */
    private String executeCode(String language, String code, int timeout) throws Exception {
        switch (language.toLowerCase()) {
            case "javascript":
            case "js":
                return executeJavaScript(code);
            case "python":
            case "py":
                return executePython(code, timeout);
            case "java":
                return executeJava(code, timeout);
            case "shell":
            case "bash":
                return executeShell(code, timeout);
            default:
                throw new IllegalArgumentException("不支持的编程语言: " + language);
        }
    }

    /**
     * 执行JavaScript代码
     */
    private String executeJavaScript(String code) throws ScriptException {
        ScriptEngine engine = scriptEngineManager.getEngineByName("javascript");
        if (engine == null) {
            throw new RuntimeException("JavaScript引擎不可用");
        }

        // 添加安全限制
        String safeCode = addJavaScriptSafetyWrapper(code);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(outputStream);
        engine.getContext().setWriter(writer);
        engine.getContext().setErrorWriter(writer);

        try {
            Object result = engine.eval(safeCode);
            writer.flush();

            String output = outputStream.toString();
            if (!output.isEmpty()) {
                return "输出:\n" + output + (result != null ? "\n返回值: " + result : "");
            } else {
                return result != null ? "返回值: " + result : "执行完成，无返回值";
            }
        } finally {
            writer.close();
        }
    }

    /**
     * 执行Python代码
     */
    private String executePython(String code, int timeout) throws Exception {
        // 创建临时Python文件
        Path tempFile = createTempFile("python_code", ".py", code);

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", tempFile.toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待执行完成
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Python代码执行超时");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return "执行失败 (退出码: " + exitCode + "):\n" + output.toString();
            }

            return "执行成功:\n" + output.toString();

        } finally {
            // 清理临时文件
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 执行Java代码
     */
    private String executeJava(String code, int timeout) throws Exception {
        // 提取类名
        String className = extractJavaClassName(code);
        if (className == null) {
            className = "TempJavaClass";
            code = "public class " + className + " {\n" +
                   "    public static void main(String[] args) {\n" +
                   code + "\n" +
                   "    }\n" +
                   "}";
        }

        // 创建临时Java文件
        Path tempFile = createTempFile(className, ".java", code);
        Path tempDir = tempFile.getParent();

        try {
            // 编译Java代码
            ProcessBuilder compileBuilder = new ProcessBuilder("javac", tempFile.toString());
            compileBuilder.directory(tempDir.toFile());
            Process compileProcess = compileBuilder.start();

            boolean compileFinished = compileProcess.waitFor(timeout, TimeUnit.SECONDS);
            if (!compileFinished) {
                compileProcess.destroyForcibly();
                throw new RuntimeException("Java代码编译超时");
            }

            if (compileProcess.exitValue() != 0) {
                String error = readProcessError(compileProcess);
                return "编译失败:\n" + error;
            }

            // 运行Java代码
            ProcessBuilder runBuilder = new ProcessBuilder("java", "-cp", tempDir.toString(), className);
            runBuilder.redirectErrorStream(true);
            Process runProcess = runBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean runFinished = runProcess.waitFor(timeout, TimeUnit.SECONDS);
            if (!runFinished) {
                runProcess.destroyForcibly();
                throw new RuntimeException("Java代码执行超时");
            }

            return "执行成功:\n" + output.toString();

        } finally {
            // 清理临时文件
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(Paths.get(tempDir.toString(), className + ".class"));
        }
    }

    /**
     * 执行Shell代码
     */
    private String executeShell(String code, int timeout) throws Exception {
        // 添加安全检查
        if (containsUnsafeShellCommands(code)) {
            throw new SecurityException("检测到不安全的Shell命令");
        }

        Path tempFile = createTempFile("shell_script", ".sh", code);

        try {
            // 设置执行权限
            tempFile.toFile().setExecutable(true);

            ProcessBuilder pb = new ProcessBuilder("bash", tempFile.toString());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Shell脚本执行超时");
            }

            int exitCode = process.exitValue();
            String result = "执行" + (exitCode == 0 ? "成功" : "失败 (退出码: " + exitCode + ")") + ":\n" + output.toString();

            return result;

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * 创建临时文件
     */
    private Path createTempFile(String prefix, String suffix, String content) throws IOException {
        Path tempFile = Files.createTempFile(Paths.get(TEMP_DIR), prefix, suffix);
        Files.write(tempFile, content.getBytes());
        return tempFile;
    }

    /**
     * 提取Java类名
     */
    private String extractJavaClassName(String code) {
        String[] lines = code.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("public class ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    return parts[2].replace("{", "");
                }
            }
        }
        return null;
    }

    /**
     * 读取进程错误输出
     */
    private String readProcessError(Process process) throws IOException {
        StringBuilder error = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }
        return error.toString();
    }

    /**
     * 添加JavaScript安全包装
     */
    private String addJavaScriptSafetyWrapper(String code) {
        // 简单的安全检查，禁止一些危险操作
        if (code.contains("java.lang.System") ||
            code.contains("java.io") ||
            code.contains("java.nio") ||
            code.contains("Runtime.getRuntime()")) {
            throw new SecurityException("检测到不安全的JavaScript代码");
        }

        return code;
    }

    /**
     * 检查Shell命令是否安全
     */
    private boolean containsUnsafeShellCommands(String code) {
        String[] unsafeCommands = {
            "rm -rf", "sudo", "su ", "chmod 777", "mkfs", "dd if=",
            ":(){ :|:& };:", "wget", "curl", "nc ", "netcat",
            "iptables", "ufw", "firewall", "systemctl", "service"
        };

        String lowerCode = code.toLowerCase();
        for (String unsafe : unsafeCommands) {
            if (lowerCode.contains(unsafe.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}

