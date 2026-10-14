package com.example.cs.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 基于内置知识库的回答提供方。
 *
 * <p>排在 Claude 之前（{@code @Order(1)}）：知识库里的答案是与系统实际行为核对过的，
 * 属于「确定的回答」；模型则可能泛泛而谈。能用确定的就不用生成式的 ——
 * 只有当问题在知识库里找不到依据时，才交给后面的提供方。
 *
 * <p>匹配策略：问题中包含的每个关键词按**长度**计分（越长的词越具体，权重越高），
 * 取总分最高的一条，且总分需达到阈值。长度加权很重要 ——
 * 「怎么管理选课记录」里既有「选课记录」（4 分）也有「选课」（2 分），
 * 加权后能正确落到「选课记录」那条而不是泛泛的「怎么选课」。
 */
@Slf4j
@Component
@Order(1)
public class KnowledgeBaseProvider implements AiAnswerProvider {

    /** 总分门槛：小于该值认为没有把握，交给下一个提供方 */
    private static final int MIN_SCORE = 2;

    @Override
    public String name() {
        return "本地知识库";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<AiAnswer> tryAnswer(String question, AiContext context) {
        if (!StringUtils.hasText(question)) {
            return Optional.empty();
        }
        String normalized = normalize(question);

        KnowledgeBase.Entry best = null;
        int bestScore = 0;
        for (KnowledgeBase.Entry entry : KnowledgeBase.ENTRIES) {
            // 角色过滤：条目限定了角色，而当前用户不在其中则跳过
            if (!entry.roles().isEmpty() && !entry.roles().contains(context.role())) {
                continue;
            }
            int score = score(normalized, entry);
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }

        if (best == null || bestScore < MIN_SCORE) {
            log.debug("知识库未命中：question={} role={}", question, context.role());
            return Optional.empty();
        }
        log.debug("知识库命中：entry={} score={}", best.id(), bestScore);
        return Optional.of(AiAnswer.of(best.answer(), name(), best.suggestions()));
    }

    private int score(String normalizedQuestion, KnowledgeBase.Entry entry) {
        int total = 0;
        for (String keyword : entry.keywords()) {
            if (normalizedQuestion.contains(keyword)) {
                total += keyword.length();
            }
        }
        return total;
    }

    /** 去掉空白并转小写，让「怎么 选课」和「怎么选课」等价，英文触发词也不区分大小写 */
    private String normalize(String s) {
        return s.toLowerCase().replaceAll("\\s+", "");
    }
}
