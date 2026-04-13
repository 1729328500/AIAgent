package top.whyh.agentai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import top.whyh.agentai.config.DashScopeProperties;


@SpringBootApplication
@EnableConfigurationProperties(DashScopeProperties.class)
@EnableAsync
public class RequirementAnalysisServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequirementAnalysisServiceApplication.class, args);
    }
}
