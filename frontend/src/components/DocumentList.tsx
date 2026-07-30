import { formatDateTime, userName } from "../format";
import type { FileDocument, UserSummary } from "../types";

interface DocumentListProps {
  repositoryTitle: string;
  documents: FileDocument[];
  users: UserSummary[];
  selectedFileId: number | null;
  loading: boolean;
  canCreate: boolean;
  creating: boolean;
  onCreate: () => void;
  onManagePermissions: () => void;
  onSelect: (fileId: number) => void;
}

export function DocumentList({
  repositoryTitle,
  documents,
  users,
  selectedFileId,
  loading,
  canCreate,
  creating,
  onCreate,
  onManagePermissions,
  onSelect,
}: DocumentListProps) {
  return (
    <section className="doc-panel" aria-label="文档列表">
      <div className="doc-panel-header">
        <span className="doc-panel-title">{repositoryTitle || "文档"}</span>
        <div className="doc-panel-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={onManagePermissions}
            disabled={!canCreate}
            title="管理知识库权限"
          >
            权限
          </button>
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={onCreate}
            disabled={!canCreate || creating}
          >
            {creating ? "创建中…" : "＋ 添加"}
          </button>
        </div>
      </div>
      <ul className="doc-list">
        {documents.map((document) => (
          <li key={document.id}>
            <button
              type="button"
              data-file-id={document.id}
              className={`doc-item ${
                document.id === selectedFileId ? "active" : ""
              }`}
              onClick={() => onSelect(document.id)}
            >
              <span className="doc-item-title">{document.title}</span>
              <span className="doc-item-meta">
                {userName(document.ownerId, users)} · 更新于{" "}
                {formatDateTime(document.recentUpdateTime)}
              </span>
            </button>
          </li>
        ))}
        {!loading && documents.length === 0 && (
          <li className="doc-empty">
            还没有文档，点击「添加文档」新建
          </li>
        )}
        {loading && <li className="doc-empty">正在加载文档…</li>}
      </ul>
    </section>
  );
}
