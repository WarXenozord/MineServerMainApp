#!/bin/bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="$ROOT_DIR/server"

cd "$SERVER_DIR"
echo "Starting Paper server..."
java -Xmx1G -Xms1G -jar paper.jar nogui || true