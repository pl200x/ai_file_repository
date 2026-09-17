import { unreadBadgeText } from "../notifications";
import { useTranslation } from "../i18n";
import type { KnowledgeRepository } from "../types";

interface RepositorySidebarProps {
  repositories: KnowledgeRepository[];
  selectedRepositoryId: number | null;
  trashSelected: boolean;
  notificationsSelected: boolean;
  searchSelected: boolean;
  assistantSelected: boolean;
  unreadNotificationCount: number;
  loading: boolean;
  onSelect: (repositoryId: number) => void;
  onSelectTrash: () => void;
  onSelectNotifications: () => void;
  onSelectSearch: () => void;
  onSelectAssistant: () => void;
}

export function RepositorySidebar({
  repositories,
  selectedRepositoryId,
  trashSelected,
  notificationsSelected,
  searchSelected,
  assistantSelected,
  unreadNotificationCount,
  loading,
  onSelect,
  onSelectTrash,
  onSelectNotifications,
  onSelectSearch,
  onSelectAssistant,
}: RepositorySidebarProps) {
  const { t } = useTranslation();
  const badge = unreadBadgeText(unreadNotificationCount);
  return (
    <aside className="sidebar" aria-label={t("知识库导航")}>
      <div className="sidebar-title">{t("知识库")}</div>
      <ul className="repo-list">
        {repositories.map((repository) => (
          <li key={repository.id}>
            <button
              type="button"
              className={`repo-item ${
                repository.id === selectedRepositoryId ? "active" : ""
              }`}
              onClick={() => onSelect(repository.id)}
            >
              <span className="repo-icon" aria-hidden="true">
                📚
              </span>
              <span className="truncate">{repository.title}</span>
              {repository.personal && (
                <span className="repo-badge">{t("个人")}</span>
              )}
            </button>
          </li>
        ))}
        {!loading && repositories.length === 0 && (
          <li className="doc-empty">{t("暂无知识库")}</li>
        )}
        {loading && <li className="doc-empty">{t("正在加载知识库…")}</li>}
      </ul>
      <div className="sidebar-footer">
        <button
          type="button"
          className={`repo-item ${assistantSelected ? "active" : ""}`}
          onClick={onSelectAssistant}
        >
          <span className="repo-icon" aria-hidden="true">
            💬
          </span>
          <span>{t("智能客服")}</span>
        </button>
        <button
          type="button"
          className={`repo-item ${searchSelected ? "active" : ""}`}
          onClick={onSelectSearch}
        >
          <span className="repo-icon" aria-hidden="true">
            🔍
          </span>
          <span>{t("语义搜索")}</span>
        </button>
        <button
          type="button"
          className={`repo-item ${notificationsSelected ? "active" : ""}`}
          onClick={onSelectNotifications}
        >
          <span className="repo-icon" aria-hidden="true">
            🔔
          </span>
          <span>{t("消息通知")}</span>
          {badge && (
            <span
              className="unread-badge"
              aria-label={t("{count} 条未读通知", { count: unreadNotificationCount })}
            >
              {badge}
            </span>
          )}
        </button>
        <button
          type="button"
          className={`repo-item ${trashSelected ? "active" : ""}`}
          onClick={onSelectTrash}
        >
          <span className="repo-icon" aria-hidden="true">
            🗑️
          </span>
          <span>{t("回收站")}</span>
        </button>
      </div>
    </aside>
  );
}
