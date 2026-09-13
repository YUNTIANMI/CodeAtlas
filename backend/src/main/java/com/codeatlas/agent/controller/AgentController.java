package com.codeatlas.agent.controller;

import com.codeatlas.agent.dto.AgentRequest;
import com.codeatlas.agent.dto.AgentResponse;
import com.codeatlas.agent.dto.ToolCallVO;
import com.codeatlas.agent.service.AgentService;
import com.codeatlas.auth.AuthUser;
import com.codeatlas.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI Agent 接口。
 *
 * <pre>
 * POST /api/v1/projects/{projectId}/agent/run       执行任务（自动调用只读工具）
 * GET  /api/v1/projects/{projectId}/agent/tool-calls 工具调用记录
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public Result<AgentResponse> run(@PathVariable Long projectId,
                                     @Valid @RequestBody AgentRequest request,
                                     Authentication authentication) {
        return Result.success(agentService.run(projectId, currentUserId(authentication),
                request.getTask()));
    }

    @GetMapping("/tool-calls")
    public Result<List<ToolCallVO>> toolCalls(@PathVariable Long projectId,
                                              Authentication authentication) {
        return Result.success(agentService.history(projectId, currentUserId(authentication)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getUserId();
        }
        throw new IllegalStateException("认证主体类型异常，无法获取用户 ID");
    }
}
