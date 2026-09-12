package com.codeatlas.ai.provider;

/**
 * 大语言模型抽象。
 *
 * <p>业务代码只依赖本接口，不绑定任何具体厂商，
 * 切换模型只需更换实现与配置（见 docs/architecture.md 第 9 节）。
 */
public interface ChatProvider {

    /**
     * 执行一次对话。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 模型生成的文本
     */
    String chat(String systemPrompt, String userPrompt);

    /** 模型标识，用于记录执行日志。 */
    String modelName();
}
