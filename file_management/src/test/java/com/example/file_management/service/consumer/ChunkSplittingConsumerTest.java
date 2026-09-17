package com.example.file_management.service.consumer;

import com.example.file_management.entity.Chunk;
import com.example.file_management.entity.ChunkSplittingMessage;
import com.example.file_management.service.ChunkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkSplittingConsumerTest {

    @Mock
    private ChunkService chunkService;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private TokenTextSplitter tokenTextSplitter;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChunkSplittingConsumer consumer;

    @Test
    void splitChunksAreWrittenToBothStoresUnderTheSameChunkId() {
        when(tokenTextSplitter.split(any(Document.class)))
                .thenReturn(List.of(new Document("part one"), new Document("part two")));

        consumer.onChunkSplitting(payload(message("the whole document body")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentCaptor =
                ArgumentCaptor.forClass((Class<List<Document>>) (Class<?>) List.class);
        verify(vectorStore).add(documentCaptor.capture());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Chunk>> chunkCaptor =
                ArgumentCaptor.forClass((Class<List<Chunk>>) (Class<?>) List.class);
        verify(chunkService).addChunkList(chunkCaptor.capture());

        List<Document> documents = documentCaptor.getValue();
        List<Chunk> chunks = chunkCaptor.getValue();
        assertEquals(2, documents.size());
        assertEquals(2, chunks.size());

        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            Document document = documents.get(chunkIndex);
            Chunk chunk = chunks.get(chunkIndex);
            //Milvus doc_id and chunks.chunk_id must be the same value, that is
            //what makes the vector hit resolvable back to the stored text
            assertEquals(document.getId(), chunk.getChunkId());
            assertEquals(document.getId(),
                    document.getMetadata().get(ChunkSplittingConsumer.METADATA_CHUNK_ID));
            assertEquals(document.getText(), chunk.getChunkContent());
            assertEquals(chunkIndex, chunk.getChunkIndex());
            assertEquals(chunkIndex,
                    document.getMetadata().get(ChunkSplittingConsumer.METADATA_CHUNK_INDEX));
            assertEquals(7, document.getMetadata().get(ChunkSplittingConsumer.METADATA_FILE_ID));
            assertEquals(10, document.getMetadata().get(ChunkSplittingConsumer.METADATA_OWNER_ID));
            assertEquals(3, document.getMetadata().get(ChunkSplittingConsumer.METADATA_REPOSITORY_ID));
            assertEquals("design doc",
                    document.getMetadata().get(ChunkSplittingConsumer.METADATA_FILE_NAME));
            assertEquals(7, chunk.getFileId());
            assertEquals(3, chunk.getRepositoryId());
            assertEquals(10, chunk.getOwnerId());
        }
        assertNotEquals(chunks.get(0).getChunkId(), chunks.get(1).getChunkId());
    }

    /**
     * A retry or a redelivery splits the same file twice. Both stores are
     * cleared first so the consumer stays idempotent instead of doubling the
     * chunks and hitting uk_chunks_file_index.
     */
    @Test
    void bothStoresAreClearedBeforeTheNewChunksAreWritten() {
        when(tokenTextSplitter.split(any(Document.class)))
                .thenReturn(List.of(new Document("part one")));

        consumer.onChunkSplitting(payload(message("the whole document body")));

        InOrder order = inOrder(vectorStore, chunkService);
        order.verify(vectorStore).delete(any(Filter.Expression.class));
        order.verify(chunkService).deleteAllChunk(3, 7);
        order.verify(vectorStore).add(any());
        order.verify(chunkService).addChunkList(any());
    }

    @Test
    void blankContentIsSkippedWithoutTouchingEitherStore() {
        consumer.onChunkSplitting(payload(message("   ")));

        verifyNoInteractions(vectorStore, chunkService);
        verify(tokenTextSplitter, never()).split(any(Document.class));
    }

    @Test
    void aDocumentThatSplitsIntoNothingIsSkippedWithoutClearingTheStores() {
        when(tokenTextSplitter.split(any(Document.class))).thenReturn(List.of());

        consumer.onChunkSplitting(payload(message("the whole document body")));

        verifyNoInteractions(vectorStore, chunkService);
    }

    private static ChunkSplittingMessage message(String content) {
        return new ChunkSplittingMessage(7, "design doc", content, 10, 3);
    }

    private static String payload(ChunkSplittingMessage message) {
        return new ObjectMapper().writeValueAsString(message);
    }
}
