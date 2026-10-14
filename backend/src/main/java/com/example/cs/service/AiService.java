package com.example.cs.service;

import com.example.cs.ai.AiAnswer;
import com.example.cs.ai.AiAnswerProvider;
import com.example.cs.ai.AiContext;
import com.example.cs.vo.AiChatVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 问答编排。
 *
 * <p>本身不包含任何回答逻辑，只负责「按顺序问一遍可用的提供方，谁先答上来就用谁」。
 * 新增回答来源不需要改这个类 —— 实现 {@link AiAnswerProvider} 并注册成 Bean 即可，
 * 顺序由提供方自己的 {@code @Order} 决定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    /** Spring 会按 @Order 排好序注入 */
    private final List<AiAnswerProvider> providers;

    public AiChatVO ask(String question, AiContext context) {
        for (AiAnswerProvider provider : providers) {
            if (!provider.isAvailable()) {
                continue;
            }
            Optional<AiAnswer> answer = provider.tryAnswer(question, context);
            if (answer.isPresent()) {
                return AiChatVO.of(answer.get());
            }
        }
        log.info("所有提供方均未给出回答：question={} role={}", question, context.role());
        // 兜底话术刻意不举具体菜单例子：不同角色能看到的菜单不一样，
        // 举「课程管理」这种管理端菜单会把学生和教师引到他们根本没有的页面上。
        //
        // 来源标记为「未匹配」而不是「本地知识库」：知识库命中与"所有提供方都没答上来"
        // 是两种完全不同的情况，用同一个标签会让用户误以为这是知识库给出的答案，
        // 也会让测试无法区分「角色隔离生效」和「知识库条目写错了」。
        return AiChatVO.of(AiAnswer.of(
                """
                这个问题我没把握给出准确答案 🤔

                我的职责是解答**本选课系统的使用方法**，可以先换个说法再问一次，
                或者从下面这些问题里挑一个 👇""",
                "未匹配",
                suggestionsFor(context.role())));
    }

    /** 按角色给出推荐的快捷问题，前端在对话框空态和兜底回答里都会用到 */
    public List<String> suggestionsFor(String role) {
        return SUGGESTIONS.getOrDefault(role, SUGGESTIONS.get("DEFAULT"));
    }

    private static final Map<String, List<String>> SUGGESTIONS = Map.of(
            "STUDENT", List.of(
                    "怎么选课",
                    "为什么有的课选不了",
                    "在哪看课表和成绩",
                    "怎么退课"),
            "TEACHER", List.of(
                    "怎么查看学生名单",
                    "怎么录入成绩",
                    "系统有哪些功能"),
            "ADMIN", List.of(
                    "怎么新增课程",
                    "怎么排课",
                    "怎么重置学生密码",
                    "怎么管理选课记录",
                    "统计报表在哪里"),
            "DEFAULT", List.of("系统有哪些功能"));
}
