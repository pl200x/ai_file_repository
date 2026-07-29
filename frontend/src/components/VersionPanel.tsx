import { formatDateTime, userName } from "../format";
import type { FileVersion } from "../types";

interface VersionPanelProps {
  versions: FileVersion[];
  loading: boolean;
  error: string | null;
}

export function VersionPanel({
  versions,
  loading,
  error,
}: VersionPanelProps) {
  return (
    <aside className="versions-panel" aria-label="版本历史">
      <div className="versions-header">版本历史</div>
      <ul className="versions-list">
        {versions.map((version) => (
          <li className="version-item" key={version.id}>
            <div className="version-no">v{version.versionNo}</div>
            <div className="version-meta">{version.title || "未命名版本"}</div>
            <div className="version-meta">
              {userName(version.editorId)} ·{" "}
              {formatDateTime(version.openTime)}
            </div>
          </li>
        ))}
        {loading && <li className="versions-empty">正在加载版本…</li>}
        {!loading && error && (
          <li className="versions-empty">版本加载失败：{error}</li>
        )}
        {!loading && !error && versions.length === 0 && (
          <li className="versions-empty">暂无版本</li>
        )}
      </ul>
    </aside>
  );
}
