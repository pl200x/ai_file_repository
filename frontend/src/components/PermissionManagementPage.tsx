import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";
import type { FormEvent } from "react";
import { api } from "../api";
import { errorMessage, formatDateTime } from "../format";
import {
  EXPIRATION_OPTIONS,
  PERMISSION_LEVEL_OPTIONS,
  permissionLevelLabel,
  permissionStatusLabel,
} from "../permissionManagement";
import type {
  PermissionLevel,
  PermissionTargetType,
  UserSummary,
  UserPermission,
} from "../types";

interface PermissionManagementPageProps {
  targetType: PermissionTargetType;
  targetId: number;
  targetTitle: string;
  userId: number;
  users: UserSummary[];
  onBack: () => void;
  showToast: (message: string, success?: boolean) => void;
}

type PermissionAction = "approve" | "reject" | "revoke";

const DEFAULT_EXPIRATION =
  EXPIRATION_OPTIONS[0]?.value ?? 86_400_000;

export function PermissionManagementPage({
  targetType,
  targetId,
  targetTitle,
  userId,
  users,
  onBack,
  showToast,
}: PermissionManagementPageProps) {
  const defaultInviteeId =
    users.find((user) => user.id !== userId)?.id ?? userId;
  const [permissions, setPermissions] = useState<UserPermission[]>([]);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [inviteeId, setInviteeId] = useState(defaultInviteeId);
  const [inviteLevel, setInviteLevel] =
    useState<PermissionLevel>("READABLE");
  const [inviteExpiration, setInviteExpiration] =
    useState(DEFAULT_EXPIRATION);
  const [needConfirmation, setNeedConfirmation] = useState(true);
  const [requestLevel, setRequestLevel] =
    useState<PermissionLevel>("READABLE");
  const [requestExpiration, setRequestExpiration] =
    useState(DEFAULT_EXPIRATION);
  const [inviting, setInviting] = useState(false);
  const [requesting, setRequesting] = useState(false);
  const [activeAction, setActiveAction] = useState<string | null>(
    null,
  );

  useEffect(() => {
    if (
      inviteeId === userId ||
      !users.some((user) => user.id === inviteeId)
    ) {
      const alternative =
        users.find((user) => user.id !== userId)?.id ?? userId;
      setInviteeId(alternative);
    }
  }, [inviteeId, userId, users]);

  const loadPermissions = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setListError(null);
      try {
        const response = await api.listTargetPermissions(
          userId,
          targetType,
          targetId,
          signal,
        );
        if (!signal?.aborted) {
          setPermissions(response.data ?? []);
        }
      } catch (error) {
        if (!signal?.aborted) {
          setPermissions([]);
          setListError(errorMessage(error));
        }
      } finally {
        if (!signal?.aborted) {
          setLoading(false);
        }
      }
    },
    [targetId, targetType, userId],
  );

  useEffect(() => {
    const controller = new AbortController();
    void loadPermissions(controller.signal);
    return () => controller.abort();
  }, [loadPermissions]);

  const sortedPermissions = useMemo(
    () =>
      [...permissions].sort((left, right) => {
        const statusOrder = {
          PENDING: 0,
          APPROVED: 1,
          REJECTED: 2,
          REVOKED: 3,
        };
        return (
          statusOrder[left.status] - statusOrder[right.status] ||
          left.name.localeCompare(right.name, "zh-CN")
        );
      }),
    [permissions],
  );

  const submitInvitation = async (event: FormEvent) => {
    event.preventDefault();
    if (inviting) return;
    setInviting(true);
    try {
      await api.inviteUser({
        requestUserId: userId,
        targetUserId: inviteeId,
        targetType,
        targetId,
        permissionType: inviteLevel,
        needConfirmation,
        expirationDate: inviteExpiration,
      });
      showToast(
        needConfirmation ? "邀请已提交，等待审批" : "权限已授予",
        true,
      );
      await loadPermissions();
    } catch (error) {
      showToast(`邀请失败：${errorMessage(error)}`);
    } finally {
      setInviting(false);
    }
  };

  const submitRequest = async (event: FormEvent) => {
    event.preventDefault();
    if (requesting) return;
    setRequesting(true);
    try {
      await api.requestPermission({
        requestUserId: userId,
        targetUserId: userId,
        targetType,
        targetId,
        permissionType: requestLevel,
        expirationDate: requestExpiration,
      });
      showToast("权限申请已提交", true);
      await loadPermissions();
    } catch (error) {
      showToast(`申请失败：${errorMessage(error)}`);
    } finally {
      setRequesting(false);
    }
  };

  const operatePermission = async (
    action: PermissionAction,
    permission: UserPermission,
  ) => {
    const actionKey = `${action}:${permission.userId}`;
    if (activeAction !== null) return;
    if (
      action === "revoke" &&
      !window.confirm(`确定撤销 ${permission.name} 的权限吗？`)
    ) {
      return;
    }

    setActiveAction(actionKey);
    const body = {
      requestUserId: userId,
      targetUserId: permission.userId,
      targetType,
      targetId,
    };
    try {
      if (action === "approve") {
        await api.approvePermission(body);
      } else if (action === "reject") {
        await api.rejectPermission(body);
      } else {
        await api.revokePermission(body);
      }
      const successMessage = {
        approve: "权限已批准",
        reject: "权限已拒绝",
        revoke: "权限已撤销",
      };
      showToast(successMessage[action], true);
      await loadPermissions();
    } catch (error) {
      const actionLabel = {
        approve: "批准",
        reject: "拒绝",
        revoke: "撤销",
      };
      showToast(
        `${actionLabel[action]}失败：${errorMessage(error)}`,
      );
    } finally {
      setActiveAction(null);
    }
  };

  const targetLabel =
    targetType === "FILE" ? "文档权限" : "知识库权限";

  return (
    <main className="permission-panel">
      <header className="permission-header">
        <div className="permission-heading">
          <button
            type="button"
            className="permission-back"
            onClick={onBack}
            aria-label="返回"
          >
            ←
          </button>
          <div>
            <span className="permission-eyebrow">{targetLabel}</span>
            <h1>{targetTitle}</h1>
            <p>管理成员访问权限，或为当前用户提交权限申请。</p>
          </div>
        </div>
        <span className="permission-target-badge">
          {targetType === "FILE" ? "文档" : "知识库"} #{targetId}
        </span>
      </header>

      <div className="permission-content">
        <section
          className="permission-members-card"
          aria-labelledby="permission-members-title"
        >
          <div className="permission-section-heading">
            <div>
              <h2 id="permission-members-title">成员权限</h2>
              <p>待审批的申请会优先展示。</p>
            </div>
            <button
              type="button"
              className="btn btn-ghost btn-sm"
              disabled={loading}
              onClick={() => void loadPermissions()}
            >
              {loading ? "刷新中…" : "刷新"}
            </button>
          </div>

          {listError && !loading && (
            <div className="permission-list-error" role="status">
              <span aria-hidden="true">🔒</span>
              <div>
                <strong>暂时无法查看成员权限</strong>
                <p>{listError}</p>
              </div>
            </div>
          )}
          {loading && (
            <div className="permission-list-state">
              正在加载成员权限…
            </div>
          )}
          {!loading && !listError && sortedPermissions.length === 0 && (
            <div className="permission-list-state">
              当前目标还没有权限记录
            </div>
          )}
          {!loading &&
            !listError &&
            sortedPermissions.length > 0 && (
              <ul className="permission-list">
                {sortedPermissions.map((permission) => {
                  const initial =
                    permission.name.trim().charAt(0).toUpperCase() ||
                    String(permission.userId);
                  return (
                    <li
                      className="permission-member"
                      key={permission.permissionId}
                    >
                      <div className="permission-avatar">
                        {permission.profile ? (
                          <img
                            src={permission.profile}
                            alt=""
                            referrerPolicy="no-referrer"
                          />
                        ) : (
                          <span>{initial}</span>
                        )}
                      </div>
                      <div className="permission-user">
                        <div className="permission-user-name">
                          {permission.name}
                          {permission.userId === userId && (
                            <span className="permission-self">你</span>
                          )}
                        </div>
                        <span>
                          {permission.email ||
                            `用户 ID ${permission.userId}`}
                        </span>
                      </div>
                      <div className="permission-grant">
                        <strong>
                          {permissionLevelLabel(
                            permission.permissionType,
                          )}
                        </strong>
                        <span>
                          到期{" "}
                          {formatDateTime(permission.expirationTime) ||
                            "未设置"}
                        </span>
                      </div>
                      <span
                        className={`permission-status status-${permission.status.toLowerCase()}`}
                      >
                        {permissionStatusLabel(permission.status)}
                      </span>
                      <div className="permission-actions">
                        {permission.status !== "APPROVED" && (
                          <button
                            type="button"
                            className="btn btn-success-ghost btn-sm"
                            disabled={activeAction !== null}
                            onClick={() =>
                              void operatePermission(
                                "approve",
                                permission,
                              )
                            }
                          >
                            {activeAction ===
                            `approve:${permission.userId}`
                              ? "批准中…"
                              : "批准"}
                          </button>
                        )}
                        {permission.status === "PENDING" && (
                          <button
                            type="button"
                            className="btn btn-danger-ghost btn-sm"
                            disabled={activeAction !== null}
                            onClick={() =>
                              void operatePermission(
                                "reject",
                                permission,
                              )
                            }
                          >
                            {activeAction ===
                            `reject:${permission.userId}`
                              ? "拒绝中…"
                              : "拒绝"}
                          </button>
                        )}
                        {permission.status === "APPROVED" && (
                          <button
                            type="button"
                            className="btn btn-danger-ghost btn-sm"
                            disabled={activeAction !== null}
                            onClick={() =>
                              void operatePermission(
                                "revoke",
                                permission,
                              )
                            }
                          >
                            {activeAction ===
                            `revoke:${permission.userId}`
                              ? "撤销中…"
                              : "撤销"}
                          </button>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
        </section>

        <aside className="permission-forms">
          <form
            className="permission-form-card"
            onSubmit={(event) => void submitInvitation(event)}
          >
            <div className="permission-form-title">
              <span aria-hidden="true">✉️</span>
              <div>
                <h2>邀请成员</h2>
                <p>需要当前用户具有可管理权限。</p>
              </div>
            </div>

            <label className="permission-field">
              <span>邀请用户</span>
              <select
                value={inviteeId}
                onChange={(event) =>
                  setInviteeId(Number(event.target.value))
                }
              >
                {users.filter((user) => user.id !== userId).map(
                  (user) => (
                    <option key={user.id} value={user.id}>
                      {user.name}（ID {user.id}）
                    </option>
                  ),
                )}
                {!users.some((user) => user.id !== userId) && (
                  <option value={userId}>暂无可邀请用户</option>
                )}
              </select>
            </label>
            <PermissionLevelField
              value={inviteLevel}
              onChange={setInviteLevel}
              id="invite-permission-level"
            />
            <ExpirationField
              value={inviteExpiration}
              onChange={setInviteExpiration}
              id="invite-expiration"
            />
            <label className="permission-checkbox">
              <input
                type="checkbox"
                checked={!needConfirmation}
                onChange={(event) =>
                  setNeedConfirmation(!event.target.checked)
                }
              />
              <span>
                立即生效
                <small>关闭时将创建待审批记录</small>
              </span>
            </label>
            <button
              type="submit"
              className="btn btn-primary permission-submit"
              disabled={
                inviting ||
                inviteeId === userId ||
                !users.some((user) => user.id === inviteeId)
              }
            >
              {inviting ? "发送邀请中…" : "发送邀请"}
            </button>
          </form>

          <form
            className="permission-form-card permission-request-card"
            onSubmit={(event) => void submitRequest(event)}
          >
            <div className="permission-form-title">
              <span aria-hidden="true">🙋</span>
              <div>
                <h2>申请权限</h2>
                <p>为当前用户 user{userId} 提交申请。</p>
              </div>
            </div>
            <PermissionLevelField
              value={requestLevel}
              onChange={setRequestLevel}
              id="request-permission-level"
            />
            <ExpirationField
              value={requestExpiration}
              onChange={setRequestExpiration}
              id="request-expiration"
            />
            <button
              type="submit"
              className="btn btn-ghost permission-submit"
              disabled={requesting}
            >
              {requesting ? "提交申请中…" : "提交申请"}
            </button>
          </form>
        </aside>
      </div>
    </main>
  );
}

interface SelectFieldProps<T> {
  value: T;
  onChange: (value: T) => void;
  id: string;
}

function PermissionLevelField({
  value,
  onChange,
  id,
}: SelectFieldProps<PermissionLevel>) {
  return (
    <label className="permission-field" htmlFor={id}>
      <span>权限级别</span>
      <select
        id={id}
        value={value}
        onChange={(event) =>
          onChange(event.target.value as PermissionLevel)
        }
      >
        {PERMISSION_LEVEL_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label} · {option.description}
          </option>
        ))}
      </select>
    </label>
  );
}

function ExpirationField({
  value,
  onChange,
  id,
}: SelectFieldProps<number>) {
  return (
    <label className="permission-field" htmlFor={id}>
      <span>有效期</span>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
      >
        {EXPIRATION_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
