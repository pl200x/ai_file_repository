import { useState } from "react";
import type { FileDocument, UserSummary } from "../types";
import { useEditorSession } from "../hooks/useEditorSession";
import { DocumentContentEditor } from "./DocumentContentEditor";
import { FileAccessRequestPanel } from "./FileAccessRequestPanel";
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
  const session = useEditorSession(props);
  const [deletingPermanently, setDeletingPermanently] =
    useState(false);
  const [movingToTrash, setMovingToTrash] = useState(false);

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
      !window.confirm("永久删除后无法恢复，确定继续吗？")
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
      !window.confirm("确定将文档移入回收站吗？")
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
              {props.mode === "new" ? "新建文档" : "编辑文档"}
            </span>
            <span
              className={`dirty-dot ${session.dirty ? "dirty" : ""}`}
              title={session.dirty ? "存在未保存更改" : "内容已保存"}
            />
            <span className="save-status">{session.saveStatus}</span>
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
                  权限管理
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
                    ? "永久删除中…"
                    : "永久删除"}
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
                  {movingToTrash ? "正在移入…" : "移入回收站"}
                </button>
              </>
            )}
            <button
              type="button"
              className={`btn btn-ghost btn-sm ${
                session.versionsVisible ? "active" : ""
              }`}
              onClick={session.toggleVersions}
              aria-expanded={session.versionsVisible}
            >
              版本历史
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
              {session.submitting ? "提交中…" : "提 交"}
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
          placeholder={session.loading ? "正在加载…" : "请输入标题"}
          autoComplete="off"
          aria-label="文档标题"
          disabled={session.loading || session.submitting}
        />
        <div className="editor-body">
          <DocumentContentEditor
            value={session.draft.content}
            onChange={(content) =>
              session.updateDraft("content", content)
            }
            onError={(message) => props.showToast(message)}
            placeholder={
              session.loading ? "正在加载文档内容…" : "开始书写正文…"
            }
            disabled={session.loading || session.submitting}
          />
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
