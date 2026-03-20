package top.whyh.agentai.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import top.whyh.agentai.dto.RequirementRequest;
import top.whyh.agentai.dto.RequirementResponse;

@Service
class TestRestTemplate {

    public ResponseEntity<RequirementResponse> postForEntity(String path, RequirementRequest request, Class<RequirementResponse> requirementResponseClass) {
        return null;
    }
}
