#!/bin/bash
set -e

# --- Detect Platform ---
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
echo "=== Detected OS: $OS ==="

# --- CREATE FOLDER STRUCTURE ---
ROOT_DIR="$(dirname "$0")/.."
SERVER_DIR="$ROOT_DIR/server"
PLUGINS_DIR="$SERVER_DIR/plugins"
LOGS_DIR="$SERVER_DIR/logs"
WORLDS_DIR="$SERVER_DIR/world"

echo "Creating Minecraft server folder structure..."
mkdir -p "$SERVER_DIR" "$PLUGINS_DIR" "$LOGS_DIR" "$WORLDS_DIR"

# --- JAVA SETUP ---
if [[ "$OS" == *"linux"* ]]; then
  echo "Checking Java (Ubuntu/Linux)..."
  if ! command -v java &> /dev/null; then
    echo "Java not found, installing OpenJDK 17..."
    sudo apt update -y
    sudo apt install -y openjdk-21-jdk
  fi
elif [[ "$OS" == *"mingw"* ]] || [[ "$OS" == *"msys"* ]]; then
  echo "Checking Java (Windows/Git Bash)..."
  if ! command -v java &> /dev/null; then
    echo "⚠ Java not found! Please install Java 17+ manually from:"
    echo "   https://adoptium.net/"
    echo "Then re-run this script."
    exit 1
  fi
else
  echo "Unsupported OS: $OS"
  exit 1
fi

echo "Java version: $(java -version 2>&1 | head -n 1)"