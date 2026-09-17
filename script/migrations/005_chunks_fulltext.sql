-- 关键字召回：在 chunks.chunk_content 上建全文索引。
--
-- 检索侧 ChunkServiceImpl.queryTopKSimilarity 由单路向量召回改成了两路召回：
-- 向量召回负责语义相近但字面不同的片段，关键字召回负责专有名词/编号/报错码这类
-- embedding 容易糊掉的精确词面，两路结果在 service 层用 RRF 融合去重后再取 topK。
-- 关键字这一路是 MATCH(chunk_content) AGAINST (... IN NATURAL LANGUAGE MODE)，
-- 没有这个索引会直接报 ERROR 1191 Can't find FULLTEXT index matching the column list
-- （service 层对这个异常做了降级，会退回纯向量召回，但检索质量就回到改造前了）。
--
-- WITH PARSER ngram：正文以中文为主，MySQL 默认全文分词器按空格和标点切词，
-- 一整句中文会被切成一个词，索引等于不可用。ngram 按固定字数切，
-- 字数由服务端变量 ngram_token_size 决定（默认 2），
-- 也就是说少于 2 个字的查询词召不回任何东西 —— 这时两路自然退化成纯向量召回。
--
-- 只索引 chunk_content，不把 file_name 一起放进来：文件名一旦进索引，
-- 查到一个文件名就会把该文件的每个片段都拉进候选集，把另一路的名额挤没。
--
-- 建索引要全表扫一遍正文，chunks 大了以后这条 DDL 不是秒级的，注意执行窗口。
ALTER TABLE `chunks`
    ADD FULLTEXT KEY `ft_chunks_content` (`chunk_content`) WITH PARSER ngram;
