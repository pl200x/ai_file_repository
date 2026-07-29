#!/usr/bin/env bash
# 精确停止 frontend(:5173)、file_management(:8087) 和 permission(:8086)。
# 优先使用 start-all 写入的 PID；仅在确认进程属于本项目时才按端口兜底。
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
RUN_DIR="$SCRIPT_DIR/run"
FRONTEND_DIR="$ROOT_DIR/frontend"
FILE_MANAGEMENT_DIR="$ROOT_DIR/file_management"
PERMISSION_DIR="$ROOT_DIR/permission"

NAMES=(frontend file_management permission)
PORTS=(5173 8087 8086)
PIDS=("" "" "")
EXIT_STATUS=0

mkdir -p "$RUN_DIR"

process_belongs_to_project() {
    local name=$1
    local pid=$2
    local command_line
    local process_cwd

    command_line="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    process_cwd="$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null |
        sed -n 's/^n//p' | head -n 1)"

    case "$name" in
        frontend)
            [[ "$command_line" == *"$FRONTEND_DIR"* &&
               "$command_line" == *"vite"* ]]
            ;;
        file_management)
            [[ "$command_line" == *"$FILE_MANAGEMENT_DIR/target/file_management-0.0.1-SNAPSHOT.jar"* ||
               ("$process_cwd" == "$FILE_MANAGEMENT_DIR" &&
                "$command_line" == *"FileManagementApplication"*) ]]
            ;;
        permission)
            [[ "$command_line" == *"$PERMISSION_DIR/target/Permission-0.0.1-SNAPSHOT.jar"* ||
               ("$process_cwd" == "$PERMISSION_DIR" &&
                "$command_line" == *"PermissionApplication"*) ]]
            ;;
        *)
            return 1
            ;;
    esac
}

for i in "${!NAMES[@]}"; do
    name=${NAMES[$i]}
    pid_file="$RUN_DIR/$name.pid"
    if [ ! -f "$pid_file" ]; then
        continue
    fi

    read -r pid < "$pid_file" || pid=""
    if [[ ! "$pid" =~ ^[0-9]+$ ]] || ! kill -0 "$pid" 2>/dev/null; then
        continue
    fi

    if process_belongs_to_project "$name" "$pid"; then
        echo "    stopping $name (pid: $pid)"
        kill "$pid" 2>/dev/null || true
        PIDS[$i]=$pid
    else
        echo "    WARNING: refusing to stop pid $pid from $pid_file because it is not recognized as this project" >&2
        EXIT_STATUS=1
    fi
done

for _ in $(seq 1 10); do
    still_running=""
    for pid in "${PIDS[@]}"; do
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            still_running=1
        fi
    done
    [ -z "$still_running" ] && break
    sleep 1
done

for i in "${!NAMES[@]}"; do
    name=${NAMES[$i]}
    pid=${PIDS[$i]}
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null &&
       process_belongs_to_project "$name" "$pid"; then
        echo "    force stopping $name (pid: $pid)"
        kill -9 "$pid" 2>/dev/null || true
    fi
done

# 兼容 PID 文件缺失的旧实例或手动启动实例，但绝不终止无法确认归属的进程。
for i in "${!NAMES[@]}"; do
    name=${NAMES[$i]}
    port=${PORTS[$i]}
    listener_pids="$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"

    if [ -z "$listener_pids" ]; then
        echo "    $name (port $port) is not running"
        continue
    fi

    for pid in $listener_pids; do
        if process_belongs_to_project "$name" "$pid"; then
            echo "    stopping $name listener on port $port (pid: $pid)"
            kill "$pid" 2>/dev/null || true
        else
            echo "    WARNING: port $port is used by unrecognized pid $pid; it was not stopped" >&2
            EXIT_STATUS=1
        fi
    done
done

sleep 1

for i in "${!NAMES[@]}"; do
    name=${NAMES[$i]}
    port=${PORTS[$i]}
    listener_pids="$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    for pid in $listener_pids; do
        if process_belongs_to_project "$name" "$pid"; then
            echo "    force stopping $name listener on port $port (pid: $pid)"
            kill -9 "$pid" 2>/dev/null || true
        else
            EXIT_STATUS=1
        fi
    done
done

for name in "${NAMES[@]}"; do
    rm -f "$RUN_DIR/$name.pid"
done

if [ "$EXIT_STATUS" -eq 0 ]; then
    echo "    frontend and backend services are stopped"
fi
exit "$EXIT_STATUS"
