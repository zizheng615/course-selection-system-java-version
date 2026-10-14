package com.example.cs.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 基于 Claude API 的回答提供方（可选扩展）。
 *
 * <p><b>只有配置了 {@code ai.claude.api-key} 才会真正发起请求</b>；
 * 未配置时 {@link #isAvailable()} 返回 false，{@link AiService} 会直接跳过它，
 * 系统仍然完全可用（走本地知识库）。这样「加一个 AI 能力」不会变成「加一个必须联网的依赖」。
 *
 * <p>任何调用异常都会被捕获并返回 {@link Optional#empty()}，
 * 让调用链继续往后降级，而不是把异常抛给用户。
 */
@Slf4j
@Component
@Order(2)
public class ClaudeProvider implements AiAnswerProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String providerName;
    private final long maxTokens;
    private final boolean enabled;

    /** 懒创建：没有 Key 时不需要构造客户端，也避免启动期因凭据问题失败 */
    private volatile AnthropicClient client;

    public ClaudeProvider(@Value("${ai.claude.enabled:true}") boolean enabled,
                          @Value("${ai.claude.api-key:}") String apiKey,
                          @Value("${ai.claude.base-url:}") String baseUrl,
                          @Value("${ai.claude.model:claude-opus-4-8}") String model,
                          @Value("${ai.claude.provider-name:Claude}") String providerName,
                          @Value("${ai.claude.max-tokens:2048}") long maxTokens) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.providerName = providerName;
        this.maxTokens = maxTokens;
    }

    @Override
    public String name() {
        return providerName;
    }

    @Override
    public boolean isAvailable() {
        return enabled && StringUtils.hasText(apiKey);
    }

    @Override
    public Optional<AiAnswer> tryAnswer(String question, AiContext context) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(buildSystemPrompt(context))
                    .addUserMessage(question)
                    .build();

            Message response = client().messages().create(params);

            String text = extractText(response);
            if (!StringUtils.hasText(text)) {
                return Optional.empty();
            }
            return Optional.of(AiAnswer.of(text.trim(), name()));
        } catch (Exception e) {
            // 网络不通、Key 无效、限流……一律降级，不把异常抛给用户
            log.warn("Claude 调用失败，将降级到下一提供方: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private AnthropicClient client() {
        AnthropicClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
                    // 必须显式设置端点：Java SDK 不像 Python/JS SDK 那样读 ANTHROPIC_BASE_URL 环境变量，
                    // 不设的话请求会打到官方 api.anthropic.com，第三方兼容端点的 Key 在那里必然被拒。
                    if (StringUtils.hasText(baseUrl)) {
                        builder.baseUrl(baseUrl);
                    }
                    local = builder.build();
                    client = local;
                }
            }
        }
        return local;
    }

    /**
     * 系统提示词把系统的真实行为交代清楚，避免模型凭空编造不存在的功能。
     * 同时限定回答范围：这套系统的使用问题才回答，其他话题礼貌拒绝。
     */
    private String buildSystemPrompt(AiContext context) {
        return """
                你是「高校选课管理系统」的内置使用助手，正在服务一位%s（姓名：%s）。

                你的职责只有一个：解答这套系统的**使用方法**问题。

                系统背景（请严格依据这些事实回答，不要编造不存在的功能）：
                · 技术栈为 Vue 3 + Spring Boot + MySQL，前后端分离的 Web 系统。
                · 三种角色：学生、教师、教务管理员。
                · 学生端功能：选课中心（选课/退课/查看已选与成绩/总学分）、我的课表（周课表网格）。
                · 教师端功能：我的课程、查看选课学生名单、逐条录入成绩（0-100）。
                · 管理员功能分四组：教学资源（课程管理、排课管理）、选课管理（选课记录、统计报表）、
                  人员管理（学生管理、教师管理、账号管理）、统计报表（热门课程、专业分布、教师工作量）。
                · 选课会校验三件事：课程是否开放、是否重复选课、是否与已选课程时间冲突。
                  时间冲突按「星期 + 节次区间 + 周次区间」的重叠判断。
                · 名额扣减是数据库原子操作，不存在超卖；同一学生同一课程有唯一索引防重复。
                · 退课是软退课：状态置为已退课、归还名额，但记录保留（保留修读历史）。
                · 容量可以调整，但不能小于当前已选人数。
                · 学生账号默认是学号、教师默认是工号，初始密码 123456；忘记密码需管理员重置。
                · 有选课记录的学生、有课程的教师、有选课记录的课程都不能删除。

                回答要求：
                1. 用简体中文，语气友好、简洁，优先给出**可操作的步骤**。
                2. 涉及菜单路径时用「左侧菜单 → 子菜单」这种写法。
                3. 与系统使用无关的问题（如闲聊、编程求助、其他系统），礼貌说明你只能解答本系统的使用问题。
                4. 不确定的地方要明说不确定，不要编造。
                5. 不要输出 Markdown 标题（#），可以用短横线列表和加粗。回答控制在 300 字以内。
                """.formatted(context.roleName(), context.realName());
    }

    /** 拼接所有文本块。Opus 4.8 默认不返回思考内容，这里只需处理普通文本块。 */
    private String extractText(Message response) {
        List<ContentBlock> blocks = response.content();
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block.isText()) {
                sb.append(block.asText().text());
            }
        }
        return sb.toString();
    }
}
