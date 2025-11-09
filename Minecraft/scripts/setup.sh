#!/bin/bash
set -e

# --- PAPER SETUP ---
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="$ROOT_DIR/server"
JAR_PATH="$SERVER_DIR/paper.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "Paper not found"
else
  echo "Paper already exists: $JAR_PATH"
fi

# --- SIMPLEAUTH BUILD ---
PLUGIN_SRC="$ROOT_DIR/plugins/SimpleAuth"
PLUGIN_OUT="$PLUGIN_SRC/out"
PLUGIN_JAR="$SERVER_DIR/plugins/SimpleAuth.jar"
PAPER_API="$ROOT_DIR/plugins/paper-api-1.21.8-R0.1-20250818.022717-36.jar"
ADVENTURE_JAR="$ROOT_DIR/plugins/adventure-api-4.17.0.jar"
ADVENTURE_KEY_JAR="$ROOT_DIR/plugins/adventure-key-4.17.0.jar"
ANOTATIONS_JAR="$ROOT_DIR/plugins/annotations-23.0.0.jar"
BUNGEE_JAR="$ROOT_DIR/plugins/BungeeCord.jar"
GUAVA_JAR="$ROOT_DIR/plugins/guava-33.5.0-jre.jar"

echo "=== Building SimpleAuth Plugin ==="

if [ -d "$PLUGIN_SRC/src" ]; then
  mkdir -p "$PLUGIN_OUT"
  # Compile
  javac -cp "$PAPER_API:$ADVENTURE_JAR:$ADVENTURE_KEY_JAR:$BUNGEE_JAR:$GUAVA_JAR:$ANOTATIONS_JAR:$JAR_PATH" \
  -d "$PLUGIN_OUT" "$PLUGIN_SRC/src/com"/*.java
  
  # Copy plugin.yml
  cp "$PLUGIN_SRC/plugin.yml" "$PLUGIN_OUT/"

  # Copy config.yml
  if [ -f "$PLUGIN_SRC/config.yml" ]; then
    cp "$PLUGIN_SRC/config.yml" "$PLUGIN_OUT/"
  else
    echo "⚠️ config.yml not found in $PLUGIN_SRC, plugin will fail on startup"
  fi
  
  # Copy users.yml
  if [ -f "$PLUGIN_SRC/users.yml" ]; then
    cp "$PLUGIN_SRC/users.yml" "$PLUGIN_OUT/"
  else
    echo "⚠️ users.yml not found in $PLUGIN_SRC, plugin will fail on startup"
  fi

  # Package
  jar cf "$PLUGIN_JAR" -C "$PLUGIN_OUT" .
  echo "✅ SimpleAuth compiled successfully: $PLUGIN_JAR"
  rm -rf "$PLUGIN_OUT"
else
  echo "⚠️  SimpleAuth source folder not found at $PLUGIN_SRC/src"
fi

# --- FIRST START (to generate configs) ---
cd "$SERVER_DIR"
echo "Starting Paper server once to generate configs..."
java -Xmx1G -Xms1G -jar paper.jar nogui || true

# --- EULA AUTO-ACCEPT ---
EULA_FILE="$SERVER_DIR/eula.txt"
echo "eula=true" > "$EULA_FILE"
echo "EULA accepted."

# --- CONFIGURE ONLINE-MODE, SEED & PORT ---
PROPERTIES_FILE="$SERVER_DIR/server.properties"
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"

if [ -f "$PROPERTIES_FILE" ]; then
  echo "Updating server.properties..."

  # Disable online mode
  if [[ "$OS" == *"mingw"* ]] || [[ "$OS" == *"msys"* ]]; then
    sed -i 's/online-mode=true/online-mode=false/' "$PROPERTIES_FILE"
  else
    sed -i.bak 's/online-mode=true/online-mode=false/' "$PROPERTIES_FILE"
  fi
  echo "Online-mode disabled."

  # Set world seed
  SEED_VALUE="YOUR_SEED_HERE"
  if grep -q "^level-seed=" "$PROPERTIES_FILE"; then
    if [[ "$OS" == *"mingw"* ]] || [[ "$OS" == *"msys"* ]]; then
      sed -i "s/^level-seed=.*/level-seed=$SEED_VALUE/" "$PROPERTIES_FILE"
    else
      sed -i.bak "s/^level-seed=.*/level-seed=$SEED_VALUE/" "$PROPERTIES_FILE"
    fi
    echo "Updated existing level-seed."
  else
    echo "level-seed=$SEED_VALUE" >> "$PROPERTIES_FILE"
    echo "Added new level-seed."
  fi

  # Set server port
  SERVER_PORT="24111"
  if grep -q "^server-port=" "$PROPERTIES_FILE"; then
    if [[ "$OS" == *"mingw"* ]] || [[ "$OS" == *"msys"* ]]; then
      sed -i "s/^server-port=.*/server-port=$SERVER_PORT/" "$PROPERTIES_FILE"
    else
      sed -i.bak "s/^server-port=.*/server-port=$SERVER_PORT/" "$PROPERTIES_FILE"
    fi
    echo "Updated existing server-port."
  else
    echo "server-port=$SERVER_PORT" >> "$PROPERTIES_FILE"
    echo "Added new server-port."
  fi

else
  echo "Warning: server.properties not found, skipping online-mode, seed, and port configuration."
fi

echo "=== Setup complete. ==="
