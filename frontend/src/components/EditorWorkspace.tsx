import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import { useTranslation } from "../i18n";
import type { FileDocument, UserSummary } from "../types";
import { useEditorSession } from "../hooks/useEditorSession";
import { DocumentContentEditor } from "./DocumentContentEditor";
import { FileAccessRequestPanel } from "./FileAccessRequestPanel";
import { MarkdownPreview } from "./MarkdownPreview";
import { VersionPanel } from "./VersionPanel";

interface EditorWorkspaceProps {
  mode: "new" | "edit";
  repositoryId: number;
  fileId: number | null;
  initialDefaultTitle?: string;
  initialVersionNo?: number;
  userId: number;
  users: UserSummary[];
  showToast: (message: string, success?: boolean) => void;
  refreshDocuments: () => Promise<FileDocument[]>;
  onCreated: (fileId: number) => void;
  onLoadFailure: () => void;
  onManagePermissions: (fileId: number) => void;
  onPermanentDelete: (fileId: number) => Promise<void>;
  onMoveToTrash: (fileId: number) => Promise<void>;
}

export function EditorWorkspace(props: EditorWorkspaceProps) {
  const { t } = useTranslation();
  const session = useEditorSession(props);
  const [deletingPermanently, setDeletingPermanently] =
    useState(false);
  const [movingToTrash, setMovingToTrash] = useState(false);
  const [viewMode, setViewMode] = useState<"edit" | "preview">(
    "edit",
  );
  //只在文档首次加载完成时按来源格式选一次默认视图；
  //之后用户手动切换不应该被内容变化再次覆盖
  const appliedDefaultViewRef = useRef(false);
  useEffect(() => {
    if (appliedDefaultViewRef.current || session.loading) return;
    appliedDefaultViewRef.current = true;
    if (session.contentFormat === "MARKDOWN") {
      setViewMode("preview");
    }
  }, [session.loading, session.contentFormat]);

  if (
    props.mode === "edit" &&
    props.fileId !== null &&
    session.accessDenied
  ) {
    return (
      <main className="editor-panel">
        <FileAccessRequestPanel
          fileId={props.fileId}
          userId={props.userId}
          checking={session.loading}
          onRetry={session.retryLoad}
          showToast={props.showToast}
        />
      </main>
    );
  }

  const deletePermanently = async () => {
    if (
      props.fileId === null ||
      deletingPermanently ||
      movingToTrash ||
      !window.confirm(t("永久删除后无法恢复，确定继续吗？"))
    ) {
      return;
    }

    setDeletingPermanently(true);
    try {
      await props.onPermanentDelete(props.fileId);
    } finally {
      setDeletingPermanently(false);
    }
  };

  const moveToTrash = async () => {
    if (
      props.fileId === null ||
      deletingPermanently ||
      movingToTrash ||
      !window.confirm(t("确定将文档移入回收站吗？"))
    ) {
      return;
    }

    setMovingToTrash(true);
    try {
      await props.onMoveToTrash(props.fileId);
    } finally {
      setMovingToTrash(false);
    }
  };

  return (
    <main className="editor-panel">
      <div className="editor-main">
        <div className="editor-header">
          <div className="editor-status-row">
            <span className="mode-badge">
              {props.mode === "new" ? t("新建文档") : t("编辑文档")}
            </span>
            <span
              className={`dirty-dot ${session.dirty ? "dirty" : ""}`}
              title={session.dirty ? t("存在未保存更改") : t("内容已保存")}
            />
            <span className="save-status">{t(session.saveStatus)}</span>
          </div>
          <div className="editor-actions">
            {props.mode === "edit" && (
              <>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={() => {
                    if (props.fileId !== null) {
                      props.onManagePermissions(props.fileId);
                    }
                  }}
                  disabled={
                    session.loading ||
                    !session.ready ||
                    session.submitting ||
                    movingToTrash ||
                    deletingPermanently
                  }
                >
                  {t("权限管理")}
                </button>
                <button
                  type="button"
                  className="btn btn-danger-ghost btn-sm"
                  onClick={() => void deletePermanently()}
                  disabled={
                    session.loading ||
                    !session.ready ||
                    session.submitting ||
                    movingToTrash ||
                    deletingPermanently
                  }
                >
                  {deletingPermanently
                    ? t("永久删除中…")
                    : t("永久删除")}
                </button>
                <button
                  type="button"
                  className="btn btn-danger-ghost btn-sm"
                  onClick={() => void moveToTrash()}
                  disabled={
                    session.loading ||
                    !session.ready ||
                    session.submitting ||
                    movingToTrash ||
                    deletingPermanently
                  }
                >
                  {movingToTrash ? t("正在移入…") : t("移入回收站")}
                </button>
                {props.fileId !== null && (
                  <a
                    className="btn btn-ghost btn-sm"
                    href={api.exportPdfUrl(props.fileId, props.userId)}
                    title={t("下载当前已保存版本的 PDF")}
                  >
                    {t("下载 PDF")}
                  </a>
                )}
              </>
            )}
            <button
              type="button"
              className={`btn btn-ghost btn-sm ${
                viewMode === "preview" ? "active" : ""
              }`}
              onClick={() =>
                setViewMode((mode) =>
                  mode === "preview" ? "edit" : "preview",
                )
              }
              disabled={session.loading}
              title={t("按 Markdown 语法渲染正文格式")}
            >
              {viewMode === "preview" ? t("编辑") : t("预览格式")}
            </button>
            <button
              type="button"
              className={`btn btn-ghost btn-sm ${
                session.versionsVisible ? "active" : ""
              }`}
              onClick={session.toggleVersions}
              aria-expanded={session.versionsVisible}
            >
              {t("版本历史")}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => void session.submit()}
              disabled={
                session.loading ||
                !session.ready ||
                session.submitting
              }
            >
              {session.submitting ? t("提交中…") : t("提 交")}
            </button>
          </div>
        </div>
        <input
          className="title-input"
          type="text"
          value={session.draft.title}
          onChange={(event) =>
            session.updateDraft("title", event.target.value)
          }
          placeholder={session.loading ? t("正在加载…") : t("请输入标题")}
          autoComplete="off"
          aria-label={t("文档标题")}
          disabled={session.loading || session.submitting}
        />
        <div className="editor-body">
          {viewMode === "preview" ? (
            <MarkdownPreview content={session.draft.content} />
          ) : (
            <DocumentContentEditor
              value={session.draft.content}
              onChange={(content) =>
                session.updateDraft("content", content)
              }
              onError={(message) => props.showToast(message)}
              placeholder={
                session.loading ? t("正在加载文档内容…") : t("开始书写正文…")
              }
              disabled={session.loading || session.submitting}
            />
          )}
          {session.versionsVisible && (
            <VersionPanel
              versions={session.versions}
              users={props.users}
              loading={session.versionsLoading}
              error={session.versionsError}
            />
          )}
        </div>
      </div>
    </main>
  );
}
