#!/bin/bash
# Drive 서명·암호화 키 3개(application.yml jwt·smtp.aes·download)를 파일에 한 번 만들어 두고 계속 같은 값을 쓰게 한다.
#
# 사용
#   ./generate_secret_keys.sh <파일> [--existing-db] [--dotenv] [--quiet]
#       파일에 저장하고 `export` 줄을 화면에 출력 — 서버 환경변수·k8s Secret 등에 복사해 넣는다.
#   source generate_secret_keys.sh <파일> [--existing-db]
#       파일에 저장하고 현재 셸에 export — 이어서 java -jar 하면 그 값으로 뜬다.
#   ./generate_secret_keys.sh --print-only [--existing-db] [--dotenv]
#       파일 없이 값만 출력 — 비밀 저장소·k8s Secret·서버 환경변수에 직접 넣을 때. 어디에도 저장되지 않으니 출력한 값을 바로 보관한다
#       (다시 실행하면 다른 값이 나온다). 판정 1번(파일)은 건너뛰고 2~4번만 본다.
#   설치본: setup.sh 가 template/drive/data/bin/env.sh 를 대상으로 호출(기존 DB 가 있으면 --existing-db), drive.sh 는 env.sh 를 source 해 기동.
#
# 키마다 판정(앞에서 걸리면 끝)
#   1. 파일에 값이 있다            → 그대로 둔다(재실행·재기동 안전)
#   2. 현재 환경변수에 이미 있다     → 그 값을 파일에 옮겨 적는다(이미 주입된 서버)
#   3. SMTP AES 이고 --existing-db  → 예전 jar 내장값(기존 DB 에 저장된 SMTP 비밀번호를 푸는 키)
#   4. 그 밖                       → openssl rand -base64 24(무작위 24바이트 → 32글자). JWT(HS256 32바이트 이상)·SMTP AES(정확히 16·24·32바이트)·다운로드(HMAC) 조건을 모두 만족
#
# 옵션
#   --existing-db  이미 운영 중인 DB 를 쓴다(SMTP 비밀번호가 저장돼 있을 수 있음) — SMTP AES 를 새로 만들지 않는다
#   --dotenv       KEY=value 형식으로 쓴다(docker --env-file·k8s --from-env-file 용). 기본은 export KEY='value'(셸 source 용)
#   --quiet        값을 화면에 출력하지 않는다(로그에 남기지 않을 때 — setup.sh)
#   --print-only   파일 없이 값만 출력(위 세 번째 사용법). 파일 경로·--quiet 와 같이 쓸 수 없다
# 파일 권한은 600 으로 바꾼다. 키 값은 이 스크립트가 지우거나 바꾸지 않는다.

_GSK_KEYS="DRIVE_JWT_SECRET_KEY DRIVE_SMTP_AES_SECRET_KEY DRIVE_DOWNLOAD_SECRET_KEY"
_GSK_LEGACY_SMTP_AES_SECRET_KEY='F4r1cG8tL7pXq9zYvB2eW6hK3uJnMdPs'

_gsk_usage() {
    echo "usage: generate_secret_keys.sh <file> [--existing-db] [--dotenv] [--quiet]"
    echo "       source generate_secret_keys.sh <file> [--existing-db]"
    echo "       generate_secret_keys.sh --print-only [--existing-db] [--dotenv]"
}

# 파일에서 키 값 읽기 — export KEY='v' / export KEY="v" / KEY=v 모두
_gsk_read() {
    tr -d '\r' < "$1" | sed -n -E "s/^(export[[:space:]]+)?$2=['\"]?([^'\"]*)['\"]?[[:space:]]*$/\2/p" | tail -n 1
}

# 키 3줄을 한 덩어리로 쓴다 — 세 값을 다 정한 뒤 한 번만 호출. 값은 awk 환경변수로 넘겨 특수문자를 해석하지 않는다.
#   어디에 쓰든 기존 키 줄은 모두 지우고 3줄을 순서대로(JWT·SMTP AES·다운로드) 한 곳에 쓴다(섞여 있거나 흩어진 경우도 모임).
#   1. "# SecretKey" 주석이 있으면: 그 주석 바로 아래
#   2. 주석은 없고 키 줄이 있으면: 첫 키 줄 자리
#   3. 둘 다 없으면: DRIVE_DATABASE_PASSWORD 줄 뒤(없으면 파일 끝)에 빈 줄 + "# SecretKey" + 3줄
# 같은 값으로 다시 실행하면 파일 내용이 그대로다.
_gsk_write_block() {
    local file="$1" dotenv="$2" key block="" tmp
    for key in $_GSK_KEYS; do
        if $dotenv; then block+="${key}=${vals[$key]}"$'\n'; else block+="export ${key}='${vals[$key]}'"$'\n'; fi
    done
    tmp="$(mktemp "${file}.XXXXXX")" || return 1
    GSK_BLOCK="${block%$'\n'}" GSK_KEYS_RE="^(export[ \t]+)?(${_GSK_KEYS// /|})=" awk '
        BEGIN { blk = ENVIRON["GSK_BLOCK"]; kre = ENVIRON["GSK_KEYS_RE"]; hdr = "^#[ \t]*[Ss]ecret[ \t_-]*[Kk]ey[ \t]*$"; anc = "^(export[ \t]+)?DRIVE_DATABASE_PASSWORD=" }
        { line[NR] = $0; s = $0; sub(/\r$/, "", s); clean[NR] = s
          if (!firstKey && s ~ kre) firstKey = NR
          if (!header && s ~ hdr) header = NR
          if (s ~ anc) anchor = NR }
        END {
          for (i = 1; i <= NR; i++) {
            if (clean[i] ~ kre) { if (!header && i == firstKey) print blk; continue }
            print line[i]
            if (header && i == header) print blk
            else if (!header && !firstKey && anchor && i == anchor) { print ""; print "# SecretKey"; print blk }
          }
          if (!header && !firstKey && !anchor) { if (NR) print ""; print "# SecretKey"; print blk }
        }
    ' "$file" > "$tmp" && cat "$tmp" > "$file"
    local rc=$?
    rm -f "$tmp"
    return $rc
}

