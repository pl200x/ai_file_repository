import type { UserSummary } from "../types";
import { useTranslation } from "../i18n";

interface TopBarProps {
  userId: number;
  users: UserSummary[];
  loading: boolean;
  onUserChange: (userId: number) => void;
}

export function TopBar({
  userId,
  users,
  loading,
  onUserChange,
}: TopBarProps) {
  const { language, setLanguage, t } = useTranslation();
  return (
    <header className="topbar">
      <div className="brand">
        <span className="brand-logo" aria-hidden="true">
          {t("语")}
        </span>
        <span className="brand-name">{t("AI 知识库")}</span>
      </div>
      <div className="topbar-right">
        <label className="identity-label" htmlFor="language-select">
          {t("语言")}
        </label>
        <select
          id="language-select"
          className="user-select"
          value={language}
          onChange={(event) => setLanguage(event.target.value as "zh-CN" | "en")}
        >
          <option value="zh-CN">{t("简体中文")}</option>
          <option value="en">English</option>
        </select>
        <label className="identity-label" htmlFor="user-select">
          {t("当前用户")}
        </label>
        <select
          id="user-select"
          className="user-select"
          value={userId}
          onChange={(event) => onUserChange(Number(event.target.value))}
          disabled={loading || users.length === 0}
        >
          {users.length === 0 ? (
            <option value={userId}>
              {loading ? t("正在加载用户…") : t("用户 ID {id}", { id: userId })}
            </option>
          ) : (
            users.map((user) => (
              <option key={user.id} value={user.id}>
                {user.name}
              </option>
            ))
          )}
        </select>
      </div>
    </header>
  );
}
