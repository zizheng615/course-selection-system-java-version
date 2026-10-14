package com.example.cs.ai;

import java.util.List;

/**
 * 一次问答的结果。
 *
 * @param content     回答正文
 * @param source      回答来源（知识库 / Claude），前端会显示成一个小标签
 * @param suggestions 后续可追问的问题，前端渲染成快捷按钮
 */
public record AiAnswer(String content, String source, List<String> suggestions) {

    public static AiAnswer of(String content, String source) {
        return new AiAnswer(content, source, List.of());
    }

    public static AiAnswer of(String content, String source, List<String> suggestions) {
        return new AiAnswer(content, source, suggestions == null ? List.of() : suggestions);
    }
}
