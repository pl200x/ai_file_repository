import type { NotificationItem, PermissionLevel } from "./types";

export const NOTIFICATION_PAGE_SIZE = 20;

//铃铛角标：超过这个数只显示 "99+"，避免窄侧栏被撑开
export const UNREAD_BADGE_LIMIT = 99;

const topicLabels: Record<string, string> = {
  APPLY_PERMISSION: "权限申请",
  LIKE: "点赞",
  COMMENT: "评论",
};

const permissionLevelLabels: Record<string, string> = {
  READABLE: "可阅读",
  WRITABLE: "可编辑",
  MANAGEABLE: "可管理",
};

export function notificationTopicLabel(topic: string) {
  return topicLabels[topic] ?? topic;
}

export function unreadBadgeText(unreadCount: number) {
  if (unreadCount <= 0) return "";
  return unreadCount > UNREAD_BADGE_LIMIT
    ? `${UNREAD_BADGE_LIMIT}+`
    : String(unreadCount);
}

export function notificationTargetLabel(
  notification: NotificationItem,
) {
  const typeLabel =
    notification.targetType === "KNOWLEDGE_REPOSITORY"
      ? "知识库"
      : "文档";
  //目标被删除时后端补不到标题，用 id 兜底而不是显示空白
  const title =
    notification.targetTitle?.trim() ||
    `#${notification.targetId}`;
  return `${typeLabel}《${title}》`;
}

//APPLY_PERMISSION 的 operationContent 存的是申请的权限级别，
//其它 topic 直接把内容当文案用
export function notificationSummary(notification: NotificationItem) {
  const applicant =
    notification.applicantName?.trim() ||
    `用户 ID ${notification.applicantId}`;
  const target = notificationTargetLabel(notification);

  if (notification.topic === "APPLY_PERMISSION") {
    const level = notification.operationContent?.trim() ?? "";
    const levelLabel = permissionLevelLabels[level] ?? level;
    return levelLabel
      ? `${applicant} 申请 ${target}的${levelLabel}权限`
      : `${applicant} 申请 ${target}的权限`;
  }

  const content = notification.operationContent?.trim();
  return content
    ? `${applicant} · ${target} · ${content}`
    : `${applicant} · ${target}`;
}

export function permissionLevelFromContent(
  content: string | null | undefined,
): PermissionLevel | null {
  const value = content?.trim() ?? "";
  return value === "READABLE" ||
    value === "WRITABLE" ||
    value === "MANAGEABLE"
    ? value
    : null;
}

export function totalPageCount(total: number, pageSize: number) {
  if (pageSize <= 0) return 1;
  return Math.max(1, Math.ceil(total / pageSize));
}
