package com.example.cs.ai;

import java.util.Optional;

/**
 * AI 回答的提供方。
 *
 * <p>这是本模块的扩展点：{@link AiService} 按 {@code @Order} 依次询问所有可用的提供方，
 * 谁先给出答案就用谁。新增一种回答来源（本地知识库、Claude、日后接入的其他模型…）
 * 只需要实现本接口并注册为 Bean，<b>不需要改动 AiService 或任何既有提供方</b>。
 */
public interface AiAnswerProvider {

    /** 提供方名称，用于日志与前端展示 */
    String name();

    /** 当前是否可用。例如 Claude 提供方需要配置了 API Key 才可用。 */
    boolean isAvailable();

    /**
     * 尝试回答。
     *
     * <p>约定：<b>没有把握时必须返回 {@link Optional#empty()}</b>，
     * 让调用链继续往下走，而不是硬编一个不可靠的答案。
     * 内部异常也应自行捕获并返回 empty，让上层能优雅降级。
     */
    Optional<AiAnswer> tryAnswer(String question, AiContext context);
}
