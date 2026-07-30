import type {
  PermissionLevel,
  PermissionRequest,
} from "./types";

export function isFileAccessDenied(error: unknown) {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    error.code === 501
  );
}

export function buildFilePermissionRequest(
  userId: number,
  fileId: number,
  permissionType: PermissionLevel,
  expirationDate: number,
): PermissionRequest {
  return {
    requestUserId: userId,
    targetUserId: userId,
    targetType: "FILE",
    targetId: fileId,
    permissionType,
    expirationDate,
  };
}
