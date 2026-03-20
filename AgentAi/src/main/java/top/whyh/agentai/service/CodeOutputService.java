// top/whyh.agentai.service.CodeOutputService.java

package top.whyh.agentai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.whyh.agentai.config.AgentOutputConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeOutputService {

    private final AgentOutputConfig outputConfig;

    // 用于清理系统名中的非法字符，使其能作为安全的文件夹名
    private static final Pattern ILLEGAL_FOLDER_NAME_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");

    /**
     * 保存生成的完整前后端项目到独立目录
     *
     * @param systemName    系统名称（用于创建项目根文件夹）
     * @param projectFiles  Map<文件相对路径, 文件内容>。例如: "backend/src/main/.../App.java" -> "package ..."
     * @return 项目根目录的绝对路径
     */
    public String saveGeneratedProject(String systemName, Map<String, String> projectFiles) {
        if (projectFiles == null || projectFiles.isEmpty()) {
            throw new IllegalArgumentException("无法保存空的项目文件集合");
        }

        try {
            // 1. 在统一的 Generated_Projects 目录下，为当前系统创建专属根文件夹
            String safeProjectFolderName = ILLEGAL_FOLDER_NAME_PATTERN
                    .matcher(systemName)
                    .replaceAll("_")
                    .trim();
            if (safeProjectFolderName.isEmpty()) {
                safeProjectFolderName = "Unnamed_Project_" + System.currentTimeMillis();
            }

            // 👇 核心：使用 AgentOutputConfig 中定义的统一项目输出路径
            Path projectRootDir = Paths.get(
                    outputConfig.getGeneratedProjectsPath(),
                    safeProjectFolderName
            ).normalize();

            // 2. 创建项目根目录
            Files.createDirectories(projectRootDir);
            log.debug("Created project root directory: {}", projectRootDir);

            // 3. 遍历所有文件，逐个写入
            for (Map.Entry<String, String> entry : projectFiles.entrySet()) {
                String relativeFilePath = entry.getKey();
                String fileContent = entry.getValue();

                // 安全检查：防止路径遍历攻击 (e.g., "../../etc/passwd")
                Path resolvedFilePath = projectRootDir.resolve(relativeFilePath).normalize();
                if (!resolvedFilePath.startsWith(projectRootDir)) {
                    log.warn("Detected path traversal attempt, skipping file: {}", relativeFilePath);
                    continue; // 跳过这个危险文件
                }

                // 确保父目录存在
                Path parentDir = resolvedFilePath.getParent();
                if (parentDir != null) {
                    Files.createDirectories(parentDir);
                }

                // 写入文件（覆盖模式）
                Files.writeString(resolvedFilePath, fileContent, StandardCharsets.UTF_8);
                log.debug("Saved file: {}", resolvedFilePath);
            }

            String absoluteProjectPath = projectRootDir.toAbsolutePath().toString();
            log.info("✅ 完整项目已成功保存至: {}", absoluteProjectPath);
            return absoluteProjectPath;

        } catch (IOException e) {
            log.error("❌ 保存完整项目失败 | 系统名称: {}", systemName, e);
            throw new RuntimeException("无法保存生成的项目: " + e.getMessage(), e);
        }
    }
}