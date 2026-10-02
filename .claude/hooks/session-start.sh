#!/bin/bash
# Warms the Gradle cache in Claude Code cloud sessions: downloads Gradle, Minecraft and all mod
# dependencies and compiles every source set, so ./gradlew build works right away.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

# Maven Central sometimes answers 429 on a cold cache; Gradle keeps what it already downloaded, so retry.
for attempt in 1 2 3 4; do
  if ./gradlew --no-daemon --console=plain compileJava compileClientJava compileGametestJava; then
    exit 0
  fi
  echo "Gradle warm-up attempt $attempt failed, retrying" >&2
  sleep $((attempt * 15))
done
exit 1
