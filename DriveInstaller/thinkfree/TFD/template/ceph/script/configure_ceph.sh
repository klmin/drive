#!/bin/bash

source ${CEPH_CONF_FILE}

# color
RED="\033[31m"
RESET="\033[0m"

func_ceph_run(){
    local image="quay.io/ceph/ceph:v19.2.0"
    echo "[INFO] Starting Ceph bootstrap..."
    export CEPHADM_IMAGE="${image}"

    sudo -E cephadm bootstrap   --mon-ip "${main_host_ip}" --single-host-defaults  --initial-dashboard-user admin   --initial-dashboard-password admin123
    echo "[OK] Ceph bootstrap completed successfully"
}

func_ceph_global_single_setting(){
    echo "[INFO] Applying single-node pool configuration (size=1)..."
    # pool size 1
    sudo cephadm shell -- ceph config set mon mon_allow_pool_size_one true
    # osd pool min_size 1
    sudo cephadm shell -- ceph config set global osd_pool_default_min_size 1
    # osd pool size 1
    sudo cephadm shell -- ceph config set global osd_pool_default_size 1
    echo "[OK] Pool configuration for single-node setup applied successfully"
}

func_osd_single_setting(){
    echo "[INFO] Checking available OSD devices..."
    available_count=$(sudo cephadm shell -- ceph orch device ls 2>/dev/null | grep -c "Yes")

    if [ "$available_count" -eq 0 ]; then
        echo -e "${RED}[ERROR]${RESET} No available devices found for OSD deployment."
        echo "[INFO] Please check with the 'lsblk' command and make sure there are unused empty disks available for OSD."
        return 1
    fi

    OSD_YAML="${BASE_DIR}/template/ceph/data/osd-encrypted.yaml"
    echo "[INFO] Adding all available OSD devices..."
    if cat "$OSD_YAML" | sudo cephadm shell -- ceph orch apply -i -; then
        echo "[OK] OSD devices added successfully"
    else
        echo -e "${RED}[ERROR]${RESET} Failed to add OSD devices"
        return 1
    fi
}

func_mgr_pool_create_new(){
    echo "[INFO] Checking OSD status..."

    for i in {1..30}; do
      line=$(sudo cephadm shell -- ceph osd tree | grep "up" | grep "1.00000" | grep "1.00000" || true)

      echo "DEBUG: line=[$line]"

      if [ -n "$line" ]; then
        echo "[OK] OSDs are up and in. Restarting MGR service..."
        sudo cephadm shell -- ceph orch restart mgr
        echo "[OK] MGR service restarted successfully"
        return 0
      fi

      echo "[INFO] OSD not available yet, waiting... ($i/30)"
      sleep 10
    done

    echo -e "${RED}[ERROR]${RESET} OSDs did not reach up+in state within 300 seconds. Exiting script."
    return 1
}

func_mgr_pool_create(){
    echo "[INFO] Restarting MGR service..."
    sudo cephadm shell -- ceph orch restart mgr
    echo "[OK] MGR service restarted successfully"
}

func_rgw_single_setting(){
    echo "[INFO] Configuring single RGW instance..."
    # Create realm
    sudo cephadm shell -- radosgw-admin realm create --rgw-realm=default --default
    # Create ZoneGroup
    sudo cephadm shell -- radosgw-admin zonegroup create --rgw-zonegroup=default --master --default
    # Create Zone
    sudo cephadm shell -- radosgw-admin zone create --rgw-zonegroup=default --rgw-zone=default --master --default
    # Commit configuration changes
    sudo cephadm shell -- radosgw-admin period update --commit
    # Deploy RGW service
    sudo cephadm shell -- ceph orch apply rgw default --placement="${main_host_name}"
    # Remove existing RGW instance
    sudo cephadm shell -- ceph orch rm rgw.default
    # Change RGW port (80 -> 9000)
    sudo cephadm shell -- ceph orch apply rgw rgw.default --port 9000 --placement="${main_host_name}"
    echo "[OK] Single RGW instance configured successfully"

}

