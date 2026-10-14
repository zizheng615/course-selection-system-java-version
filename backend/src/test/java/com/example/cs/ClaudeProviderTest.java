package com.example.cs;

import com.example.cs.ai.AiContext;
import com.example.cs.ai.ClaudeProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Claude 提供方的开关逻辑测试。
 *
 * <p>这里**不发起真实请求**（没有 API Key 也发不出去），只验证最容易出错的
 * 「什么时候该启用我」这段判断 —— 一旦这段写错，要么在无 Key 环境下
 * 让整个 AI 功能不可用，要么在配置了 Key 时却不生效。
 *
 * <p>SDK 调用本身的正确性由编译期保证：{@code ClaudeProvider} 里对
 * {@code MessageCreateParams} 的构造会随 {@code mvn compile} 一起被类型检查，
 * 字段名或方法名写错会直接编译失败。
 */
class ClaudeProviderTest {

    private static final AiContext CONTEXT = new AiContext("STUDENT", "张伟");

    private ClaudeProvider provider(boolean enabled, String apiKey) {
        return new ClaudeProvider(enabled, apiKey, "claude-opus-4-8", 2048);
    }

    @Test
    @DisplayName("未配置 API Key 时不可用，且不抛异常")
    void disabledWhenApiKeyMissing() {
        ClaudeProvider p = provider(true, "");
        assertFalse(p.isAvailable(), "空 Key 应当判定为不可用");

        // 不可用时 tryAnswer 必须安全返回 empty，让调用链继续降级
        Optional<?> answer = p.tryAnswer("怎么选课", CONTEXT);
        assertTrue(answer.isEmpty(), "不可用时应当返回 empty 而不是抛异常");
    }

    @Test
    @DisplayName("API Key 为空白字符时同样视为未配置")
    void disabledWhenApiKeyBlank() {
        assertFalse(provider(true, "   ").isAvailable());
    }

    @Test
    @DisplayName("配置了 Key 且未显式关闭时可用")
    void enabledWhenApiKeyPresent() {
        assertTrue(provider(true, "sk-ant-test-key").isAvailable());
    }

    @Test
    @DisplayName("enabled=false 时即使有 Key 也不可用（可强制走本地知识库）")
    void disabledByConfigSwitch() {
        assertFalse(provider(false, "sk-ant-test-key").isAvailable());
    }
}
