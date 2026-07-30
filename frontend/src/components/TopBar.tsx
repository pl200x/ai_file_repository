import type { UserSummary } from "../types";

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
  return (
    <header className="topbar">
      <div className="brand">
        <span className="brand-logo" aria-hidden="true">
          语
        </span>
        <span className="brand-name">AI 知识库</span>
      </div>
      <div className="topbar-right">
        <label className="identity-label" htmlFor="user-select">
          当前用户
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
              {loading ? "正在加载用户…" : `用户 ID ${userId}`}
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
