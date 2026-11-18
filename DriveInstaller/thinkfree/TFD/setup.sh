#!/bin/bash
set -eEo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGFILE="${BASE_DIR}/template/logs/setup_ceph.log"
CONF_DIR="${BASE_DIR}/conf"
CEPH_CONF_FILE="${CONF_DIR}/ceph.conf"
S3_CONF_FILE="${CONF_DIR}/s3.conf"

source ${BASE_DIR}/template/ceph/script/install_ceph.sh
source ${BASE_DIR}/template/ceph/script/configure_ceph.sh
source "${CEPH_CONF_FILE}"

# color
YELLOW="\033[33m"
RESET="\033[0m"
TITLE="\e[1;96m"
RED="\033[31m"

trap "echo -e \"${RED}[ERROR]${RESET} An unexpected error occurred.\"; \
      echo \"[INFO] Check \$LOGFILE for more details.\"; exit 1" ERR

func_welcome() {

    if [[ "$1" == "Ceph" ]]; then
        echo ""
	echo -e " ${TITLE}[[ Ceph Installation ]]${RESET}"
	echo -e "  Ceph storage components will be installed and configured"
        echo -e "  This may take several minutes. Please wait..."
    else
	echo ""
	echo -e " ${TITLE}[[ S3 Configuration ]]${RESET}"
	echo "  Please enter S3 information."
        echo "  Press ENTER to keep the current setting from s3.conf, or type a new value to update it."
    fi
    echo ""
}

func_ceph() {
    if sudo ceph -s &>/dev/null; then
        echo "[OK] Ceph cluster already running. Skipping Ceph setup."
        return 0
    fi
    
    func_hostname_setting
    func_check_all_packages
    if ! sudo podman ps --format "{{.Names}}" | grep -q ceph; then
        func_ceph_run
    fi
        func_ceph_global_single_setting
    if ceph osd tree | grep -q "osd."; then
        echo "[OK] OSD already exists, skipping OSD setup."
    else
        func_osd_single_setting
    fi
    
    func_mgr_pool_create_new
    func_rgw_single_setting
    func_rgw_user_create
    func_pool_single_setting
    exit 0
}

# 서명·암호화 키 3개 — generate_secret_keys.sh 가 env.sh 의 빈 키만 채운다(값이 있으면 유지, 규칙은 그 스크립트 머리말).
# 기존 DB(postgres data 폴더가 비어 있지 않음)면 --existing-db — SMTP AES 를 새로 만들지 않는다. 값은 로그에 남기지 않는다(--quiet).
func_secret_keys() {
    local args=("${BASE_DIR}/template/drive/data/bin/env.sh" --quiet)
    local pg_data="${BASE_DIR}/template/database/postgres/data"
    if [ -d "$pg_data" ] && [ -n "$(ls -A "$pg_data" 2>/dev/null)" ]; then
        args+=(--existing-db)
    fi
    bash "${BASE_DIR}/generate_secret_keys.sh" "${args[@]}"
}

