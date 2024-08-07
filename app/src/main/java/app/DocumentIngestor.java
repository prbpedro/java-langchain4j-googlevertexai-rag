package app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DocumentIngestor {

    private static final String DOCUMENTS_FOLDER = "documents";
    private static final String PDF_EXTENSION = ".pdf";
    private static final String FILE_NAME = "file_name";

    public static void ingestDocuments(
            final EmbeddingModel embeddingModel,
            final EmbeddingStore<TextSegment> embeddingStore)
            throws URISyntaxException, IOException, InterruptedException, ExecutionException {

        final List<Document> documents = getDocuments();

        final EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(26, 0))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        try (QdrantClient qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(
                        QDrantConstants.HOST,
                        QDrantConstants.PORT,
                        QDrantConstants.USE_TLS)
                        .build())) {

            createCollectionIfNotExists(qdrantClient);
            ingestDocuments(documents, ingestor, qdrantClient);
        }
    }

    private static List<Document> getDocuments()
            throws URISyntaxException, FileNotFoundException, IOException {
        final List<Document> documents = new ArrayList<>();

        final ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
        final TextDocumentParser textParser = new TextDocumentParser();

        final Path path = Paths.get(
                App.class
                        .getClassLoader()
                        .getResource(DOCUMENTS_FOLDER)
                        .toURI());

        try (Stream<Path> walk = Files.walk(path)) {

            final Iterator<Path> walkIterator = walk.iterator();
            while (walkIterator.hasNext()) {
                final Path documentPath = walkIterator.next();
                final File file = documentPath.toFile();

                if (file.isFile()) {
                    final FileInputStream inputStream = new FileInputStream(file);

                    final Document document = documentPath.endsWith(PDF_EXTENSION)
                            ? pdfParser.parse(inputStream)
                            : textParser.parse(inputStream);

                    document.metadata().put(FILE_NAME, file.getName());
                    documents.add(document);
                }
            }
        }

        return documents;
    }

    private static void createCollectionIfNotExists(final QdrantClient qdrantClient)
            throws InterruptedException, ExecutionException {
        final Optional<String> collection = qdrantClient
                .listCollectionsAsync()
                .get()
                .stream()
                .filter(coll -> coll.equals(QDrantConstants.COLLECTION_NAME))
                .findFirst();

        if (collection.isPresent()) {
            log.info("Coleção já existe: {}", collection.get());
            return;
        }

        qdrantClient.createCollectionAsync(
                QDrantConstants.COLLECTION_NAME,
                VectorParams
                        .newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(768)
                        .build())
                .get();
    }

    private static void ingestDocuments(
            final List<Document> documents,
            final EmbeddingStoreIngestor ingestor,
            final QdrantClient qdrantClient)
            throws InterruptedException, ExecutionException {
        for (Document document : documents) {
            ScrollResponse scrollResponse = qdrantClient.scrollAsync(
                    ScrollPoints
                            .newBuilder()
                            .setCollectionName(QDrantConstants.COLLECTION_NAME)
                            .setFilter(
                                    Filter
                                            .newBuilder()
                                            .addMust(ConditionFactory
                                                    .matchKeyword(
                                                            FILE_NAME,
                                                            document.metadata().getString(FILE_NAME)))
                                            .build())
                            .setLimit(1)
                            .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                            .build())
                    .get();

            if (scrollResponse.getResultCount() > 0) {
                log.info("Documento já inserido: {}", document.metadata().getString(FILE_NAME));
            } else {
                ingestor.ingest(document);
            }
        }
    }
}
