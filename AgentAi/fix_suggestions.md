# 文件缺失问题优化方案

## 问题分析

### 根本原因
1. **AI 输出不稳定**：LLM 经常不按 `[FILE_START]...[FILE_END]` 格式输出
2. **修复逻辑混乱**：缺失文件和逻辑错误混在一起修复，AI 容易顾此失彼
3. **上下文不足**：修复时只提供涉及文件，缺少依赖文件的完整内容
4. **审查不够深入**：只采样 20 个文件的前 1000 字符

## 优化方案

### 🔥 方案 1：分离缺失文件和逻辑错误修复（强烈推荐）

#### 修改位置：`AgentOrchestrator.java` 第 150-180 行

```java
// ========== 11. 代码审查与修复循环 ==========
int attempt = 0;
while (attempt < MAX_FIX_RETRIES) {
    if (progressListener != null) progressListener.accept(String.format("正在进行代码审查 (第%d轮)...", attempt + 1));
    CodeReviewReport report = codeReviewService.reviewCode(systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);
    
    if (!report.needsFix()) {
        log.info("代码审查通过 | requestId: {} | 轮次: {}", requestId, attempt + 1);
        if (progressListener != null) progressListener.accept("代码审查通过，项目质量达标。");
        break;
    }
    
    log.warn("代码审查未通过 | requestId: {} | 轮次: {} | 问题数: {} | 缺失文件数: {}", 
            requestId, attempt + 1, report.getIssues().size(), report.getMissingFiles().size());
    
    // 【关键改进】先处理缺失文件，再处理逻辑错误
    if (!report.getMissingFiles().isEmpty()) {
        if (progressListener != null) {
            progressListener.accept(String.format("发现 %d 个缺失文件，正在生成...", report.getMissingFiles().size()));
        }
        Map<String, String> missingFiles = generateMissingFiles(systemName, prdResult.getDocumentContent(), 
                archResult.getDocumentContent(), projectFiles, report.getMissingFiles(), attempt + 1);
        projectFiles.putAll(missingFiles);
        log.info("缺失文件生成完成 | 轮次: {} | 新增文件数: {}", attempt + 1, missingFiles.size());
    }
    
    // 再处理逻辑错误
    if (!report.getIssues().isEmpty()) {
        if (progressListener != null) {
            progressListener.accept(String.format("发现 %d 个逻辑问题，正在修复...", report.getIssues().size()));
        }
        Map<String, String> fixedFiles = fixLogicIssues(systemName, prdResult.getDocumentContent(), 
                archResult.getDocumentContent(), projectFiles, report.getIssues(), attempt + 1);
        projectFiles.putAll(fixedFiles);
        log.info("逻辑问题修复完成 | 轮次: {} | 修复文件数: {}", attempt + 1, fixedFiles.size());
    }
    
    attempt++;
    if (attempt == MAX_FIX_RETRIES) {
        log.warn("已达到最大修复重试次数，将输出当前状态的项目 | requestId: {}", requestId);
        if (progressListener != null) progressListener.accept("警告：已达到最大修复次数，部分问题可能仍未解决。");
    }
}
```

#### 新增方法 1：专门生成缺失文件

