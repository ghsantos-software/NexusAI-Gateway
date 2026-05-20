package com.nexusai.gateway.ai;

import com.nexusai.gateway.ai.dto.ChatRequest;
import com.nexusai.gateway.ai.dto.ChatResponse;
import com.nexusai.gateway.shared.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Gateway", description = "Send prompts through the DLP-protected AI gateway")
@SecurityRequirement(name = "Bearer Authentication")
public class AiGatewayController {

    private final AiGatewayService aiGatewayService;

    @PostMapping("/chat")
    @Operation(
            summary = "Send a chat message",
            description = """
                    Sends a prompt through the full NexusAI pipeline:
                    1. DLP masking (CPF, email, phone, credit card)
                    2. Cache lookup
                    3. LLM call (Ollama or OpenAI)
                    4. Async audit log
                    """
    )
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(aiGatewayService.chat(request));
    }
}
