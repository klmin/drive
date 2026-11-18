#!/bin/bash

source ${CEPH_CONF_FILE}

# color
RED="\033[31m"
RESET="\033[0m"

REQUIRED_PACKAGE_ARR=("epel-release" "cephadm" "python3-jinja2" "ceph-common" "podman")

func_hostname_setting(){
    echo "[INFO] Setting host_name .. "
    echo " "
    echo "[INFO] main_host_ip : ${main_host_ip}"
    echo "[INFO] main_host_name : ${main_host_name}"
    if [[ -n "${main_host_ip}" && -n "${main_host_name}" ]]; then
        count=$(grep -w "${main_host_ip} ${main_host_name}" /etc/hosts | wc -l)
        if [[ $count -lt 1 ]]; then
            echo -e "${main_host_ip} ${main_host_name}" | sudo tee -a /etc/hosts  > /dev/null || true
        else
            echo "${main_host_ip} ${main_host_name} has already been added."
        fi
    else
        echo -e "${RED}[ERROR]${RESET} main_host_ip or main_host_name is incorrectly defined."
        echo "[INFO] Please check ${CEPH_CONF_FILE} file."
        return 1
    fi

    echo -n "[INFO] Applying host_name .. "
    if [[ ${main_host_name} == `hostname` ]]; then
        echo "${main_host_name} is already apply."
    else
        sudo hostnamectl set-hostname "${main_host_name}" || true 
        echo "OK"
    fi
}

func_check_package() {
    package_name=$1
    echo "[INFO] Checking ${package_name} is installed .. "
    if [[ $(rpm -qi ${package_name} 2>&1) == *"is not installed"*  ]] || [[ $(rpm -qi ${package_name} 2>&1 | wc -l) == "1"  ]]; then
        echo -e "${RED}[ERROR]${RESET} ${package_name} is not installed."
        echo "[INFO] Please install ${package_name} using following command and try again."

        if [[ ${package_name} == "ceph-common" ]]; then
            echo "  sudo cephadm install ${package_name}"
        else
            echo "  sudo dnf install -y ${package_name}"
        fi
        return 1
    else
        package_version=$(rpm -qi ${package_name} 2>&1 | awk -F ' ' '/Version/ {print $3}')

        echo "[OK] (${package_version})"
    fi
}

func_check_all_packages() {
    for pkg in "${REQUIRED_PACKAGE_ARR[@]}"; do
        func_check_package "${pkg}"
    done
}

func_echo_fail() {
    echo -e "${RED}[ERROR]${RESET} An error has occurred. Please try again."
    return 1
}
