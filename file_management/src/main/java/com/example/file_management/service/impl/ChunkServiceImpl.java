package com.example.file_management.service.impl;

import com.example.file_management.controller.vo.ChunkVO;
import com.example.file_management.entity.Chunk;
import com.example.file_management.enums.PermissionTargetType;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.mapper.ChunkMapper;
import com.example.file_management.service.ChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChunkServiceImpl implements ChunkService {
    private static final Logger logger = LoggerFactory.getLogger(ChunkServiceImpl.class);

    //一次检索最多召回多少片段：topK直接决定embedding之后的回表量和上下文长度，
    //必须有上限，否则一个请求就能把整张chunks表拉出来
    private static final int MAX_TOP_K = 50;

    //每一路各自比k多召回一些再融合：只召回k条的话，某一路的第k+1名哪怕在另一路排第一
    //也进不了候选集，融合就退化成"两个topK拼接"，两路互补的意义没了
    private static final int RECALL_MULTIPLIER = 3;
    //单路召回的硬上限：向量侧是Milvus的topK，关键字侧是MySQL的LIMIT，
    //两边都不能被k线性放大到无界
    private static final int MAX_RECALL_PER_CHANNEL = 100;

    //RRF(Reciprocal Rank Fusion)的平滑常数，取原论文和ES/Milvus的默认值60：
    //调大越拉平各路头部之间的差距，调小越偏袒各路第一名
    private static final int RRF_K = 60;
    //两路等权。要更偏语义就调大向量权重，要更偏精确词面就调大关键字权重
    private static final double VECTOR_WEIGHT = 1.0;
    private static final double KEYWORD_WEIGHT = 1.0;

    //日志里chunkId只留UUID前8位，一行才放得下一整路的召回顺序
    private static final int CHUNK_ID_LOG_LENGTH = 8;
    private static final int MAX_LOGGED_CHUNK_IDS = 20;
    private static final int MAX_LOGGED_QUERY_LENGTH = 60;

    @Autowired
    private ChunkMapper chunkMapper;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private PermissionIntegration permissionIntegration;

    @Override
    public void addChunk(Chunk chunk) {
        chunkMapper.addChunk(chunk);
    }

    @Override
    public void addChunkList(List<Chunk> chunkList) {
        chunkMapper.addChunkList(chunkList);
    }

    @Override
    public Chunk queryById(int id) {
        return chunkMapper.queryById(id);
    }

    @Override
    public List<Chunk> queryByRepositoryIdAndFileId(int repositoryId, int fileId) {
        List<Chunk> res = new ArrayList<>();
        res = chunkMapper.queryByRepositoryIdAndFileId(repositoryId,fileId);
        return res;
    }

    @Override
    public List<Chunk> queryByChunkId(List<String> chunkIds) {
        //空列表会让mapper的foreach生成IN ()的非法SQL
        if (chunkIds == null || chunkIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Chunk> res = new ArrayList<>();
        res = chunkMapper.queryByChunkId(chunkIds);
        return res;
    }

    @Override
    public void deleteAllChunk(int repositoryId, int fileId) {
        chunkMapper.deleteAllChunk(repositoryId,fileId);
    }

    //两路召回：向量召回负责语义相近但字面不同的片段，关键字召回负责专有名词、
    //编号、报错码这类embedding容易糊掉、但字面完全一致的片段。
    //两路各自多召回一些，去重融合排序后再截到k条。
    @Override
    public List<ChunkVO> queryTopKSimilarity(int k, String userInput, int userId) {
        if (k <= 0 || k > MAX_TOP_K) {
            throw new IllegalArgumentException("k must be between 1 and " + MAX_TOP_K);
        }
        //空问题没有可向量化的内容，也没有可分词的内容，直接短路，省一次embedding调用
        if (userInput == null || userInput.isBlank()) {
            logger.info("hybrid recall skipped: blank query, user={}", userId);
            return new ArrayList<>();
        }

        long startedAt = System.currentTimeMillis();
        int recallSize = Math.min(k * RECALL_MULTIPLIER, MAX_RECALL_PER_CHANNEL);

        //两路互不依赖，任何一路挂了都拿另一路的结果继续；只有两路都挂了才把异常抛出去，
        //否则一次Milvus宕机或者全文索引没建会被伪装成"什么都没搜到"
        RuntimeException vectorFailure = null;
        RuntimeException keywordFailure = null;

        //两路分开计时：向量那一路要先调embedding再查Milvus，关键字那一路是本地一条SQL，
        //耗时差一个数量级是正常的，分开记才能一眼看出慢在哪
        List<String> vectorChunkIdList = new ArrayList<>();
        long vectorStartedAt = System.currentTimeMillis();
        try {
            vectorChunkIdList = vectorRecall(userInput, recallSize);
        } catch (RuntimeException e) {
            vectorFailure = e;
            logger.error("vector recall failed, degrading to keyword-only: {}", e.toString());
        }
        long vectorCost = System.currentTimeMillis() - vectorStartedAt;

        List<Chunk> keywordChunkList = new ArrayList<>();
        long keywordStartedAt = System.currentTimeMillis();
        try {
            keywordChunkList = keywordRecall(userInput, recallSize);
        } catch (RuntimeException e) {
            keywordFailure = e;
            //全文索引没建的话MySQL报ERROR 1191，降级成纯向量召回比整个检索接口500更合适
            logger.error("keyword recall failed, degrading to vector-only: {}", e.toString());
        }
        long keywordCost = System.currentTimeMillis() - keywordStartedAt;

        if (vectorFailure != null && keywordFailure != null) {
            throw vectorFailure;
        }

        //关键字这一路查的就是chunks表，正文已经在手里；只有向量那一路命中的片段才需要回表
        Map<String, Chunk> chunkByChunkId = new HashMap<>();
        List<String> keywordChunkIdList = new ArrayList<>();
        for (Chunk each : keywordChunkList) {
            keywordChunkIdList.add(each.getChunkId());
            chunkByChunkId.put(each.getChunkId(), each);
        }

        logChannelOrder(vectorChunkIdList, keywordChunkIdList);

        List<FusedHit> fusedHitList = fuse(vectorChunkIdList, keywordChunkIdList);
        if (fusedHitList.isEmpty()) {
            logger.info("hybrid recall done in {}ms | user={} k={} recallSize={} query=\"{}\""
                            + " | vector={}hits/{}ms keyword={}hits/{}ms | no candidate",
                    System.currentTimeMillis() - startedAt, userId, k, recallSize,
                    abbreviate(userInput),
                    vectorChunkIdList.size(), vectorCost,
                    keywordChunkIdList.size(), keywordCost);
            return new ArrayList<>();
        }
        //先截到k再回表：名次在融合这一步就定死了，第k名之后的片段不会出现在结果里，
        //没必要为它们取正文
        List<FusedHit> topKHitList =
                fusedHitList.subList(0, Math.min(k, fusedHitList.size()));

        List<String> missingChunkIdList = new ArrayList<>();
        for (FusedHit hit : topKHitList) {
            if (!chunkByChunkId.containsKey(hit.chunkId)) {
                missingChunkIdList.add(hit.chunkId);
            }
        }
        for (Chunk each : queryByChunkId(missingChunkIdList)) {
            chunkByChunkId.put(each.getChunkId(), each);
        }

        //一次批量取用户可读的全部文件id，命中多少条都只问一次权限服务
        Set<Integer> readableFileIdSet = permissionIntegration.batchPermissionCheck(
                PermissionTargetType.FILE.getCode(), userId);

        List<ChunkVO> res = new ArrayList<>();
        List<String> orphanChunkIdList = new ArrayList<>();
        int visibleCount = 0;
        for (FusedHit hit : topKHitList) {
            Chunk chunk = chunkByChunkId.get(hit.chunkId);
            //向量库有、MySQL没有：两边不一致时跳过，不返回没有正文的空壳
            if (chunk == null) {
                orphanChunkIdList.add(hit.chunkId);
                continue;
            }
            boolean visible = readableFileIdSet.contains(chunk.getFileId());
            if (visible) {
                visibleCount++;
            }
            logHit(res.size() + 1, hit, chunk, visible);
            res.add(convertToVO(chunk, visible));
        }

        //两个存储不一致是真问题而不是噪声：向量还在、正文没了，说明某次删除只清了一侧。
        //静默跳过会让这种不一致一直查不出来，所以单独报一条
        if (!orphanChunkIdList.isEmpty()) {
            logger.warn("{} hit(s) exist in the vector store but not in chunks table,"
                            + " skipped: {}",
                    orphanChunkIdList.size(), shorten(orphanChunkIdList));
        }

        //一次检索一行汇总，可直接grep "hybrid recall done"：
        //overlap是两路都召回到的条数，它是判断两路是否真的互补的第一手指标——
        //常年接近0说明两路各查各的，常年接近candidates说明第二路没带来新东西
        logger.info("hybrid recall done in {}ms | user={} k={} recallSize={} query=\"{}\""
                        + " | vector={}hits/{}ms keyword={}hits/{}ms"
                        + " | candidates={} overlap={} | returned={} visible={} dropped={}",
                System.currentTimeMillis() - startedAt, userId, k, recallSize,
                abbreviate(userInput),
                vectorChunkIdList.size(), vectorCost,
                keywordChunkIdList.size(), keywordCost,
                fusedHitList.size(), countOverlap(fusedHitList),
                res.size(), visibleCount, orphanChunkIdList.size());
        return res;
    }

    //两路各自的原始顺序，用来核对"这条到底是哪一路捞回来的、排第几"。
    //明细走DEBUG：一次检索最多刷两行，但每行可能有上百个id，默认不占日志
    private void logChannelOrder(List<String> vectorChunkIdList,
                                 List<String> keywordChunkIdList) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        logger.debug("vector recall order  ({} hits): {}",
                vectorChunkIdList.size(), shorten(vectorChunkIdList));
        logger.debug("keyword recall order ({} hits): {}",
                keywordChunkIdList.size(), shorten(keywordChunkIdList));
    }

    //融合后每一条的来源与名次。rank显示成"-"表示这一路没召回到它，
    //两路都有值的那几条就是被RRF加权顶上去的
    private void logHit(int position, FusedHit hit, Chunk chunk, boolean visible) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        logger.debug("  #{} chunk={} rrf={} vector={} keyword={} file={} index={} visible={}",
                position,
                shortenChunkId(hit.chunkId),
                String.format("%.6f", hit.score),
                formatRank(hit.vectorRank),
                formatRank(hit.keywordRank),
                chunk.getFileId(),
                chunk.getChunkIndex(),
                visible);
    }

    private int countOverlap(List<FusedHit> fusedHitList) {
        int overlap = 0;
        for (FusedHit hit : fusedHitList) {
            if (hit.vectorRank != FusedHit.ABSENT_RANK
                    && hit.keywordRank != FusedHit.ABSENT_RANK) {
                overlap++;
            }
        }
        return overlap;
    }

    private static String formatRank(int rank) {
        return rank == FusedHit.ABSENT_RANK ? "-" : "#" + rank;
    }

    //chunkId是UUID，日志里只留前8位够定位了，全量打出来一行放不下
    private static String shortenChunkId(String chunkId) {
        return chunkId.length() <= CHUNK_ID_LOG_LENGTH
                ? chunkId
                : chunkId.substring(0, CHUNK_ID_LOG_LENGTH);
    }

    private static String shorten(List<String> chunkIdList) {
        StringBuilder builder = new StringBuilder("[");
        int shown = Math.min(chunkIdList.size(), MAX_LOGGED_CHUNK_IDS);
        for (int index = 0; index < shown; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(shortenChunkId(chunkIdList.get(index)));
        }
        if (chunkIdList.size() > shown) {
            builder.append(", …+").append(chunkIdList.size() - shown);
        }
        return builder.append("]").toString();
    }

    //用户问题原样进日志会带换行、也可能很长，压成一行并截断，
    //日志是用来定位的，不是用来存档提问的
    private static String abbreviate(String userInput) {
        String oneLine = userInput.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= MAX_LOGGED_QUERY_LENGTH
                ? oneLine
                : oneLine.substring(0, MAX_LOGGED_QUERY_LENGTH) + "…";
    }

    //写入时同一个UUID既做Milvus的doc_id又做chunks.chunk_id，所以命中直接就是回表的键
    private List<String> vectorRecall(String userInput, int recallSize) {
        List<Document> hitList = vectorStore.similaritySearch(
                SearchRequest.builder().query(userInput).topK(recallSize).build());
        List<String> chunkIdList = new ArrayList<>();
        if (hitList == null) {
            return chunkIdList;
        }
        for (Document each : hitList) {
            chunkIdList.add(each.getId());
        }
        return chunkIdList;
    }

    //全文索引已经按相关度排好序，这里拿到的就是关键字这一路的名次
    private List<Chunk> keywordRecall(String userInput, int recallSize) {
        List<Chunk> hitList = chunkMapper.queryTopKByKeyword(userInput, recallSize);
        return hitList == null ? new ArrayList<>() : hitList;
    }

    //用名次而不是分数融合两路结果。
    //向量侧是[0,1]的余弦相似度，关键字侧是MySQL的MATCH...AGAINST相关度（tf-idf量级、无上界），
    //两个分数量纲不同，直接比大小没意义；归一化到同一区间也只是把各路第一名都拉成1.0，
    //丢掉的正是"这一路到底有多确信"。RRF只看名次：score = Σ weight / (RRF_K + rank)，
    //两路都命中的片段自然累加两份分数，去重和加权在同一步完成。
    private List<FusedHit> fuse(List<String> vectorChunkIdList,
                                List<String> keywordChunkIdList) {
        Map<String, FusedHit> hitByChunkId = new LinkedHashMap<>();
        accumulate(hitByChunkId, vectorChunkIdList, VECTOR_WEIGHT, true);
        accumulate(hitByChunkId, keywordChunkIdList, KEYWORD_WEIGHT, false);

        List<FusedHit> fusedHitList = new ArrayList<>(hitByChunkId.values());
        fusedHitList.sort(Comparator
                .comparingDouble((FusedHit hit) -> hit.score).reversed()
                //同分时先看向量名次：两路各自的第n名分数完全相同，
                //没有次级比较的话最终顺序就随map的迭代顺序漂
                .thenComparingInt(hit -> hit.vectorRank)
                .thenComparingInt(hit -> hit.keywordRank)
                //最后按chunkId定序，保证同样的输入永远给同样的输出
                .thenComparing(hit -> hit.chunkId));
        return fusedHitList;
    }

    private void accumulate(Map<String, FusedHit> hitByChunkId,
                            List<String> chunkIdList,
                            double weight,
                            boolean fromVector) {
        for (int index = 0; index < chunkIdList.size(); index++) {
            //RRF的rank从1开始，第一名不能落到除以RRF_K
            int rank = index + 1;
            FusedHit hit =
                    hitByChunkId.computeIfAbsent(chunkIdList.get(index), FusedHit::new);
            hit.score += weight / (RRF_K + rank);
            if (fromVector) {
                hit.vectorRank = rank;
            } else {
                hit.keywordRank = rank;
            }
        }
    }

    //一个候选片段在两路里的名次和融合后的总分。
    //没被某一路召回时那一路的名次留在ABSENT_RANK，排序时自然排在被召回过的后面
    private static final class FusedHit {
        static final int ABSENT_RANK = Integer.MAX_VALUE;

        private final String chunkId;
        private double score;
        private int vectorRank = ABSENT_RANK;
        private int keywordRank = ABSENT_RANK;

        private FusedHit(String chunkId) {
            this.chunkId = chunkId;
        }
    }

    //无权限的命中保留一行，让调用方知道"还有N条你看不到"，但fileName和chunkContent不带出去：
    //正文照发的话这个可见性标记就只是装饰，等于绕过了文件权限
    private ChunkVO convertToVO(Chunk chunk, boolean visible) {
        ChunkVO chunkVO = new ChunkVO();
        chunkVO.setId(chunk.getId());
        chunkVO.setFileId(chunk.getFileId());
        chunkVO.setChunkId(chunk.getChunkId());
        chunkVO.setOwnerId(chunk.getOwnerId());
        chunkVO.setRepositoryId(chunk.getRepositoryId());
        chunkVO.setChunkIndex(chunk.getChunkIndex());
        chunkVO.setVisible(visible);
        if (visible) {
            chunkVO.setFileName(chunk.getFileName());
            chunkVO.setChunkContent(chunk.getChunkContent());
        }
        return chunkVO;
    }

}
