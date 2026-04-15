package top.whyh.agentai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.entity.Artifact;
import top.whyh.agentai.mapper.ArtifactMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactMapper artifactMapper;

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable String id) {
        Artifact artifact = artifactMapper.selectById(id);
        if (artifact == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource;
            String contentType;
            String fileName = artifact.getName();

            // 检查 content 是路径还是实际内容
            if (artifact.getContent().length() < 500 && artifact.getContent().matches("^[a-zA-Z]:\\\\.*|^/.*")) {
                // 可能是路径
                Path filePath = Paths.get(artifact.getContent());
                if (Files.exists(filePath)) {
                    resource = new FileSystemResource(filePath.toFile());
                    contentType = Files.probeContentType(filePath);
                } else {
                    // 如果路径不存在，回退到按文本下载
                    byte[] data = artifact.getContent().getBytes(StandardCharsets.UTF_8);
                    resource = new ByteArrayResource(data);
                    contentType = "text/plain";
                }
            } else {
                // 实际内容 (文本或 JSON)
                byte[] data = artifact.getContent().getBytes(StandardCharsets.UTF_8);
                resource = new ByteArrayResource(data);
                if ("code".equals(artifact.getArtifactType())) {
                    contentType = "application/json";
                    if (!fileName.endsWith(".json")) fileName += ".json";
                } else {
                    contentType = "text/markdown";
                    if (!fileName.endsWith(".md")) fileName += ".md";
                }
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8") + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Artifact> getArtifactById(@PathVariable String id) {
        Artifact artifact = artifactMapper.selectById(id);
        if (artifact == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(artifact);
    }
}
