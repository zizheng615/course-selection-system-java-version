package com.example.cs.controller;

import com.example.cs.ai.AiContext;
import com.example.cs.common.Result;
import com.example.cs.dto.AiChatDTO;
import com.example.cs.security.LoginUser;
import com.example.cs.security.UserContext;
import com.example.cs.service.AiService;
import com.example.cs.vo.AiChatVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI 助手", description = "系统使用问答，按登录角色给出针对性回答")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "提问",
            description = "先查内置知识库，未命中且配置了 Claude API Key 时再询问模型；"
                    + "角色由 JWT 决定，无需前端传递")
    @PostMapping("/chat")
    public Result<AiChatVO> chat(@Valid @RequestBody AiChatDTO dto) {
        LoginUser user = UserContext.require();
        AiContext context = new AiContext(user.role(), user.realName());
        return Result.ok(aiService.ask(dto.getQuestion(), context));
    }

    @Operation(summary = "当前角色的推荐问题")
    @GetMapping("/suggestions")
    public Result<List<String>> suggestions() {
        return Result.ok(aiService.suggestionsFor(UserContext.require().role()));
    }
}
