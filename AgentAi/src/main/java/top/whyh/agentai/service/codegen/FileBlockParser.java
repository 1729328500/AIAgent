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
    /**
     * 更强大的正则，支持：
     * 1. 路径和内容之间可选的换行
     * 2. 路径前后的空白符
     * 3. 内容两端的空白符
     */
    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile(
            "\\[FILE_START\\]\\s*(.+?)\\s*[\\r\\n]+(.*?)\\s*\\[FILE_END\\]",
            Pattern.DOTALL | Pattern.MULTILINE
    );

    public Map<String, String> parse(String raw) throws GraphRunnerException {
        // 预处理：移除 AI 可能误导的 Markdown 代码块标记，以免干扰解析
        String processedRaw = raw.replaceAll("```[a-zA-Z]*\\n", "").replaceAll("```", "");

        Map<String, String> projectFiles = new LinkedHashMap<>();
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(processedRaw);
        boolean foundAnyFile = false;
        
        while (matcher.find()) {
            foundAnyFile = true;
            String filePath = matcher.group(1).trim();
            String fileContent = matcher.group(2);
            
            // 清理路径中的异常字符
            String normalizedPath = filePath
                    .replace('\\', '/')
                    .replaceAll("^/+", "")
                    .replaceAll("[\\r\\n]", "") // 防止路径包含换行
                    .trim();
                    
            if (normalizedPath.isEmpty() || normalizedPath.contains("..")) {
                log.warn("跳过非法文件路径: {}", normalizedPath);
                continue;
            }
            
            projectFiles.put(normalizedPath, fileContent);
        }
        
        if (!foundAnyFile) {
            log.error("AI 输出内容解析失败，原始响应内容预览:\n{}", 
                    processedRaw.length() > 500 ? processedRaw.substring(0, 500) + "..." : processedRaw);
            throw new GraphRunnerException("AI 未按指定格式输出文件，请检查提示词或模型行为。响应中未找到 [FILE_START] 标记。");
        }
        return projectFiles;
    }
}
