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

    // Active when nexusai.ai.provider=openai (requires OPENAI_API_KEY env var)
    @Bean
    @ConditionalOnProperty(name = "nexusai.ai.provider", havingValue = "openai")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel) {
        log.info("AI provider configured: OpenAI");
        return ChatClient.create(openAiChatModel);
    }

    // Active by default when nexusai.ai.provider=ollama (or not set)
    // Requires Ollama running locally: docker compose up -d ollama
    @Bean
    @Primary
    @ConditionalOnProperty(name = "nexusai.ai.provider", havingValue = "ollama", matchIfMissing = true)
    public ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel) {
        log.info("AI provider configured: Ollama");
        return ChatClient.create(ollamaChatModel);
    }

    // Embeddings always use OpenAI text-embedding-3-small (1536 dimensions).
    // This matches the vector(1536) column in document_chunks and provides
    // consistent embedding quality regardless of the chat provider selected.
    // Requires OPENAI_API_KEY to be set for RAG features to work.
    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(OpenAiEmbeddingModel openAiEmbeddingModel) {
        log.info("Embedding model configured: OpenAI text-embedding-3-small (1536 dims)");
        return openAiEmbeddingModel;
    }
}