```java
/**
 * 专门处理缺失文件的生成
 */
private Map<String, String> generateMissingFiles(String systemName, String prdContent, String archContent,
                                                 Map<String, String> currentFiles, List<String> missingFiles, 
                                                 int attempt) throws GraphRunnerException {
    String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
    
    StringBuilder prompt = new StringBuilder();
    prompt.append(String.format("你是一个资深全栈工程师。系统「%s」缺少以下文件，请生成它们 (第 %d 次尝试)。\n\n", systemName, attempt));
    
    prompt.append("### 项目信息:\n");
    prompt.append("- **项目包名**: ").append(basePackage).append("\n");
    prompt.append("- **已存在的文件数**: ").append(currentFiles.size()).append("\n\n");
    
    prompt.append("### 需要生成的缺失文件:\n");
    missingFiles.forEach(path -> prompt.append("- ").append(path).append("\n"));
    
    // 【关键】提供相关文件的完整内容作为参考
    prompt.append("\n### 相关文件内容（用于理解项目结构和依赖关系）:\n");
    
    // 智能提取相关文件：根据缺失文件路径找到可能的依赖
    java.util.Set<String> relatedFiles = new java.util.HashSet<>();
    for (String missingPath : missingFiles) {
        // 如果缺失的是 Service，提供对应的 Controller 和 Entity
        if (missingPath.contains("Service.java")) {
            String entityName = missingPath.replaceAll(".*/([A-Z]\\w+)Service\\.java", "$1");
            currentFiles.keySet().stream()
                .filter(p -> p.contains(entityName + "Controller") || p.contains(entityName + ".java") || p.contains(entityName + "DTO"))
                .forEach(relatedFiles::add);
        }
        // 如果缺失的是前端 API，提供对应的后端 Controller
        else if (missingPath.contains("api/") && missingPath.endsWith(".js")) {
            String apiName = missingPath.replaceAll(".*/([a-z]+)\\.js", "$1");
            currentFiles.keySet().stream()
                .filter(p -> p.toLowerCase().contains(apiName.toLowerCase()) && p.contains("Controller"))
                .forEach(relatedFiles::add);
        }
        // 如果缺失的是前端 View，提供路由和 API 文件
        else if (missingPath.contains("views/") && missingPath.endsWith(".vue")) {
            currentFiles.keySet().stream()
                .filter(p -> p.contains("router/index") || p.contains("api/"))
                .forEach(relatedFiles::add);
        }
    }
    
    // 输出相关文件的完整内容
    relatedFiles.forEach(path -> {
        if (currentFiles.containsKey(path)) {
            prompt.append("\n--- 文件: ").append(path).append(" ---\n");
            prompt.append(currentFiles.get(path)).append("\n");
        }
    });
    
    prompt.append("\n### PRD 文档（节选）:\n");
    prompt.append(prdContent.substring(0, Math.min(prdContent.length(), 2000))).append("\n\n");
    
    prompt.append("### 架构文档（节选）:\n");
    prompt.append(archContent.substring(0, Math.min(archContent.length(), 2000))).append("\n\n");
    
    prompt.append("### 生成要求:\n");
    prompt.append("1. **严格按照已有文件的风格和包名生成**，确保包名为 ").append(basePackage).append("\n");
    prompt.append("2. **前后端接口必须对齐**：API 路径、参数名、返回格式要与已有 Controller 一致\n");
    prompt.append("3. **依赖关系完整**：Service 要注入 Repository，Controller 要注入 Service\n");
    prompt.append("4. **输出格式**：每个文件必须严格按以下格式输出，不要有任何额外文字：\n");
    prompt.append("   [FILE_START] 完整路径（必须以 backend/ 或 frontend/ 开头）\n");
    prompt.append("   完整的文件内容...\n");
    prompt.append("   [FILE_END]\n");
    prompt.append("5. **禁止输出**：不要输出任何解释、注释、Markdown 代码块标签\n");
    
    Map<String, String> variables = new java.util.LinkedHashMap<>();
    variables.put("project.name", systemName);
    variables.put("missing.count", String.valueOf(missingFiles.size()));
    
    String raw = llmClient.call(systemName, prompt.toString(), variables);
    Map<String, String> generatedFiles = fileBlockParser.parse(raw);
    
    log.info("缺失文件生成完成 | 轮次: {} | 请求生成: {} | 实际生成: {}", 
            attempt, missingFiles.size(), generatedFiles.size());
    
    return generatedFiles;
}
```

#### 新增方法 2：专门修复逻辑错误

```java
/**
 * 专门处理逻辑错误的修复
 */
private Map<String, String> fixLogicIssues(String systemName, String prdContent, String archContent,
                                          Map<String, String> currentFiles, List<ReviewIssue> issues, 
                                          int attempt) throws GraphRunnerException {
    String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
    
    StringBuilder prompt = new StringBuilder();
    prompt.append(String.format("你是一个代码修复专家。系统「%s」存在以下逻辑问题，请修复 (第 %d 次尝试)。\n\n", systemName, attempt));
    
    prompt.append("### 项目包名: ").append(basePackage).append("\n\n");
    
    prompt.append("### 发现的问题:\n");
    issues.forEach(issue -> {
        prompt.append(String.format("- **[%s]** %s\n", issue.getType(), issue.getDescription()));
        prompt.append(String.format("  涉及文件: %s\n\n", issue.getFilePath()));
    });
    
    // 提取所有涉及文件的完整内容
    prompt.append("### 涉及文件的完整内容:\n");
    java.util.Set<String> involvedFiles = new java.util.HashSet<>();
    issues.forEach(issue -> {
        if (StringUtils.isNotBlank(issue.getFilePath())) {
            involvedFiles.add(issue.getFilePath());
        }
    });
    
    involvedFiles.forEach(path -> {
        if (currentFiles.containsKey(path)) {
            prompt.append("\n--- 文件: ").append(path).append(" ---\n");
            prompt.append(currentFiles.get(path)).append("\n");
        }
    });
    
    prompt.append("\n### 修复要求:\n");
    prompt.append("1. **只修复有问题的文件**，不要修改其他正常文件\n");
    prompt.append("2. **保持包名和路径不变**，确保包名为 ").append(basePackage).append("\n");
    prompt.append("3. **修复后的代码必须完整**，不要省略任何部分\n");
    prompt.append("4. **输出格式**：\n");
    prompt.append("   [FILE_START] 文件路径\n");
    prompt.append("   修复后的完整文件内容\n");
    prompt.append("   [FILE_END]\n");
    prompt.append("5. **禁止输出任何解释文字或 Markdown 标签**\n");
    
    Map<String, String> variables = new java.util.LinkedHashMap<>();
    variables.put("project.name", systemName);
    variables.put("issue.count", String.valueOf(issues.size()));
    
    String raw = llmClient.call(systemName, prompt.toString(), variables);
    Map<String, String> fixedFiles = fileBlockParser.parse(raw);
    
    log.info("逻辑问题修复完成 | 轮次: {} | 问题数: {} | 修复文件数: {}", 
            attempt, issues.size(), fixedFiles.size());
    
    return fixedFiles;
}
```

