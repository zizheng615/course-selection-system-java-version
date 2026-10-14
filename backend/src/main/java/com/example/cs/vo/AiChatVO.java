package com.example.cs.vo;

import com.example.cs.ai.AiAnswer;
import lombok.Data;

import java.util.List;

/**
 * AI 问答返回体。
 */
@Data
public class AiChatVO {

    /** 回答正文（纯文本，前端渲染时做最简的加粗/换行处理） */
    private String answer;

    /** 回答来源：本地知识库 / Claude，前端显示成一个小标签，让用户知道答案的可信度 */
    private String source;

    /** 追问建议 */
    private List<String> suggestions;

    public static AiChatVO of(AiAnswer answer) {
        AiChatVO vo = new AiChatVO();
        vo.answer = answer.content();
        vo.source = answer.source();
        vo.suggestions = answer.suggestions();
        return vo;
    }
}
