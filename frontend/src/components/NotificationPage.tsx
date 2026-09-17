import {
  notificationSummary,
  notificationTopicLabel,
  totalPageCount,
} from "../notifications";
import { formatDateTime } from "../format";
import { useTranslation } from "../i18n";
import type { NotificationItem, NotificationPage as Page } from "../types";

interface NotificationPageProps {
  page: Page | null;
  loading: boolean;
  markingId: number | null;
  onMarkRead: (notification: NotificationItem) => void;
  onOpenTarget: (notification: NotificationItem) => void;
  onChangePage: (page: number) => void;
}

export function NotificationPage({
  page,
  loading,
  markingId,
  onMarkRead,
  onOpenTarget,
  onChangePage,
}: NotificationPageProps) {
  const { t } = useTranslation();
  const notifications = page?.notifications ?? [];
  const currentPage = page?.page ?? 1;
  const pageCount = totalPageCount(
    page?.total ?? 0,
    page?.pageSize ?? 20,
  );

  return (
    <main className="notification-panel">
      <div className="notification-header">
        <div>
          <h1>{t("消息通知")}</h1>
          <p>{t("别人对你管理的文档和知识库发起的操作会出现在这里。")}</p>
        </div>
        <span className="notification-count">
          {loading
            ? t("加载中…")
            : t("{unread} 条未读 / 共 {total} 条", { unread: page?.unreadCount ?? 0, total: page?.total ?? 0 })}
        </span>
      </div>

      <div className="notification-content">
        {loading && (
          <div className="notification-loading">{t("正在加载通知…")}</div>
        )}
        {!loading && notifications.length === 0 && (
          <div className="notification-empty">
            <span aria-hidden="true">🔔</span>
            <p>{t("暂时没有通知")}</p>
          </div>
        )}
        {!loading && notifications.length > 0 && (
          <ul className="notification-list">
            {notifications.map((notification) => (
              <li
                className={`notification-item ${
                  notification.read ? "" : "unread"
                }`}
                key={notification.id}
              >
                <span
                  className="notification-dot"
                  aria-hidden="true"
                />
                <div className="notification-info">
                  <span className="notification-topic">
                    {t(notificationTopicLabel(notification.topic))}
                  </span>
                  <button
                    type="button"
                    className="notification-summary"
                    onClick={() => onOpenTarget(notification)}
                  >
                    {t(notificationSummary(notification))}
                  </button>
                  <span className="notification-meta">
                    {formatDateTime(notification.operationTime)}
                  </span>
                </div>
                <div className="notification-actions">
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    disabled={markingId !== null}
                    onClick={() => onMarkRead(notification)}
                  >
                    {markingId === notification.id
                      ? t("处理中…")
                      : notification.read
                        ? t("标为未读")
                        : t("标为已读")}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {pageCount > 1 && (
        <div className="notification-pager">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={loading || currentPage <= 1}
            onClick={() => onChangePage(currentPage - 1)}
          >
            {t("上一页")}
          </button>
          <span className="notification-pager-label">
            {t("第 {page} / {total} 页", { page: currentPage, total: pageCount })}
          </span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={loading || currentPage >= pageCount}
            onClick={() => onChangePage(currentPage + 1)}
          >
            {t("下一页")}
          </button>
        </div>
      )}
    </main>
  );
}
