#!/usr/bin/env zsh
# Helper to build and run AtomKV from the project root.
# Usage: ./scripts/atomkv.sh

# Resolve project root (script lives in scripts/)
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SCRIPT_DIR" || exit 1

# Ensure JDK (optional): adjust JAVA_HOME if needed
export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null)}
if [[ -n "$JAVA_HOME" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Build jar (skip tests by default)
mvn -DskipTests package || { echo "Build failed"; exit 1; }

# Ensure AOF directory exists
mkdir -p "$HOME/.atomkv"

# Run the produced jar (artifactId/version per pom.xml)
JAR="target/atomkv-1.0.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Jar not found: $JAR"
  echo "Artifacts in target/:" 
  ls -1 target | sed -n '1,200p'
  exit 1
fi

echo "Starting AtomKV from $JAR"
java -jar "$JAR"