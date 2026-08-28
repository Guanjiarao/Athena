#!/bin/bash

set -u

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR" || exit 1

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

LOG_DIR="$DIR/logs"
mkdir -p "$LOG_DIR"

SPRING_PROFILE="${SPRING_PROFILE:-prod}"
JAVA_OPTS_DEFAULT="-Xms256m -Xmx512m"
JAVA_OPTS="${JAVA_OPTS:-$JAVA_OPTS_DEFAULT}"

JAR_PATTERNS=(
  "athena-*.jar"
)

EXCLUDE_PATTERNS=(
  "*-sources.jar"
  "*-javadoc.jar"
  "original-*.jar"
)

normalize_service_name() {
    local name="$1"
    echo "$name" | tr '[:upper:]' '[:lower:]' | tr '_' '-'
}

is_excluded_jar() {
    local jar_file="$1"
    local pattern
    for pattern in "${EXCLUDE_PATTERNS[@]}"; do
        if [[ "$jar_file" == $pattern ]]; then
            return 0
        fi
    done
    return 1
}

collect_jars() {
    local files=()
    local pattern
    for pattern in "${JAR_PATTERNS[@]}"; do
        for f in $pattern; do
            if [[ -f "$f" ]] && ! is_excluded_jar "$f"; then
                files+=("$f")
            fi
        done
    done

    if [[ ${#files[@]} -eq 0 ]]; then
        return 0
    fi

    printf "%s\n" "${files[@]}" | sort -u
}

service_name_from_jar() {
    local jar_file="$1"
    basename "$jar_file" .jar
}

matches_service_filter() {
    local jar_file="$1"
    shift || true

    if [[ $# -eq 0 ]]; then
        return 0
    fi

    local service_name
    service_name="$(normalize_service_name "$(service_name_from_jar "$jar_file")")"

    local token
    for token in "$@"; do
        token="$(normalize_service_name "$token")"
        if [[ "$service_name" == *"$token"* ]]; then
            return 0
        fi
    done

    return 1
}

find_pid_by_jar() {
    local jar_file="$1"
    pgrep -f "java.*$jar_file"
}

show_recent_log() {
    local log_file="$1"
    if [[ -f "$log_file" ]]; then
        echo -e "${YELLOW}最近日志（最后 30 行）:${NC}"
        tail -n 30 "$log_file"
    else
        echo -e "${YELLOW}日志文件还不存在: $log_file${NC}"
    fi
}

stop_one() {
    local jar_file="$1"
    local service_name
    service_name="$(service_name_from_jar "$jar_file")"

    local pids
    pids="$(find_pid_by_jar "$jar_file")"

    if [[ -z "${pids:-}" ]]; then
        echo -e "${YELLOW}[未运行] $service_name${NC}"
        return 0
    fi

    echo -e "${BLUE}[停止] $service_name -> PID: $pids${NC}"
    for pid in $pids; do
        kill "$pid" 2>/dev/null || true
    done

    sleep 5

    local remaining
    remaining="$(find_pid_by_jar "$jar_file")"
    if [[ -n "${remaining:-}" ]]; then
        echo -e "${YELLOW}[强制停止] $service_name -> PID: $remaining${NC}"
        for pid in $remaining; do
            kill -9 "$pid" 2>/dev/null || true
        done
    fi

    echo -e "${GREEN}[已停止] $service_name${NC}"
}

start_one() {
    local jar_file="$1"
    local service_name
    service_name="$(service_name_from_jar "$jar_file")"

    local pids
    pids="$(find_pid_by_jar "$jar_file")"
    if [[ -n "${pids:-}" ]]; then
        echo -e "${YELLOW}[跳过] $service_name 已在运行 (PID: $pids)${NC}"
        return 0
    fi

    local log_file="$LOG_DIR/${service_name}.log"

    # 按服务加载 <短名>.env（如 cognition-agent.env 存放 ATHENA_MODEL_* 模型凭证）。
    # 短名 = jar 名去掉 athena- 前缀和版本号：athena-cognition-agent-0.0.1-SNAPSHOT -> cognition-agent
    local short_name env_file
    short_name="$(echo "$service_name" | sed -E 's/^athena-//; s/-[0-9]+\.[0-9]+.*$//')"
    env_file="$DIR/${short_name}.env"
    if [[ -f "$env_file" ]]; then
        echo -e "${BLUE}  env: ${short_name}.env${NC}"
        set -a
        # shellcheck disable=SC1090
        source "$env_file"
        set +a
    fi

    echo -e "${BLUE}[启动] $service_name${NC}"
    echo -e "${BLUE}  profile: $SPRING_PROFILE${NC}"
    echo -e "${BLUE}  opts: $JAVA_OPTS${NC}"

    nohup java \
        $JAVA_OPTS \
        -Dspring.profiles.active="$SPRING_PROFILE" \
        -Dserver.tomcat.basedir="$LOG_DIR" \
        -Dlogging.file.name="$log_file" \
        -jar "$jar_file" \
        > /dev/null 2>&1 &

    local pid=$!
    sleep 4

    if ps -p "$pid" > /dev/null 2>&1; then
        echo -e "${GREEN}  ✓ 启动成功 (PID: $pid)${NC}"
    else
        echo -e "${RED}  ✗ 启动失败，请检查日志: $log_file${NC}"
        show_recent_log "$log_file"
        return 1
    fi
}

stop_all() {
    local filters=("$@")

    echo -e "${BLUE}正在停止服务...${NC}"

    local jars
    jars="$(collect_jars)"

    if [[ -z "${jars:-}" ]]; then
        echo -e "${YELLOW}当前目录下没有找到可管理的 JAR${NC}"
        return 0
    fi

    local matched=0
    while IFS= read -r jar; do
        [[ -z "$jar" ]] && continue
        if matches_service_filter "$jar" "${filters[@]}"; then
            matched=1
            stop_one "$jar"
        fi
    done <<< "$jars"

    if [[ $matched -eq 0 ]]; then
        echo -e "${YELLOW}没有匹配到指定服务: ${filters[*]}${NC}"
        return 1
    fi

    echo -e "${GREEN}停止流程完成${NC}"
}

start_all() {
    local filters=("$@")

    echo -e "${BLUE}开始启动服务...${NC}"

    local jars
    jars="$(collect_jars)"

    if [[ -z "${jars:-}" ]]; then
        echo -e "${YELLOW}当前目录下没有找到可启动的 JAR${NC}"
        return 1
    fi

    local matched=0
    local started_count=0
    local failed_count=0

    while IFS= read -r jar; do
        [[ -z "$jar" ]] && continue
        if matches_service_filter "$jar" "${filters[@]}"; then
            matched=1
            if start_one "$jar"; then
                started_count=$((started_count + 1))
            else
                failed_count=$((failed_count + 1))
            fi
            sleep 2
        fi
    done <<< "$jars"

    if [[ $matched -eq 0 ]]; then
        echo -e "${YELLOW}没有匹配到指定服务: ${filters[*]}${NC}"
        return 1
    fi

    echo ""
    echo -e "${GREEN}启动完成，成功: $started_count，失败: $failed_count${NC}"
    [[ $failed_count -eq 0 ]]
}

restart_all() {
    local filters=("$@")
    stop_all "${filters[@]}"
    echo ""
    start_all "${filters[@]}"
}

status_all() {
    local filters=("$@")

    echo -e "${BLUE}服务状态检查:${NC}"
    echo "========================================"

    local jars
    jars="$(collect_jars)"

    if [[ -z "${jars:-}" ]]; then
        echo -e "${YELLOW}当前目录下没有找到可管理的 JAR${NC}"
        return 0
    fi

    local matched=0
    local running_count=0
    while IFS= read -r jar; do
        [[ -z "$jar" ]] && continue
        if ! matches_service_filter "$jar" "${filters[@]}"; then
            continue
        fi

        matched=1
        local service_name
        service_name="$(service_name_from_jar "$jar")"

        local pids
        pids="$(find_pid_by_jar "$jar")"

        if [[ -n "${pids:-}" ]]; then
            echo -e "${GREEN}✓ $service_name 运行中 (PID: $pids)${NC}"
            running_count=$((running_count + 1))
        else
            echo -e "${RED}✗ $service_name 未运行${NC}"
        fi
    done <<< "$jars"

    if [[ $matched -eq 0 ]]; then
        echo -e "${YELLOW}没有匹配到指定服务: ${filters[*]}${NC}"
        return 1
    fi

    echo "========================================"
    echo -e "总计: $running_count 个服务正在运行"

    echo ""
    echo -e "${BLUE}网络端口监听情况:${NC}"
    ss -lntp 2>/dev/null | grep java || netstat -tlnp 2>/dev/null | grep java || echo "未找到服务端口"
}

show_logs() {
    local filter="${1:-}"

    echo -e "${BLUE}可查看的日志文件:${NC}"

    if [[ ! -d "$LOG_DIR" ]]; then
        echo -e "${YELLOW}日志目录不存在${NC}"
        return 0
    fi

    local has_log=0
    local log_file
    for log_file in "$LOG_DIR"/*.log; do
        [[ -f "$log_file" ]] || continue
        if [[ -z "$filter" || "$(basename "$log_file")" == *"$filter"* ]]; then
            echo "  $log_file"
            has_log=1
        fi
    done

    if [[ $has_log -eq 0 ]]; then
        echo -e "${YELLOW}没有匹配到日志文件${NC}"
        return 0
    fi

    echo ""
    echo -e "${YELLOW}查看示例:${NC}"
    echo "tail -f $LOG_DIR/athena-gateway-0.0.1-SNAPSHOT.log"
    echo "tail -f $LOG_DIR/athena-insight-biz-0.0.1-SNAPSHOT.log"
    echo "tail -f $LOG_DIR/athena-rag-bootstrap-0.0.1-SNAPSHOT.log"
    echo "tail -f $LOG_DIR/*.log"
}

usage() {
    echo -e "${BLUE}Athena 服务管理脚本${NC}"
    echo "=========================="
    echo -e "${GREEN}使用方法:${NC}"
    echo "  $0 {start|stop|restart|status|logs} [service-name ...]"
    echo ""
    echo -e "${YELLOW}命令说明:${NC}"
    echo "  start    - 启动所有服务或指定服务"
    echo "  stop     - 停止所有服务或指定服务"
    echo "  restart  - 重启所有服务或指定服务"
    echo "  status   - 查看所有服务或指定服务状态"
    echo "  logs     - 查看日志文件列表，可带服务名过滤"
    echo ""
    echo -e "${YELLOW}环境变量:${NC}"
    echo "  SPRING_PROFILE=prod"
    echo "  JAVA_OPTS='-Xms512m -Xmx1024m'"
    echo ""
    echo -e "${YELLOW}示例:${NC}"
    echo "  $0 start"
    echo "  $0 start gateway"
    echo "  $0 restart rag"
    echo "  $0 status insight"
    echo "  $0 logs rag"
}

COMMAND="${1:-}"
shift || true

case "$COMMAND" in
    start)
        start_all "$@"
        ;;
    stop)
        stop_all "$@"
        ;;
    restart)
        restart_all "$@"
        ;;
    status)
        status_all "$@"
        ;;
    logs)
        show_logs "${1:-}"
        ;;
    *)
        usage
        exit 1
        ;;
esac