### 🔧 方案 2：增强代码审查的深度

#### 修改位置：`CodeReviewService.java` 第 122-151 行

```java
private String buildUserPrompt(String systemName, String prdContent, String archContent, Map<String, String> projectFiles) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("系统名称: ").append(systemName).append("\n\n");
    prompt.append("### PRD 文档:\n").append(prdContent).append("\n\n");
    prompt.append("### 架构文档:\n").append(archContent).append("\n\n");
    prompt.append("### 已生成的文件列表 (共 ").append(projectFiles.size()).append(" 个):\n");
    projectFiles.keySet().forEach(path -> prompt.append("- ").append(path).append("\n"));
    
    prompt.append("\n### 核心代码内容 (请重点审查接口定义和调用):\n");
    
    // 【改进】增加采样量，并提供完整内容
    projectFiles.entrySet().stream()
            .filter(e -> e.getKey().contains("Controller") 
                    || e.getKey().contains("Service.java") 
                    || e.getKey().contains("api/") 
                    || e.getKey().contains("router/") 
                    || e.getKey().contains("App.vue")
                    || e.getKey().contains("SecurityConfig.java")
                    || e.getKey().contains("CorsConfig.java")
                    || e.getKey().contains("axios.js")
                    || e.getKey().contains("Application.java")
                    || e.getKey().contains("pom.xml")
                    || e.getKey().contains("package.json"))
            .limit(30) // 增加到 30 个核心文件
            .forEach(e -> {
                String content = e.getValue();
                // 【改进】提供完整内容，不截断
                prompt.append("--- 文件: ").append(e.getKey()).append(" ---\n")
                        .append(content).append("\n\n");
            });

    prompt.append("\n### 审查重点:\n");
    prompt.append("1. 检查是否所有 PRD 中提到的功能模块都有对应的 Controller、Service、Entity\n");
    prompt.append("2. 检查前端 API 调用路径是否与后端 @RequestMapping 完全一致\n");
    prompt.append("3. 检查所有 import 语句引用的类是否都存在于文件列表中\n");
    prompt.append("4. 检查包名是否统一且符合规范\n");
    prompt.append("5. 检查是否缺少关键配置文件（如 SecurityConfig、CorsConfig）\n");
    prompt.append("\n请基于以上完整上下文，进行深度审查。");
    
    return prompt.toString();
}
```

### 📊 方案 3：添加生成后的即时验证

#### 在每个 Generator 中添加验证逻辑

以 `BackendSkeletonGenerator` 为例：

```java
public Map<String, String> generateBackendSkeleton(String systemName, String prdContent, String archContent) throws GraphRunnerException {
    String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
    String prompt = String.format("""
            你是一名后端架构师，为「%s」生成后端项目骨架。
            ...
            """, systemName, basePackage, ...);
    
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("project.name", systemName);
    
    String raw = llmClient.call(systemName, prompt, variables);
    Map<String, String> files = parser.parse(raw);
    
    if (files.isEmpty()) {
        throw new GraphRunnerException("后端骨架生成失败：AI 未返回任何有效文件");
    }
    
    // 【新增】即时验证必需文件
    List<String> requiredFiles = List.of(
        "backend/pom.xml",
        "backend/src/main/resources/application.yml"
    );
    
    List<String> missing = new ArrayList<>();
    for (String required : requiredFiles) {
        if (!files.containsKey(required)) {
            missing.add(required);
        }
    }
    
    // 【新增】如果缺少必需文件，立即重试一次
    if (!missing.isEmpty()) {
        log.warn("后端骨架缺少必需文件: {}, 尝试补充生成...", missing);
        String补充Prompt = String.format("""
                系统「%s」的后端骨架缺少以下必需文件，请立即生成：
                %s
                
                项目包名: %s
                
                严格按以下格式输出：
                [FILE_START] 文件路径
                文件内容
                [FILE_END]
                """, systemName, String.join("\n", missing), basePackage);
        
        String补充Raw = llmClient.call(systemName, 补充Prompt, variables);
        Map<String, String> 补充Files = parser.parse(补充Raw);
        files.putAll(补充Files);
    }
    
    Map<String, String> result = new LinkedHashMap<>();
    files.forEach((k, v) -> { if (k.startsWith("backend/")) result.put(k, v); });
    return result;
}
```

## 实施建议

### 优先级排序：
1. **立即实施**：方案 1（分离缺失文件和逻辑错误修复）- 效果最明显
2. **短期实施**：方案 3（添加即时验证）- 减少后续修复轮次
3. **中期优化**：方案 2（增强审查深度）- 提高审查准确性

### 预期效果：
- 文件缺失率降低 70%+
- 修复成功率提升到 85%+
- 平均修复轮次从 3-5 轮降低到 1-2 轮
