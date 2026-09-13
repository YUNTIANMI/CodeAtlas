package com.codeatlas.ai.provider;

import java.util.List;

/**
 * 大语言模型抽象。
 *
 * <p>业务代码只依赖本接口，不绑定任何具体厂商，
 * 切换模型只需更换实现与配置（见 docs/architecture.md 第 9 节）。
 */
public interface ChatProvider {

    /**
     * 执行一次单轮对话。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型生成的文本
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 执行多轮对话。
     *
     * @param systemPrompt 系统提示词
     * @param history      历史对话，每个元素为 {角色, 内容}，角色为 USER / ASSISTANT
     * @param userPrompt   当前问题
     * @return 模型生成的文本
     */
    default String chat(String systemPrompt, List<String[]> history, String userPrompt) {
        if (history == null || history.isEmpty()) {
            return chat(systemPrompt, userPrompt);
        }
        StringBuilder context = new StringBuilder("历史对话：\n");
        for (String[] turn : history) {
            context.append(turn[0]).append("：").append(turn[1]).append("\n");
        }
        context.append("\n当前问题：").append(userPrompt);
        return chat(systemPrompt, context.toString());
    }

    /** 模型标识，用于记录执行日志。 */
    String modelName();
}
