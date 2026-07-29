#!/usr/bin/env bash
# 构建并启动 frontend(:5173)、permission(:8086) 和 file_management(:8087)。
# 所有构建先完成，再停止旧实例；任一新服务未就绪时自动回滚三个进程。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_DIR="$ROOT_DIR/frontend"
PERMISSION_DIR="$ROOT_DIR/permission"
FILE_MANAGEMENT_DIR="$ROOT_DIR/file_management"
LOG_DIR="$SCRIPT_DIR/logs"
RUN_DIR="$SCRIPT_DIR/run"

PERMISSION_JAR="$PERMISSION_DIR/target/Permission-0.0.1-SNAPSHOT.jar"
FILE_MANAGEMENT_JAR="$FILE_MANAGEMENT_DIR/target/file_management-0.0.1-SNAPSHOT.jar"

mkdir -p "$LOG_DIR" "$RUN_DIR" "$PERMISSION_DIR/logs" "$FILE_MANAGEMENT_DIR/logs"

require_command() {
    local command_name=$1
    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "ERROR: required command '$command_name' was not found" >&2
        exit 1
    fi
}

for required_command in java npm curl lsof; do
    require_command "$required_command"
done

if [ ! -f "$FRONTEND_DIR/package-lock.json" ]; then
    echo "ERROR: frontend/package-lock.json is missing" >&2
    exit 1
fi

echo "==> [1/6] prepare frontend dependencies ..."
if [ ! -x "$FRONTEND_DIR/node_modules/.bin/vite" ] ||
   [ ! -f "$FRONTEND_DIR/node_modules/.package-lock.json" ] ||
   [ "$FRONTEND_DIR/package-lock.json" -nt "$FRONTEND_DIR/node_modules/.package-lock.json" ]; then
    (cd "$FRONTEND_DIR" && npm ci --no-audit --no-fund)
else
    echo "    frontend dependencies are up to date"
fi

echo "==> [2/6] build frontend ..."
(cd "$FRONTEND_DIR" && npm run build)

echo "==> [3/6] package permission ..."
(cd "$PERMISSION_DIR" && ./mvnw -q -Dmaven.test.skip=true clean package)

echo "==> [4/6] package file_management ..."
(cd "$FILE_MANAGEMENT_DIR" && ./mvnw -q -Dmaven.test.skip=true clean package)

echo "==> [5/6] stop old instances ..."
"$SCRIPT_DIR/stop-all.sh"

STARTED_SERVICES=0
cleanup_on_exit() {
    local status=$?
    trap - EXIT
    if [ "$STARTED_SERVICES" -eq 1 ]; then
        echo "==> startup failed; rolling back all newly started services ..." >&2
        "$SCRIPT_DIR/stop-all.sh" || true
    fi
    exit "$status"
}
trap cleanup_on_exit EXIT

start_java_service() {
    local name=$1
    local work_dir=$2
    local jar_path=$3
    local pid

    LOG_HOME="$work_dir/logs" LOG_PATH="$work_dir/logs" \
        nohup java -jar "$jar_path" \
        > "$LOG_DIR/$name.log" 2>&1 &
    pid=$!
    echo "$pid" > "$RUN_DIR/$name.pid"
}

start_frontend() {
    local pid

    nohup "$FRONTEND_DIR/node_modules/.bin/vite" "$FRONTEND_DIR" \
        --host 127.0.0.1 --port 5173 --strictPort \
        > "$LOG_DIR/frontend.log" 2>&1 &
    pid=$!
    echo "$pid" > "$RUN_DIR/frontend.pid"
}

echo "==> [6/6] start frontend and backend services ..."
STARTED_SERVICES=1
start_java_service permission "$PERMISSION_DIR" "$PERMISSION_JAR"
start_java_service file_management "$FILE_MANAGEMENT_DIR" "$FILE_MANAGEMENT_JAR"
start_frontend

wait_for_http() {
    local name=$1
    local url=$2
    local expected_text=$3
    local pid_file="$RUN_DIR/$name.pid"
    local pid
    pid="$(<"$pid_file")"

    for _ in $(seq 1 90); do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo "    ERROR: $name exited before becoming ready; check $LOG_DIR/$name.log" >&2
            return 1
        fi

        local response
        if response=$(curl -fsS --max-time 2 "$url" 2>/dev/null); then
            if [[ "$response" == *"$expected_text"* ]]; then
                echo "    $name is UP  ($url, log: $LOG_DIR/$name.log)"
                return 0
            fi
            if [[ "$response" == *'"success":false'* ]]; then
                echo "    ERROR: $name responded but reported an unhealthy dependency; check $LOG_DIR/$name.log" >&2
                return 1
            fi
        fi
        sleep 1
    done

    echo "    ERROR: $name did not become ready within 90s; check $LOG_DIR/$name.log" >&2
    return 1
}

if ! wait_for_http permission \
    "http://127.0.0.1:8086/api/permissions/user/1" '"success":true'; then
    exit 1
fi
if ! wait_for_http file_management \
    "http://127.0.0.1:8087/api/repository/list?tenantId=1" '"success":true'; then
    exit 1
fi
if ! wait_for_http frontend \
    "http://127.0.0.1:5173/" 'id="root"'; then
    exit 1
fi

STARTED_SERVICES=0
trap - EXIT

echo "==> all services are up"
echo "    UI: http://127.0.0.1:5173"
echo "    file API: http://127.0.0.1:8087"
echo "    permission API: http://127.0.0.1:8086"
