import { useState } from "react";
import { useTranslation } from "../i18n";
import {
  chunkPositionLabel,
  documentTitleOf,
  groupHitsByFile,
  isTruncated,
  normalizeQuery,
  searchSummary,
  snippet,
  TOP_K_OPTIONS,
} from "../chunkSearch";
import type { ChunkHit } from "../types";

interface SearchPageProps {
  query: string;
  hits: ChunkHit[];
  topK: number;
  loading: boolean;
  searched: boolean;
  onSearch: (query: string, topK: number) => void;
  onOpenDocument: (repositoryId: number, fileId: number) => void;
}

export function SearchPage({
  query,
  hits,
  topK,
  loading,
  searched,
  onSearch,
  onOpenDocument,
}: SearchPageProps) {
  const { t } = useTranslation();
  const [input, setInput] = useState(query);
  const [selectedTopK, setSelectedTopK] = useState(topK);
  //展开的是片段而不是文档：同一篇里可能有一段要看全文、另一段只需摘要
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const groups = groupHitsByFile(hits);
  const trimmed = normalizeQuery(input);

  const toggleExpanded = (chunkId: string) => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(chunkId)) {
        next.delete(chunkId);
      } else {
        next.add(chunkId);
      }
      return next;
    });
  };

  return (
    <main className="search-panel">
      <div className="search-header">
        <div>
          <h1>{t("语义搜索")}</h1>
          <p>{t("按意思检索文档内容，而不是按关键词匹配。")}</p>
        </div>
        {searched && !loading && (
          <span className="search-count">{t(searchSummary(hits))}</span>
        )}
      </div>

      <form
        className="search-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (!trimmed || loading) return;
          onSearch(trimmed, selectedTopK);
        }}
      >
        <input
          className="search-input"
          type="search"
          value={input}
          placeholder={t("描述你想找的内容，例如：权限过期之后会怎么样")}
          aria-label={t("搜索内容")}
          onChange={(event) => setInput(event.target.value)}
        />
        <label className="search-topk">
          {t("返回")}
          <select
            value={selectedTopK}
            aria-label={t("返回条数")}
            onChange={(event) =>
              setSelectedTopK(Number(event.target.value))
            }
          >
            {TOP_K_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {t("{count} 条", { count: option })}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          className="btn btn-primary"
          disabled={!trimmed || loading}
        >
          {loading ? t("搜索中…") : t("搜索")}
        </button>
      </form>

      <div className="search-content">
        {loading && <div className="search-loading">{t("正在检索…")}</div>}

        {!loading && !searched && (
          <div className="search-empty">
            <span aria-hidden="true">🔍</span>
            <p>{t("输入一句话，找到相关的文档片段")}</p>
          </div>
        )}

        {!loading && searched && groups.length === 0 && (
          <div className="search-empty">
            <span aria-hidden="true">🗂️</span>
            <p>{t("没有找到相关内容，换个说法试试")}</p>
          </div>
        )}

        {!loading && groups.length > 0 && (
          <ul className="search-group-list">
            {groups.map((group) => (
              <li className="search-group" key={group.fileId}>
                <div className="search-group-header">
                  {group.visible ? (
                    <button
                      type="button"
                      className="search-doc-title"
                      onClick={() =>
                        onOpenDocument(group.repositoryId, group.fileId)
                      }
                    >
                      {documentTitleOf(group, (id) => t("文档 #{id}", { id }))}
                    </button>
                  ) : (
                    <span className="search-doc-title locked">
                      <span aria-hidden="true">🔒</span>
                      {documentTitleOf(group, (id) => t("文档 #{id}", { id }))}
                    </span>
                  )}
                  <span className="search-group-meta">
                    {t("{count} 个片段", { count: group.hits.length })}
                  </span>
                </div>

                <ul className="search-hit-list">
                  {group.hits.map((each) => (
                    <li className="search-hit" key={each.chunkId}>
                      <span className="search-hit-position">
                        {t(chunkPositionLabel(each))}
                      </span>
                      {each.visible ? (
                        <div className="search-hit-body">
                          <p className="search-hit-text">
                            {expanded.has(each.chunkId)
                              ? each.chunkContent
                              : snippet(each.chunkContent)}
                          </p>
                          {isTruncated(each.chunkContent) && (
                            <button
                              type="button"
                              className="btn btn-ghost btn-sm"
                              onClick={() => toggleExpanded(each.chunkId)}
                            >
                              {expanded.has(each.chunkId)
                                ? t("收起")
                                : t("展开全文")}
                            </button>
                          )}
                        </div>
                      ) : (
                        <p className="search-hit-locked">
                          {t("你没有这篇文档的访问权限，内容不会显示")}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
