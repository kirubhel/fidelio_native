#!/bin/bash

# Deployment script for RESPECT native app metadata
# Server: 196.189.50.57
# Path: https://learningcloud.et/native_app/RESPECT_MANIFEST.json

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
SERVER_IP="196.189.50.57"
SERVER_USER="administrator"
SERVER_PASSWORD="Girar@2025"
REMOTE_DIR="/administrator/e-learning/native_app"

echo -e "${GREEN}Deploying Native App Manifests...${NC}"

# Create remote directory first
sshpass -p "$SERVER_PASSWORD" ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR}"

# Upload the assets files
echo -e "${GREEN}Copying files to ${REMOTE_DIR}...${NC}"
cd app/src/main/assets
sshpass -p "$SERVER_PASSWORD" scp -o StrictHostKeyChecking=no RESPECT_MANIFEST.json opds.json activities.json ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/

# Also copy common images if needed
sshpass -p "$SERVER_PASSWORD" ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_IP} "mkdir -p ${REMOTE_DIR}/images"
sshpass -p "$SERVER_PASSWORD" scp -o StrictHostKeyChecking=no images/logo.png ${SERVER_USER}@${SERVER_IP}:${REMOTE_DIR}/images/

echo -e "${GREEN}Done!${NC}"
echo -e "${GREEN}The manifest should be at: https://learningcloud.et/native_app/RESPECT_MANIFEST.json${NC}"
echo -e "${GREEN}Warning: Ensure your Nginx/Reverse Proxy is configured to serve ${REMOTE_DIR}${NC}"
