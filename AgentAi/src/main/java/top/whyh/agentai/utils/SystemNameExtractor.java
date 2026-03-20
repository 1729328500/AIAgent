package top.whyh.agentai.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * 从用户口语中提取系统名称的工具类
 */
public class SystemNameExtractor {

    /**
     * 提取核心系统名称
     */
    public static String extract(String userInput) {
        if (StringUtils.isBlank(userInput)) {
            return "";
        }
        // 第一步：移除前缀（扩展更多常见话术）
        String[] prefixes = {
                "帮我生成一个", "生成", "做一个", "帮我做", "需要一个", "开发一个",
                "帮我写一个", "写一个", "创建一个", "设计一个", "出一个"
        };
        String cleanInput = userInput.trim();
        for (String prefix : prefixes) {
            if (cleanInput.startsWith(prefix)) {
                cleanInput = cleanInput.substring(prefix.length()).trim();
                break;
            }
        }
        // 第二步：移除后缀
        String[] suffixes = {
                "的需求文档", "的需求分析", "的需求", "系统的需求", "文档", "的开发文档",
                "的PRD", "的产品文档", "的功能需求", "的详细需求"
        };
        for (String suffix : suffixes) {
            if (cleanInput.endsWith(suffix)) {
                cleanInput = cleanInput.substring(0, cleanInput.length() - suffix.length()).trim();
                break;
            }
        }
        // 第三步：兜底补充「系统」（如输入"电商订单" → "电商订单系统"）
        if (!cleanInput.endsWith("系统") && StringUtils.isNotBlank(cleanInput)) {
            cleanInput += "系统";
        }
        return cleanInput;
    }
}