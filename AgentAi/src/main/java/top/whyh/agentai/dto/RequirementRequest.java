package top.whyh.agentai.dto;


import jakarta.validation.constraints.NotBlank;

// 请求参数类
public class RequirementRequest {
    /**
     * 仅接收用户极简输入，如：帮我生成一个电商订单系统
     */
    @NotBlank(message = "请输入需要生成需求文档的系统描述（示例：帮我生成一个电商订单系统）")
    private String requirementDescription;

    public String getRequirementDescription() {
        return requirementDescription;
    }

    public void setRequirementDescription(String requirementDescription) {
        this.requirementDescription = requirementDescription;
    }
}