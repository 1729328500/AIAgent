package top.whyh.agentai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.entity.Artifact;
import top.whyh.agentai.mapper.ArtifactMapper;

import java.io.File;
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
            Path filePath = Paths.get(artifact.getContent());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());

            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + artifact.getName() + "\"")
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
