package com.codeatlas.agent.tool;

import java.util.Map;

/**
 * Agent 工具抽象。
 *
 * <p>设计约束（见 ADR-005）：
 * 1. 工具只读，不得修改代码、不得 Push、不得改数据库
 * 2. 每个工具内部强制按 projectId 过滤，杜绝跨项目访问
 * 3. 返回值一律为字符串，便于直接拼装进模型上下文
 */
public interface AgentTool {

    /** 工具名，与模型交互时使用。 */
    String name();

    /** 工具说明，会注入到提示词中供模型选择。 */
    String description();

    /** 参数说明，用于提示词。 */
    String parametersDescription();

    /**
     * 执行工具。
     *
     * @param projectId 项目 ID，工具必须以此限制数据范围
     * @param input     模型给出的入参
     * @return 执行结果（文本）
     */
    String execute(Long projectId, Map<String, String> input);
}
