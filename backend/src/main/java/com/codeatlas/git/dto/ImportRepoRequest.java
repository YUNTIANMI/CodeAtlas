package com.codeatlas.git.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 导入 Git 仓库请求。
 */
public class ImportRepoRequest {

    @NotBlank(message = "仓库地址不能为空")
    private String repoUrl;

    /** 凭证引用名，不传明文 Token。 */
    private String accessTokenRef;

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getAccessTokenRef() {
        return accessTokenRef;
    }

    public void setAccessTokenRef(String accessTokenRef) {
        this.accessTokenRef = accessTokenRef;
    }
}
