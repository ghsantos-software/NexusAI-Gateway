package com.nexusai.gateway.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "nexusai.ai.provider", havingValue = "openai")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        log.info("AI provider configured: OpenAI");
        return ChatClient.create(openAiChatModel);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "nexusai.ai.provider", havingValue = "ollama", matchIfMissing = true)
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        log.info("AI provider configured: Ollama");
        return ChatClient.create(ollamaChatModel);
    }

    // Embeddings always use OpenAI text-embedding-3-small (1536 dims) regardless of the chat
    // provider, so the vector column in document_chunks stays consistent. Requires OPENAI_API_KEY.
    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(OpenAiEmbeddingModel openAiEmbeddingModel) {
        log.info("Embedding model configured: OpenAI text-embedding-3-small (1536 dims)");
        return openAiEmbeddingModel;
    }
}
