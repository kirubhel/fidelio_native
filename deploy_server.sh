#!/bin/bash

# Deployment script for RESPECT native app metadata server
# Server: 196.189.50.57
# Port: 9018

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

SERVER_IP="196.189.50.57"
SERVER_USER="administrator"
SERVER_PASSWORD="Girar@2025"
REMOTE_DIR="/administrator/e-learning/native_app_server"
APP_NAME="kokeb-fidel-metadata"

echo -e "${GREEN}Step 1: Building Docker image locally (for linux/amd64)...${NC}"
docker build --platform linux/amd64 -t ${APP_NAME}:latest .

echo -e "${GREEN}Step 2: Saving and streaming image to server...${NC}"
docker save ${APP_NAME}:latest | \
sshpass -p "$SERVER_PASSWORD" ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_IP} \
"docker load"

echo -e "${GREEN}Step 3: Creating remote directory and copying compose...${NC}"
sshpass -p "$SERVER_PASSWORD" ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR}"
sshpass -p "$SERVER_PASSWORD" scp -o StrictHostKeyChecking=no docker-compose.yml ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

echo -e "${GREEN}Step 4: Starting server...${NC}"
sshpass -p "$SERVER_PASSWORD" ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_IP} \
"cd ${REMOTE_DIR} && docker compose up -d"

echo -e "${GREEN}Done! Server is running at http://${SERVER_IP}:9018${NC}"
echo -e "${GREEN}Please ensure your reverse proxy maps https://learningcloud.et/native_app/ to http://localhost:9018/${NC}"
