#!/bin/bash
set -e # 에러 발생 시 스크립트 중단

# color
YELLOW="\033[33m"
RESET="\033[0m"
TITLE="\e[1;96m"
RED="\033[31m"

BASE_DIR="$(pwd)"

echo ""
echo -e " ${YELLOW}▶ Starting Installation${RESET}"
echo ""
echo -e " ${TITLE}[[ Available Installation Options ]]${RESET}"
echo -e "  1: All-in-one(Thinkfree Drive + Thinkfree Office)"
echo -e "  2: Thinkfree Drive"
echo -e "  3: Thinkfree Office"
echo ""
read -p "Please choose installation type: " choice

case "$choice" in
    1)
	echo "[INFO] Installing All-in-one(Thinkfree Drive + Thinkfree Office)..."
        if ! command -v docker &>/dev/null; then
            echo -e "${RED}[ERROR]${RESET} Docker is not installed. Please install Docker first."
            exit 1
        fi
	bash "${BASE_DIR}/TFD/setup.sh"
	cd "${BASE_DIR}/TFD" || { echo -e "${RED}[ERROR]${RESET} No such TFD directory."; exit 1; }
	sudo docker compose up -d --build
	cd "${BASE_DIR}/TFO" || { echo -e "${RED}[ERROR]${RESET} No such TFO directory."; exit 1; }
	echo ""
	echo -e " ${YELLOW}▶ Starting Thinkfree Office Installation${RESET}"
        echo ""
	sudo docker compose up -d --build
        ;;
    2)
        echo "[INFO] Installing TFD..."
	if ! command -v docker &>/dev/null; then
            echo -e "${RED}[ERROR]${RESET} Docker is not installed. Please install Docker first."
            exit 1
        fi
	bash "${BASE_DIR}/TFD/setup.sh"
	cd "${BASE_DIR}/TFD" || { echo -e "${RED}[ERROR]${RESET} No such TFD directory."; exit 1; }
	sudo docker compose up -d --build
	;;
    3)
        echo "[INFO] Installing TFO..."
	if ! command -v docker &>/dev/null; then
            echo -e "${RED}[ERROR]${RESET} Docker is not installed. Please install Docker first."
            exit 1
        fi
	cd "${BASE_DIR}/TFO" || { echo -e "${RED}[ERROR]${RESET} No such TFO directory."; exit 1; }
	echo ""
	echo -e " ${YELLOW}▶ Starting Thinkfree Office Installation${RESET}"
        echo ""
        sudo docker compose up -d --build
	;;
    *)
	echo -e "${RED}[ERROR]${RESET} Invalid choice"
	exit 1
	;;
esac

echo "[INFO] Installation complete"