_gsk_valid() {
    local key="$1" n=${#2}
    if [ "$key" = "DRIVE_SMTP_AES_SECRET_KEY" ]; then
        [ "$n" -eq 16 ] || [ "$n" -eq 24 ] || [ "$n" -eq 32 ]
    else
        [ "$n" -ge 32 ]
    fi
}

_gsk_main() {
    local sourced="$1"; shift
    local file="" existing_db=false dotenv=false quiet=false print_only=false arg
    for arg in "$@"; do
        case "$arg" in
            --existing-db) existing_db=true ;;
            --dotenv) dotenv=true ;;
            --quiet) quiet=true ;;
            --print-only) print_only=true ;;
            -h|--help) _gsk_usage; return 0 ;;
            -*) echo "[ERROR] unknown option: $arg" >&2; _gsk_usage >&2; return 2 ;;
            *) if [ -n "$file" ]; then echo "[ERROR] only one file" >&2; return 2; fi; file="$arg" ;;
        esac
    done
    if $print_only; then
        if [ -n "$file" ] || $quiet; then echo "[ERROR] --print-only takes no file and no --quiet" >&2; _gsk_usage >&2; return 2; fi
    elif [ -z "$file" ]; then
        _gsk_usage >&2; return 2
    fi
    if ! command -v openssl >/dev/null 2>&1; then
        echo "[ERROR] openssl not found — install it (dnf -y install openssl) and run again." >&2
        return 1
    fi
    if ! $print_only; then
        if [ ! -e "$file" ]; then
            ( umask 077; mkdir -p "$(dirname "$file")" && : > "$file" ) || { echo "[ERROR] cannot create $file" >&2; return 1; }
            echo "[OK] created $file"
        fi
        if [ ! -w "$file" ]; then echo "[ERROR] cannot write $file" >&2; return 1; fi
    fi

    local key value how new_smtp=false any_new=false
    local -A vals
    for key in $_GSK_KEYS; do
        value=""
        $print_only || value="$(_gsk_read "$file" "$key")"
        if [ -n "$value" ]; then
            how="kept (already in file)"
        elif [ -n "${!key:-}" ]; then
            value="${!key}"; how="copied from current environment"
        elif [ "$key" = "DRIVE_SMTP_AES_SECRET_KEY" ] && $existing_db; then
            value="$_GSK_LEGACY_SMTP_AES_SECRET_KEY"; how="previous built-in value (--existing-db)"
        else
            value="$(openssl rand -base64 24)"; how="generated"; any_new=true
            [ "$key" = "DRIVE_SMTP_AES_SECRET_KEY" ] && new_smtp=true
        fi
        if ! _gsk_valid "$key" "$value"; then
            echo "[ERROR] ${key}: invalid length ${#value} (JWT·download >= 32, SMTP AES = 16·24·32) — fix it in $file" >&2
            return 1
        fi
        case "$value" in *"'"*|*$'\n'*) echo "[ERROR] ${key}: value must not contain ' or newline" >&2; return 1 ;; esac
        vals[$key]="$value"
        echo "[OK] ${key}: ${how}"
    done
    if ! $print_only; then
        _gsk_write_block "$file" "$dotenv" || { echo "[ERROR] cannot write $file" >&2; return 1; }
        chmod 600 "$file" 2>/dev/null || echo "[WARN] chmod 600 $file failed — restrict read access to this file yourself"
    fi

    if $new_smtp; then
        echo "[WARN] DRIVE_SMTP_AES_SECRET_KEY was newly generated. If this server uses a DB that already has SMTP settings,"
        if $print_only; then
            echo "       do not use this value — run again with --existing-db (otherwise stored SMTP passwords cannot be decrypted)."
        else
            echo "       remove that line from $file and run again with --existing-db (otherwise stored SMTP passwords cannot be decrypted)."
        fi
    fi
    if $print_only; then
        echo "[WARN] Not saved anywhere — store these values now (secret store / server environment). Running again prints different keys."
    elif $any_new; then
        echo "[WARN] Back up $file — losing DRIVE_SMTP_AES_SECRET_KEY makes stored SMTP passwords unreadable."
    fi

    if [ "$sourced" = true ]; then
        for key in $_GSK_KEYS; do export "$key=${vals[$key]}"; done
        echo "[OK] exported to the current shell"
    fi
    if $print_only || { [ "$sourced" != true ] && ! $quiet; }; then
        if $print_only; then
            echo "# --- generated values (not saved) ---"
        else
            echo "# --- values in $file (put these into the server environment / secret store) ---"
        fi
        for key in $_GSK_KEYS; do
            if $dotenv; then echo "${key}=${vals[$key]}"; else echo "export ${key}='${vals[$key]}'"; fi
        done
    fi
    return 0
}

if [[ "${BASH_SOURCE[0]}" != "${0}" ]]; then
    _gsk_main true "$@"
    _gsk_rc=$?
    unset -f _gsk_usage _gsk_read _gsk_write_block _gsk_valid _gsk_main
    unset _GSK_KEYS _GSK_LEGACY_SMTP_AES_SECRET_KEY
    return $_gsk_rc 2>/dev/null || true
else
    _gsk_main false "$@"
    exit $?
fi
