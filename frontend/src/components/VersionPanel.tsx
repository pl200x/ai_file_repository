import { formatDateTime, userName } from "../format";
import { useTranslation } from "../i18n";
import type { FileVersion, UserSummary } from "../types";

interface VersionPanelProps {
  versions: FileVersion[];
  users: UserSummary[];
  loading: boolean;
  error: string | null;
}

export function VersionPanel({
  versions,
  users,
  loading,
  error,
}: VersionPanelProps) {
  const { t } = useTranslation();
  return (
    <aside className="versions-panel" aria-label={t("版本历史")}>
      <div className="versions-header">{t("版本历史")}</div>
      <ul className="versions-list">
        {versions.map((version) => (
          <li className="version-item" key={version.id}>
            <div className="version-no">v{version.versionNo}</div>
            <div className="version-meta">{version.title || t("未命名版本")}</div>
            <div className="version-meta">
              {userName(version.editorId, users, (id) => t("用户 ID {id}", { id }))} ·{" "}
              {formatDateTime(version.openTime)}
            </div>
          </li>
        ))}
        {loading && <li className="versions-empty">{t("正在加载版本…")}</li>}
        {!loading && error && (
          <li className="versions-empty">{t("版本加载失败：{detail}", { detail: error })}</li>
        )}
        {!loading && !error && versions.length === 0 && (
          <li className="versions-empty">{t("暂无版本")}</li>
        )}
      </ul>
    </aside>
  );
}
