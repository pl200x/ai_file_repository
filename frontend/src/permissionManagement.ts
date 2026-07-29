import type {
  PermissionLevel,
  PermissionStatus,
} from "./types";

export const PERMISSION_LEVEL_OPTIONS: ReadonlyArray<{
  value: PermissionLevel;
  label: string;
  description: string;
}> = [
  {
    value: "READABLE",
    label: "可阅读",
    description: "可以查看内容",
  },
  {
    value: "WRITABLE",
    label: "可编辑",
    description: "可以查看和修改内容",
  },
  {
    value: "MANAGEABLE",
    label: "可管理",
    description: "可以管理内容与成员权限",
  },
];

export const EXPIRATION_OPTIONS: ReadonlyArray<{
  value: number;
  label: string;
}> = [
  { value: 86_400_000, label: "1 天" },
  { value: 259_200_000, label: "3 天" },
  { value: 2_592_000_000, label: "1 个月" },
  { value: 31_536_000_000, label: "1 年" },
];

const permissionLevelLabels: Record<
  PermissionLevel | "NONE",
  string
> = {
  READABLE: "可阅读",
  WRITABLE: "可编辑",
  MANAGEABLE: "可管理",
  NONE: "无权限",
};

const permissionStatusLabels: Record<PermissionStatus, string> = {
  PENDING: "待审批",
  APPROVED: "已生效",
  REJECTED: "已拒绝",
  REVOKED: "已撤销",
};

export function permissionLevelLabel(
  permissionLevel: PermissionLevel | "NONE",
) {
  return permissionLevelLabels[permissionLevel] ?? permissionLevel;
}

export function permissionStatusLabel(status: PermissionStatus) {
  return permissionStatusLabels[status] ?? status;
}
