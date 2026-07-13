#!/usr/bin/env bash
# Ubuntu 22.04/24.04 EC2 인스턴스에서 최초 1회 sudo로 실행한다.
#   scp -i key.pem deploy/ec2-bootstrap.sh ubuntu@<EC2_HOST>:~/
#   ssh -i key.pem ubuntu@<EC2_HOST> "sudo bash ec2-bootstrap.sh"
set -euo pipefail

DEPLOY_DIR="/opt/ttd-backend"
DEPLOY_USER="${SUDO_USER:-ubuntu}"
SWAP_FILE="/swapfile"
SWAP_SIZE_MB=2048

if [ "$(id -u)" -ne 0 ]; then
  echo "root 권한으로 실행해야 한다 (sudo bash ec2-bootstrap.sh)" >&2
  exit 1
fi

echo "==> apt 업데이트 및 사전 패키지 설치"
apt-get update -y
apt-get install -y ca-certificates curl gnupg

echo "==> Docker 공식 apt 저장소 등록"
install -m 0755 -d /etc/apt/keyrings
if [ ! -f /etc/apt/keyrings/docker.asc ]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi
UBUNTU_CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y

echo "==> Docker Engine + Compose 플러그인 설치"
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

echo "==> ${DEPLOY_USER} 계정을 docker 그룹에 추가 (재로그인 후 적용)"
usermod -aG docker "${DEPLOY_USER}"

echo "==> 스왑 ${SWAP_SIZE_MB}MB 설정 (소형 인스턴스에서 JVM 메모리 여유 확보)"
if [ ! -f "${SWAP_FILE}" ]; then
  fallocate -l "${SWAP_SIZE_MB}M" "${SWAP_FILE}"
  chmod 600 "${SWAP_FILE}"
  mkswap "${SWAP_FILE}"
  swapon "${SWAP_FILE}"
  echo "${SWAP_FILE} none swap sw 0 0" >> /etc/fstab
else
  echo "이미 존재함, 건너뜀"
fi

echo "==> 배포 디렉터리 생성: ${DEPLOY_DIR}"
mkdir -p "${DEPLOY_DIR}"
chown "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_DIR}"

cat <<EOF

==================== 다음 단계 (수동) ====================
1. 로컬에서 아래 두 파일을 ${DEPLOY_DIR} 로 복사한다:
   scp -i key.pem docker-compose.prod.yml ubuntu@<EC2_HOST>:${DEPLOY_DIR}/
   scp -i key.pem .env.prod.example       ubuntu@<EC2_HOST>:${DEPLOY_DIR}/.env

2. EC2에 SSH로 들어가 ${DEPLOY_DIR}/.env 를 실제 값으로 채운다.
   (POSTGRES_*, RABBITMQ_*, AES/HMAC/JWT 키, OPENAI_API_KEY 등)
   RDS를 쓰는 경우 docker-compose.prod.yml의 SPRING_DATASOURCE_URL을
   RDS 엔드포인트로 바꿔야 한다 (postgres 서비스 컨테이너 대신).

3. docker 그룹 적용을 위해 한 번 재로그인(또는 재접속) 후 확인:
   ssh -i key.pem ubuntu@<EC2_HOST>
   docker ps

4. GitHub Actions CD(cd.yml)가 SSH로 접속해 아래를 실행하니
   최초 1회는 수동으로 먼저 띄워 정상 기동을 확인해두면 좋다:
   cd ${DEPLOY_DIR} && docker compose -f docker-compose.prod.yml pull backend && docker compose -f docker-compose.prod.yml up -d
============================================================
EOF
