import type { KnowledgeRepository } from "../types";

interface RepositorySidebarProps {
  repositories: KnowledgeRepository[];
  selectedRepositoryId: number | null;
  trashSelected: boolean;
  loading: boolean;
  onSelect: (repositoryId: number) => void;
  onSelectTrash: () => void;
}

export function RepositorySidebar({
  repositories,
  selectedRepositoryId,
  trashSelected,
  loading,
  onSelect,
  onSelectTrash,
}: RepositorySidebarProps) {
  return (
    <aside className="sidebar" aria-label="知识库导航">
      <div className="sidebar-title">知识库</div>
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
                <span className="repo-badge">个人</span>
              )}
            </button>
          </li>
        ))}
        {!loading && repositories.length === 0 && (
          <li className="doc-empty">暂无知识库</li>
        )}
        {loading && <li className="doc-empty">正在加载知识库…</li>}
      </ul>
      <div className="sidebar-footer">
        <button
          type="button"
          className={`repo-item ${trashSelected ? "active" : ""}`}
          onClick={onSelectTrash}
        >
          <span className="repo-icon" aria-hidden="true">
            🗑️
          </span>
          <span>回收站</span>
        </button>
      </div>
    </aside>
  );
}
