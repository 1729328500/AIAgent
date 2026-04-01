package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class FileBlockParser {
    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile(
            "\\[FILE_START\\]\\s*(.+?)\\s*\\n(.*?)\\s*\\[FILE_END\\]",
            Pattern.DOTALL | Pattern.MULTILINE
    );

    public Map<String, String> parse(String raw) throws GraphRunnerException {
        Map<String, String> projectFiles = new LinkedHashMap<>();
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(raw);
        boolean foundAnyFile = false;
        while (matcher.find()) {
            foundAnyFile = true;
            String filePath = matcher.group(1).trim();
            String fileContent = matcher.group(2);
            String normalizedPath = filePath
                    .replace('\\', '/')
                    .replaceAll("^/+", "");
            if (normalizedPath.isEmpty() || normalizedPath.contains("..")) {
                continue;
            }
            projectFiles.put(normalizedPath, fileContent);
        }
        if (!foundAnyFile) {
            throw new GraphRunnerException("AI 未按指定格式输出文件，请检查提示词或模型行为。");
        }
        return projectFiles;
    }
}
