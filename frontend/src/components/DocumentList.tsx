import { useRef } from "react";
import { formatDateTime, userName } from "../format";
import { useTranslation } from "../i18n";
import type { FileDocument, UserSummary } from "../types";

interface DocumentListProps {
  repositoryTitle: string;
  documents: FileDocument[];
  users: UserSummary[];
  selectedFileId: number | null;
  loading: boolean;
  canCreate: boolean;
  creating: boolean;
  uploadingPdf: boolean;
  uploadingMarkdown: boolean;
  onCreate: () => void;
  onUploadPdf: (file: File) => void;
  onUploadMarkdown: (file: File) => void;
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
  uploadingPdf,
  uploadingMarkdown,
  onCreate,
  onUploadPdf,
  onUploadMarkdown,
  onManagePermissions,
  onSelect,
}: DocumentListProps) {
  const pdfInputRef = useRef<HTMLInputElement | null>(null);
  const markdownInputRef = useRef<HTMLInputElement | null>(null);
  const { t } = useTranslation();

  return (
    <section className="doc-panel" aria-label={t("文档列表")}>
      <div className="doc-panel-header">
        <span className="doc-panel-title">{repositoryTitle || t("文档")}</span>
        <div className="doc-panel-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={onManagePermissions}
            disabled={!canCreate}
            title={t("管理知识库权限")}
          >
            {t("权限")}
          </button>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => pdfInputRef.current?.click()}
            disabled={!canCreate || uploadingPdf}
          >
            {uploadingPdf ? t("上传中…") : t("上传 PDF")}
          </button>
          <input
            ref={pdfInputRef}
            className="visually-hidden"
            type="file"
            accept=".pdf,application/pdf"
            aria-label={t("选择要上传的 PDF")}
            disabled={!canCreate || uploadingPdf}
            onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              event.currentTarget.value = "";
              if (file) onUploadPdf(file);
            }}
          />
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => markdownInputRef.current?.click()}
            disabled={!canCreate || uploadingMarkdown}
          >
            {uploadingMarkdown ? t("上传中…") : t("上传 Markdown")}
          </button>
          <input
            ref={markdownInputRef}
            className="visually-hidden"
            type="file"
            accept=".md,.markdown,text/markdown"
            aria-label={t("选择要上传的 Markdown 文件")}
            disabled={!canCreate || uploadingMarkdown}
            onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              event.currentTarget.value = "";
              if (file) onUploadMarkdown(file);
            }}
          />
          <button
            type="button"
            className="btn btn-primary btn-sm"
            onClick={onCreate}
            disabled={!canCreate || creating}
          >
            {creating ? t("创建中…") : t("＋ 添加")}
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
                {userName(document.ownerId, users, (id) => t("用户 ID {id}", { id }))} · {t("更新于")}{" "}
                {formatDateTime(document.recentUpdateTime)}
              </span>
            </button>
          </li>
        ))}
        {!loading && documents.length === 0 && (
          <li className="doc-empty">
            {t("还没有文档，点击「添加文档」新建")}
          </li>
        )}
        {loading && <li className="doc-empty">{t("正在加载文档…")}</li>}
      </ul>
    </section>
  );
}
