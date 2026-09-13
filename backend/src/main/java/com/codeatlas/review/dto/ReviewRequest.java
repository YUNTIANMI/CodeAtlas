package com.codeatlas.review.dto;

import com.codeatlas.review.entity.ReviewResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 代码审查请求。
 */
public class ReviewRequest {

    /** 审查来源：FILE / SNIPPET / COMMIT / DIFF */
    @NotNull(message = "审查来源类型不能为空")
    private ReviewResult.SourceType sourceType;

    /** 来源标识：文件名、Commit Hash 等 */
    private String sourceRef;

    /** 代码内容或 Diff 内容 */
    @NotBlank(message = "审查内容不能为空")
    private String content;

    public ReviewResult.SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ReviewResult.SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
