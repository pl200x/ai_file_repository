import { formatDateTime, userName } from "../format";
import { useTranslation } from "../i18n";
import type { FileDocument, UserSummary } from "../types";

interface TrashBinPageProps {
  documents: FileDocument[];
  users: UserSummary[];
  loading: boolean;
  deletingFileId: number | null;
  restoringFileId: number | null;
  onDelete: (fileId: number) => void;
  onRestore: (fileId: number) => void;
}

export function TrashBinPage({
  documents,
  users,
  loading,
  deletingFileId,
  restoringFileId,
  onDelete,
  onRestore,
}: TrashBinPageProps) {
  const { t } = useTranslation();
  const actionInProgress =
    deletingFileId !== null || restoringFileId !== null;

  return (
    <main className="trash-panel">
      <div className="trash-header">
        <div>
          <h1>{t("回收站")}</h1>
          <p>{t("这里展示已被软删除的文档。永久删除后将无法恢复。")}</p>
        </div>
        <span className="trash-count">
          {loading ? t("加载中…") : t("{count} 个文档", { count: documents.length })}
        </span>
      </div>

      <div className="trash-content">
        {!loading && documents.length === 0 && (
          <div className="trash-empty">
            <span aria-hidden="true">🗑️</span>
            <p>{t("回收站是空的")}</p>
          </div>
        )}
        {loading && <div className="trash-loading">{t("正在加载回收站…")}</div>}
        {!loading && documents.length > 0 && (
          <ul className="trash-list">
            {documents.map((document) => (
              <li className="trash-item" key={document.id}>
                <div className="trash-file-icon" aria-hidden="true">
                  📄
                </div>
                <div className="trash-file-info">
                  <span className="trash-file-title">
                    {document.title}
                  </span>
                  <span className="trash-file-meta">
                    {userName(document.ownerId, users, (id) => t("用户 ID {id}", { id }))} · {t("移入于")}{" "}
                    {formatDateTime(document.recentUpdateTime)}
                  </span>
                </div>
                <div className="trash-file-actions">
                  <button
                    type="button"
                    className="btn btn-success-ghost btn-sm"
                    disabled={actionInProgress}
                    onClick={() => onRestore(document.id)}
                  >
                    {restoringFileId === document.id
                      ? t("恢复中…")
                      : t("恢复")}
                  </button>
                  <button
                    type="button"
                    className="btn btn-danger-ghost btn-sm"
                    disabled={actionInProgress}
                    onClick={() => onDelete(document.id)}
                  >
                    {deletingFileId === document.id
                      ? t("永久删除中…")
                      : t("永久删除")}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  );
}
