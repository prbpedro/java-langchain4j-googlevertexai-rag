package app;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmbeddingStoreFactory {

    public static EmbeddingStore<TextSegment> createAndIngest(
            final EmbeddingModel model)
            throws InterruptedException, ExecutionException, URISyntaxException, IOException {

        final QdrantEmbeddingStore qdrantEmbeddingStore = QdrantEmbeddingStore
                .builder()
                .collectionName(QDrantConstants.COLLECTION_NAME)
                .host(QDrantConstants.HOST)
                .port(QDrantConstants.PORT)
                .useTls(QDrantConstants.USE_TLS)
                .build();

        DocumentIngestor.ingestDocuments(model, qdrantEmbeddingStore);

        return qdrantEmbeddingStore;
    }
}
