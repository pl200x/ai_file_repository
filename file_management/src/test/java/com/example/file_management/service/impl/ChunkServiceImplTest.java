package com.example.file_management.service.impl;

import com.example.file_management.controller.vo.ChunkVO;
import com.example.file_management.entity.Chunk;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.mapper.ChunkMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkServiceImplTest {

    @Mock
    private ChunkMapper chunkMapper;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private PermissionIntegration permissionIntegration;

    @InjectMocks
    private ChunkServiceImpl chunkService;

    /**
     * The lookup is an IN query, so MySQL hands the rows back in index order.
     * Losing the vector store's ranking there would make topK meaningless.
     */
    @Test
    void hitsKeepSimilarityOrderEvenWhenTheLookupReturnsAnotherOrder() {
        stubHits("chunk-c", "chunk-a", "chunk-b");
        when(chunkMapper.queryByChunkId(anyList())).thenReturn(List.of(
                chunk("chunk-a", 7, 0),
                chunk("chunk-b", 7, 1),
                chunk("chunk-c", 7, 2)));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(3, "问题", 10);

        assertEquals(
                List.of("chunk-c", "chunk-a", "chunk-b"),
                result.stream().map(ChunkVO::getChunkId).toList());
    }

    @Test
    void chunkOfAReadableFileCarriesItsText() {
        stubHits("chunk-a");
        when(chunkMapper.queryByChunkId(anyList()))
                .thenReturn(List.of(chunk("chunk-a", 7, 0)));
        stubReadableFiles(7);

        ChunkVO only = chunkService.queryTopKSimilarity(1, "问题", 10).get(0);

        assertTrue(only.isVisible());
        assertEquals("片段正文", only.getChunkContent());
        assertEquals("design doc", only.getFileName());
        assertEquals(7, only.getFileId());
        assertEquals(3, only.getRepositoryId());
    }

    /**
     * The hit still counts, so the caller can say "N more matches you cannot
     * open", but text and file name must not leak past the permission check.
     */
    @Test
    void chunkOfAnUnreadableFileIsMarkedAndStripped() {
        stubHits("chunk-a", "chunk-b");
        when(chunkMapper.queryByChunkId(anyList())).thenReturn(List.of(
                chunk("chunk-a", 7, 0),
                chunk("chunk-b", 9, 0)));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(2, "问题", 10);

        assertEquals(2, result.size());
        assertTrue(result.get(0).isVisible());

        ChunkVO hidden = result.get(1);
        assertFalse(hidden.isVisible());
        assertNull(hidden.getChunkContent());
        assertNull(hidden.getFileName());
        //id仍然返回：调用方要能数出"看不到的有几条"
        assertEquals(9, hidden.getFileId());
        assertEquals("chunk-b", hidden.getChunkId());
    }

    /**
     * Milvus keeps the vector while MySQL lost the row when a delete only
     * cleaned one side. An entry with no text is worse than no entry.
     */
    @Test
    void hitsMissingFromMysqlAreSkipped() {
        stubHits("chunk-a", "chunk-gone");
        when(chunkMapper.queryByChunkId(anyList()))
                .thenReturn(List.of(chunk("chunk-a", 7, 0)));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(2, "问题", 10);

        assertEquals(1, result.size());
        assertEquals("chunk-a", result.get(0).getChunkId());
    }

    @Test
    void blankInputCostsNoEmbeddingCall() {
        assertTrue(chunkService.queryTopKSimilarity(4, "   ", 10).isEmpty());

        verifyNoInteractions(vectorStore, chunkMapper, permissionIntegration);
    }

    @Test
    void topKOutsideTheAllowedRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> chunkService.queryTopKSimilarity(0, "问题", 10));
        assertThrows(IllegalArgumentException.class,
                () -> chunkService.queryTopKSimilarity(51, "问题", 10));

        verifyNoInteractions(vectorStore, chunkMapper, permissionIntegration);
    }

    @Test
    void noHitFromEitherChannelMeansNoLookupAndNoPermissionCall() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        stubKeywordHits();

        assertTrue(chunkService.queryTopKSimilarity(4, "问题", 10).isEmpty());

        verify(chunkMapper, never()).queryByChunkId(anyList());
        verifyNoInteractions(permissionIntegration);
    }

    /**
     * The whole point of a second channel: a chunk both channels agree on
     * outranks one that only a single channel found, even when that single
     * channel ranked it first.
     */
    @Test
    void chunkFoundByBothChannelsOutranksSingleChannelHits() {
        stubHits("vector-only", "agreed");
        stubKeywordHits(chunk("agreed", 7, 1), chunk("keyword-only", 7, 2));
        when(chunkMapper.queryByChunkId(anyList()))
                .thenReturn(List.of(chunk("vector-only", 7, 0)));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(3, "问题", 10);

        //agreed拿到两路的分数相加，vector-only只有向量第一名的一份
        assertEquals(
                List.of("agreed", "vector-only", "keyword-only"),
                result.stream().map(ChunkVO::getChunkId).toList());
    }

    /**
     * Exact wording the embedding blurs away — an error code, a ticket id —
     * has to survive on the keyword channel alone.
     */
    @Test
    void keywordOnlyHitStillReachesTheResult() {
        stubHits();
        stubKeywordHits(chunk("keyword-only", 7, 0));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(4, "ERROR 1451", 10);

        assertEquals(1, result.size());
        assertEquals("keyword-only", result.get(0).getChunkId());
        assertEquals("片段正文", result.get(0).getChunkContent());
    }

    /**
     * The keyword channel reads the chunks table already, so its hits arrive
     * with their text. Looking them up a second time would be a wasted query.
     */
    @Test
    void keywordHitsCarryTheirOwnTextAndAreNotLookedUpAgain() {
        stubHits("agreed");
        stubKeywordHits(chunk("agreed", 7, 0));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(4, "问题", 10);

        assertEquals(1, result.size());
        assertEquals("片段正文", result.get(0).getChunkContent());
        verify(chunkMapper, never()).queryByChunkId(anyList());
    }

    /**
     * Each channel over-fetches so the other one's runner-up can still win the
     * fusion; recalling only k would make this two top-k lists glued together.
     */
    @Test
    void bothChannelsRecallMoreCandidatesThanTopK() {
        stubHits("chunk-a");
        stubKeywordHits();
        when(chunkMapper.queryByChunkId(anyList()))
                .thenReturn(List.of(chunk("chunk-a", 7, 0)));
        stubReadableFiles(7);

        chunkService.queryTopKSimilarity(4, "问题", 10);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertEquals(12, request.getValue().getTopK());
        verify(chunkMapper).queryTopKByKeyword("问题", 12);
    }

    /**
     * The full text index is a migration away from not existing (ERROR 1191).
     * Losing the second channel must cost recall, not the whole endpoint.
     */
    @Test
    void keywordChannelFailureDegradesToVectorOnly() {
        stubHits("chunk-a");
        when(chunkMapper.queryTopKByKeyword(anyString(), anyInt()))
                .thenThrow(new RuntimeException("ERROR 1191: Can't find FULLTEXT index"));
        when(chunkMapper.queryByChunkId(anyList()))
                .thenReturn(List.of(chunk("chunk-a", 7, 0)));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(4, "问题", 10);

        assertEquals(1, result.size());
        assertEquals("chunk-a", result.get(0).getChunkId());
    }

    @Test
    void vectorChannelFailureDegradesToKeywordOnly() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("milvus is down"));
        stubKeywordHits(chunk("keyword-only", 7, 0));
        stubReadableFiles(7);

        List<ChunkVO> result = chunkService.queryTopKSimilarity(4, "问题", 10);

        assertEquals(1, result.size());
        assertEquals("keyword-only", result.get(0).getChunkId());
    }

    /**
     * With both stores unreachable there is nothing to degrade to. Returning an
     * empty list would read as "nothing matched" and hide the outage.
     */
    @Test
    void bothChannelsFailingSurfacesTheOutage() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("milvus is down"));
        when(chunkMapper.queryTopKByKeyword(anyString(), anyInt()))
                .thenThrow(new RuntimeException("mysql is down"));

        assertThrows(RuntimeException.class,
                () -> chunkService.queryTopKSimilarity(4, "问题", 10));

        verifyNoInteractions(permissionIntegration);
    }

    private void stubHits(String... chunkIds) {
        List<Document> documents = new java.util.ArrayList<>();
        for (String chunkId : chunkIds) {
            //写入时doc_id就是chunk_id，检索侧靠它回表
            documents.add(Document.builder().id(chunkId).text("片段正文").build());
        }
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);
    }

    private void stubKeywordHits(Chunk... chunks) {
        when(chunkMapper.queryTopKByKeyword(anyString(), anyInt()))
                .thenReturn(List.of(chunks));
    }

    private void stubReadableFiles(Integer... fileIds) {
        when(permissionIntegration.batchPermissionCheck(anyString(), anyInt()))
                .thenReturn(Set.of(fileIds));
    }

    private static Chunk chunk(String chunkId, int fileId, int chunkIndex) {
        Chunk chunk = new Chunk();
        chunk.setId(chunkIndex + 1);
        chunk.setFileId(fileId);
        chunk.setFileName("design doc");
        chunk.setChunkId(chunkId);
        chunk.setChunkContent("片段正文");
        chunk.setOwnerId(10);
        chunk.setRepositoryId(3);
        chunk.setChunkIndex(chunkIndex);
        return chunk;
    }
}
