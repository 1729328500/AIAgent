package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import top.whyh.agentai.config.CoderAgentConfig;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmClient {
    private final CoderAgentConfig coderConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String call(String systemName, String userPrompt, Map<String, String> variables) throws GraphRunnerException {
        String url = String.format(
                "%s/api/v1/workspace/%s/chat",
                coderConfig.getBaseUrl().replaceAll("/+$", ""),
                coderConfig.getWorkspaceSlug()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + coderConfig.getApiKey().trim());
        Map<String, Object> body = Map.of(
                "message", userPrompt,
                "max_tokens", 20000,
                "stream", false,
                "mode", "chat",
                "sessionId", "agent-" + System.currentTimeMillis(),
                "variables", variables
        );
        try {
            log.info("调用 AnythingLLM | URL: {} | System: {}", url, systemName);
            ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
                JsonNode textNode = root.path("textResponse");
                if (!textNode.isMissingNode() && !textNode.asText().isEmpty()) {
                    return textNode.asText();
                }
                throw new GraphRunnerException("AnythingLLM 响应中无有效代码输出: " + response.getBody());
            }
            throw new GraphRunnerException(String.format("调用 AnythingLLM 失败 [%s]: %s", response.getStatusCode(), response.getBody()));
        } catch (RestClientException e) {
            throw new GraphRunnerException("调用 AnythingLLM 失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new GraphRunnerException("解析 AnythingLLM 响应失败：" + e.getMessage(), e);
        }
    }
}
