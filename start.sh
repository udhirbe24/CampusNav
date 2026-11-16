#!/usr/bin/env bash
# CampusNav one-click starter for macOS / Linux.
# Needs Java 17 or newer. Maven is downloaded automatically by ./mvnw the first time.
set -e
cd "$(dirname "$0")/backend"

# Use Homebrew's Java 21 if no Java is on the PATH (common on Macs)
if ! java -version >/dev/null 2>&1; then
  for candidate in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21 /opt/homebrew/opt/openjdk /usr/local/opt/openjdk; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      break
    fi
  done
fi
if ! java -version >/dev/null 2>&1; then
  echo "[ERROR] Java was not found. Install it with:  brew install openjdk@21"
  exit 1
fi

PORT="${PORT:-8088}"
echo "====================================================================="
echo "   CampusNav - Smart Campus Navigation System (Java / Spring Boot)"
echo "   Thapar Institute of Engineering and Technology"
echo "====================================================================="
echo "   Navigation map:  http://localhost:$PORT/app"
echo "   Admin portal:    http://localhost:$PORT/admin"
echo "   Press Ctrl+C to stop."
echo "====================================================================="

# Open the browser once the server answers
( for _ in $(seq 1 60); do
    if curl -sf "http://localhost:$PORT/api/health" >/dev/null 2>&1; then
      (open "http://localhost:$PORT/app" || xdg-open "http://localhost:$PORT/app") >/dev/null 2>&1
      break
    fi
    sleep 1
  done ) &

PORT="$PORT" ./mvnw -q spring-boot:run
