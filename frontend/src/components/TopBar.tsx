import { DEMO_USERS } from "../config";

interface TopBarProps {
  userId: number;
  onUserChange: (userId: number) => void;
}

export function TopBar({ userId, onUserChange }: TopBarProps) {
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
        >
          {DEMO_USERS.map((user) => (
            <option key={user.id} value={user.id}>
              {user.name}
            </option>
          ))}
        </select>
      </div>
    </header>
  );
}