func_s3() {
    if [ ! -f "$S3_CONF_FILE" ]; then
        echo -e "${RED}[ERROR]${RESET} Config file not found: $S3_CONF_FILE"	
        exit 1
    fi

    source "$S3_CONF_FILE"
    while true; do

	# Current Value
	echo "[Current S3 Information]"
	echo "    - S3_ACCESS_KEY  : ${S3_ACCESS_KEY}"
	echo "    - S3_SECRET_KEY  : ${S3_SECRET_KEY}"
	echo "    - S3_BUCKET_NAME : ${S3_BUCKET_NAME}"
	echo "    - S3_REGION      : ${S3_REGION}"
	echo

	keys=("S3_ACCESS_KEY" "S3_SECRET_KEY" "S3_BUCKET_NAME" "S3_REGION")

	declare -A new_values

	for key in "${keys[@]}"; do
	    current_val=$(eval echo "\$$key")

	    if [[ -z "$current_val" ]]; then
		while true; do
		    read -p "    - ${key} (required) : " input
		    if [[ -n "$input" ]]; then
			new_values[$key]="$input"
			break
		    else
			echo "${key} cannot be empty."
		    fi
		done
	    else
		read -p "    - ${key} (${current_val}) : " input
		new_values[$key]="${input:-$current_val}"
	    fi
	done

	echo ""
	echo "[Review S3 information]"
	for key in "${keys[@]}"; do
	    echo "    - $key : ${new_values[$key]}"
	done
	echo

	read -p "Is it correct? [Y/n] " confirm
	if [[ "$confirm" =~ ^[Yy]$ || -z "$confirm" ]]; then
	    for key in "${keys[@]}"; do
		sed -i "s|^$key=.*|$key=\"${new_values[$key]}\"|" "$S3_CONF_FILE"
		eval $key=\"${new_values[$key]}\"
	    done

	    echo "[OK] S3 configuration updated in $S3_CONF_FILE"	
	    
            ENV_FILE="${BASE_DIR}/template/drive/data/bin/env.sh"
            # s3.conf 키 → application.yml 의 DRIVE_STORAGE_*. type=s3 는 AWS S3 라 region 필수·endpoint 금지(S3ClientConfig 기동 검증)
            # → 이전 스토리지 설정(ceph endpoint·admin 포함, 구 STORAGE_TYPE/CEPH_*/S3_* 이름)을 지우고 블록을 새로 쓴다
            sed -i -E "/^export (DRIVE_STORAGE_|STORAGE_TYPE=|CEPH_|S3_)/d" "${ENV_FILE}"
            STORAGE_BLOCK="$(mktemp)"
            {
                echo "export DRIVE_STORAGE_TYPE=\"s3\""
                echo "export DRIVE_STORAGE_REGION=\"${new_values[S3_REGION]}\""
                echo "export DRIVE_STORAGE_ACCESS_KEY=\"${new_values[S3_ACCESS_KEY]}\""
                echo "export DRIVE_STORAGE_SECRET_KEY=\"${new_values[S3_SECRET_KEY]}\""
                echo "export DRIVE_STORAGE_BUCKET_NAME=\"${new_values[S3_BUCKET_NAME]}\""
            } > "${STORAGE_BLOCK}"
            sed -i "/^export DRIVE_DATABASE_PASSWORD=/r ${STORAGE_BLOCK}" "${ENV_FILE}"
            rm -f "${STORAGE_BLOCK}"

	    echo "[OK] S3 exports updated in ${ENV_FILE}"
	    break
	else
	    echo "[INFO] Re-entering S3 configuration..."
	fi
    done

}

echo ""
echo -e " ${YELLOW}▶ Starting Thinkfree Drive Installation${RESET}"
echo
echo -e " ${TITLE}[[ Select Storage Type ]]${RESET}"
echo "  1: Ceph Storage(default)"
echo "  2: S3 Storage"
echo
read -p "Please choose storage type(1): " choice

case "$choice" in
    2) STORAGE_TYPE="s3" ;;
    *) STORAGE_TYPE="ceph" ;;
esac

echo "[INFO] Selected storage type: $STORAGE_TYPE"

echo
echo -e " ${TITLE}[[ Secret Keys ]]${RESET}"
func_secret_keys 2>&1 | tee -a "$LOGFILE"

if [ "$STORAGE_TYPE" = "ceph" ]; then
    func_welcome Ceph | tee -a "$LOGFILE"
    func_ceph 2>&1 | tee -a "$LOGFILE" | grep --line-buffered -E "(\x1B\[[0-9;]*m)?\[(INFO|OK|ERROR)\]" || true
    main_status=${PIPESTATUS[0]}
    if [[ $main_status -eq 0 ]]; then
        func_health_check
	exit 0
    fi
else
    func_welcome S3 | tee -a "$LOGFILE"
    func_s3 2>&1 | tee -a "$LOGFILE"
    main_status=${PIPESTATUS[0]}
    if [[ $main_status -eq 0 ]]; then
        echo "[OK] S3 setup finished successfully."
    fi
fi
