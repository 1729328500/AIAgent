package top.whyh.agentai.utils;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MarkdownValidator {

    public static final Parser PARSER = Parser.builder().build();
    private static final MarkdownRenderer MARKDOWN_RENDERER = MarkdownRenderer.builder().build();

    public static void validateMarkdownStructure(String markdownContent) {
        // 解析Markdown内容
        Node document = PARSER.parse(markdownContent);

        // 验证文档结构
        validateDocumentStructure(document);

        // 验证必需章节
        validateRequiredSections(document);

        // 验证格式规范性
        validateFormattingConventions(document);
    }

    private static void validateDocumentStructure(Node document) {
        // 验证根节点是Document
        Assertions.assertInstanceOf(Document.class, document, "根节点必须是Document类型");

        // 验证文档包含内容 - 使用visitor模式检查是否有子节点
        List<Node> allNodes = new ArrayList<>();
        collectAllNodes(document, allNodes);

        // 检查是否只有根节点而没有实际内容
        long contentNodesCount = allNodes.stream()
                .filter(node -> !(node instanceof Document))
                .count();

        Assertions.assertTrue(contentNodesCount > 0, "文档内容不能为空");
    }

    private static void validateRequiredSections(Node document) {
        // 定义必需的章节标题
        String[] requiredSections = {
                "需求背景与目标", "目标用户与场景", "核心功能需求",
                "非功能需求", "需求优先级", "验收标准"
        };

        // 提取所有标题文本
        List<String> headings = extractHeadings(document);

        // 验证每个必需章节是否存在
        for (String section : requiredSections) {
            boolean exists = headings.stream()
                    .anyMatch(h -> h.contains(section));

            Assertions.assertTrue(exists, "必需章节缺失：" + section);
        }
    }

    private static void validateFormattingConventions(Node document) {
        // 收集所有标题节点进行验证
        List<Heading> headings = new ArrayList<>();
        collectHeadings(document, headings);

        for (Heading heading : headings) {
            int level = heading.getLevel();

            // 主章节应该是二级标题
            if (level != 2) {
                Assertions.fail("章节标题应该使用## 二级标题格式，实际使用了" + level + "级标题: " +
                        getTextContent(heading));
            }
        }
    }

    private static List<String> extractHeadings(Node node) {
        List<Heading> headings = new ArrayList<>();
        collectHeadings(node, headings);

        return headings.stream()
                .map(MarkdownValidator::getTextContent) // 修改为类名引用静态方法
                .collect(Collectors.toList());
    }

    private static String getTextContent(Heading heading) { // 改为静态方法
        StringBuilder sb = new StringBuilder();
        collectText(heading, sb);
        return sb.toString().trim();
    }

    private static void collectText(Node node, StringBuilder sb) {
        if (node instanceof Text) {
            sb.append(((Text) node).getLiteral());
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectText(child, sb);
        }
    }

    private static void collectHeadings(Node node, List<Heading> headings) {
        if (node instanceof Heading) {
            headings.add((Heading) node);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectHeadings(child, headings);
        }
    }

    private static void collectAllNodes(Node node, List<Node> allNodes) {
        allNodes.add(node);
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectAllNodes(child, allNodes);
        }
    }

    public static String convertToHtml(String markdownContent) {
        Node document = PARSER.parse(markdownContent);
        return HtmlRenderer.builder().build().render(document);
    }

    public static String convertToMarkdown(Node document) {
        return MARKDOWN_RENDERER.render(document);
    }
}
