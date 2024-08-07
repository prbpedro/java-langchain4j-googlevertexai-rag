package app;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;

public class App {

    private static final String GOOGLE_VERTEX_AI_LOCATION = "GOOGLE_VERTEX_AI_LOCATION";
    private static final String GOOGLE_VERTEX_AI_PROJECT_ID = "GOOGLE_VERTEX_AI_PROJECT_ID";

    public static void main(String[] args)
            throws InterruptedException, ExecutionException, URISyntaxException, IOException, SQLException {

        PersistentChatMemoryStore store = new PersistentChatMemoryStore();

        Assistant assistant = createAssistant(store);
        Utils.startConversationWith(store, assistant);
    }

    private static Assistant createAssistant(PersistentChatMemoryStore store)
            throws InterruptedException, ExecutionException, URISyntaxException, IOException, SQLException {

        final String googleVertexAiEndpoint = System.getenv(GOOGLE_VERTEX_AI_LOCATION)
                + "-aiplatform.googleapis.com:443";

        EmbeddingModel embeddingModel = VertexAiEmbeddingModel
                .builder()
                .endpoint(googleVertexAiEndpoint)
                .project(System.getenv(GOOGLE_VERTEX_AI_PROJECT_ID))
                .location(System.getenv(GOOGLE_VERTEX_AI_LOCATION))
                .publisher("google")
                .modelName("text-multilingual-embedding-002")
                .maxRetries(3)
                .build();

        EmbeddingStore<TextSegment> embeddingStore = EmbeddingStoreFactory.createAndIngest(embeddingModel);

        ChatLanguageModel chatLanguageModel = VertexAiGeminiChatModel
                .builder()
                .project(System.getenv(GOOGLE_VERTEX_AI_PROJECT_ID))
                .location(System.getenv(GOOGLE_VERTEX_AI_LOCATION))
                .modelName("gemini-1.5-flash-001")
                .maxOutputTokens(1000)
                .temperature(0f)
                .build();

        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatLanguageModel);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.82)
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .build();

        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(15)
                .chatMemoryStore(store)
                .build();

        return AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
}
