package top.whyh.agentai.service.codegen;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiSpecExtractor {
    public String extract(Map<String, String> backendFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("后端已生成的接口如下：\n");
        backendFiles.forEach((path, content) -> {
            if (path.startsWith("backend/src/main/java/") && path.contains("/controller/")) {
                Pattern classMapping = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");
                Pattern methodGet = Pattern.compile("@GetMapping\\(\"([^\"]*)\"\\)");
                Pattern methodPost = Pattern.compile("@PostMapping\\(\"([^\"]*)\"\\)");
                Pattern methodPut = Pattern.compile("@PutMapping\\(\"([^\"]*)\"\\)");
                Pattern methodDelete = Pattern.compile("@DeleteMapping\\(\"([^\"]*)\"\\)");
                Matcher cm = classMapping.matcher(content);
                String base = "";
                if (cm.find()) base = cm.group(1);
                Matcher mg = methodGet.matcher(content);
                while (mg.find()) sb.append("- GET ").append(base).append(mg.group(1)).append("\n");
                Matcher mp = methodPost.matcher(content);
                while (mp.find()) sb.append("- POST ").append(base).append(mp.group(1)).append("\n");
                Matcher mu = methodPut.matcher(content);
                while (mu.find()) sb.append("- PUT ").append(base).append(mu.group(1)).append("\n");
                Matcher md = methodDelete.matcher(content);
                while (md.find()) sb.append("- DELETE ").append(base).append(md.group(1)).append("\n");
            }
        });
        return sb.toString();
    }
}