func_pool_single_setting(){
    echo "[INFO] Applying single-node pool settings..."
    # Set the pool's size and min_size 1
    sudo cephadm shell -- ceph osd pool set .rgw.root min_size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set .rgw.root size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set .mgr min_size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set .mgr size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set default.rgw.log min_size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set default.rgw.log size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set default.rgw.control min_size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set default.rgw.control size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set default.rgw.meta min_size 1 --yes-i-really-mean-it
    sudo cephadm shell -- ceph osd pool set default.rgw.meta size 1 --yes-i-really-mean-it

    echo "[INFO] Muting health warning for single-replica pools..."
    # Disable health check warnings for pools configured with size = 1
    sudo cephadm shell -- ceph health mute POOL_NO_REDUNDANCY

    echo "[INFO] Adjusting PG/PGP numbers for RGW pools..."
    # Set pg_num, pgp_num
    sudo cephadm shell -- ceph osd pool set .rgw.root pg_num 16
    sudo cephadm shell -- ceph osd pool set .rgw.root pgp_num 16
    sudo cephadm shell -- ceph osd pool set default.rgw.log pg_num 16
    sudo cephadm shell -- ceph osd pool set default.rgw.log pgp_num 16
    sudo cephadm shell -- ceph osd pool set default.rgw.control pg_num 16
    sudo cephadm shell -- ceph osd pool set default.rgw.control pgp_num 16
    sudo cephadm shell -- ceph osd pool set default.rgw.meta pg_num 16
    sudo cephadm shell -- ceph osd pool set default.rgw.meta pgp_num 16

    echo "[OK] Single-node pool settings applied successfully"

}

func_mgr_dashboard_setting(){
    echo "[INFO] Applying Dashboard HTTP/Port settings..."

    sudo cephadm shell -- ceph config set mgr mgr/dashboard/ssl false
    sudo cephadm shell -- ceph config set mgr mgr/dashboard/server_port 9090
    sudo cephadm shell -- ceph orch restart mgr

    echo "[OK] Dashboard set to HTTP :9090"
}

func_rgw_user_create(){
    FILE_PATH="${BASE_DIR}/template/ceph/data/admin.rgw.key"

    echo "[INFO] Creating RGW admin user and storing key..."
    sudo cephadm shell -- radosgw-admin user create --uid="admin" --display-name="admin" --system \
      | grep -E '"(access_key|secret_key)"' \
      | sed -E 's/.*"access_key"\s*:\s*"([^"]+)".*/access_key=\1/; s/.*"secret_key"\s*:\s*"([^"]+)".*/secret_key=\1/' \
      > "${FILE_PATH}"

    echo "[OK] RGW admin user created and key saved at ${FILE_PATH}"

    echo "[INFO] Updating env.sh with new Ceph keys..."
    ENV_FILE="${BASE_DIR}/template/drive/data/bin/env.sh"
    ACCESS_KEY=$(grep access_key "${FILE_PATH}" | cut -d'=' -f2)
    SECRET_KEY=$(grep secret_key "${FILE_PATH}" | cut -d'=' -f2)

    # application.yml 의 DRIVE_STORAGE_*. type=ceph 는 endpoint 필수(S3ClientConfig 기동 검증), admin = Ceph 대시보드(용량 조회)
    # → 이전 스토리지 설정(구 STORAGE_TYPE/CEPH_*/S3_* 이름 포함)을 지우고 블록을 새로 쓴다
    sed -i -E "/^export (DRIVE_STORAGE_|STORAGE_TYPE=|CEPH_|S3_)/d" "${ENV_FILE}"
    STORAGE_BLOCK="$(mktemp)"
    {
        echo "export DRIVE_STORAGE_TYPE=\"ceph\""
        echo "export DRIVE_STORAGE_ENDPOINT=\"http://${main_host_ip}:${main_host_port}\""
        echo "export DRIVE_STORAGE_ACCESS_KEY=\"${ACCESS_KEY}\""
        echo "export DRIVE_STORAGE_SECRET_KEY=\"${SECRET_KEY}\""
        echo "export DRIVE_STORAGE_BUCKET_NAME=\"${ceph_bucket_name}\""
        echo "export DRIVE_STORAGE_ADMIN_URL=\"http://${main_host_ip}:9090\""
        echo "export DRIVE_STORAGE_ADMIN_USERNAME=\"admin\""
        echo "export DRIVE_STORAGE_ADMIN_PASSWORD=\"admin123\""
    } > "${STORAGE_BLOCK}"
    sed -i "/^export DRIVE_DATABASE_PASSWORD=/r ${STORAGE_BLOCK}" "${ENV_FILE}"
    rm -f "${STORAGE_BLOCK}"

    echo "[OK] env.sh updated successfully with Ceph configuration"
}

func_health_check(){
    echo "[INFO] Running final Ceph Health Check..." | tee -a "$LOGFILE"
    sudo ceph -s | tee -a "$LOGFILE"
    echo "[OK] Ceph installation completed successfully" | tee -a "$LOGFILE"
}
