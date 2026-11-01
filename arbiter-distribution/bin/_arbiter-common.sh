# Shared launcher bootstrap for the Arbiter distribution.
#
# This file is sourced by bin/arbiter, bin/server-start, and bin/server-stop; it
# is never executed directly. The sourcing script must set ARBITER_TOOL (the
# message prefix) and SCRIPT_DIR (its real directory) before sourcing, and must
# call arbiter_resolve_home before anything else.

arbiter_fail() {
  printf '%s: %s\n' "$ARBITER_TOOL" "$*" >&2
  exit 2
}

# Validate the explicit ARBITER_HOME (if any) or derive the root from the
# script location, then canonicalize it.
arbiter_resolve_home() {
  if [ -n "${ARBITER_HOME:-}" ] && [ ! -d "$ARBITER_HOME" ]; then
    arbiter_fail "ARBITER_HOME does not exist: $ARBITER_HOME"
  fi
  if ! ARBITER_HOME=$(CDPATH= cd -- "${ARBITER_HOME:-$SCRIPT_DIR/..}" && pwd); then
    arbiter_fail "cannot resolve the distribution root"
  fi
  if [ ! -d "$ARBITER_HOME/lib" ]; then
    arbiter_fail "invalid distribution root (lib not found): $ARBITER_HOME"
  fi
}

arbiter_require_file() {
  if [ ! -f "$1" ]; then
    arbiter_fail "required file not found: $1"
  fi
}

# Resolve the JVM (explicit override, then JAVA_HOME, then PATH).
arbiter_find_java() {
  if [ -n "${1:-}" ]; then
    JAVA_EXE=$1
  elif [ -n "${JAVA_HOME:-}" ]; then
    JAVA_EXE=$JAVA_HOME/bin/java
  else
    JAVA_EXE=java
  fi

  case $JAVA_EXE in
    */*)
      if [ ! -x "$JAVA_EXE" ]; then
        arbiter_fail "Java executable not found: $JAVA_EXE"
      fi
      ;;
    *)
      if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
        arbiter_fail "Java executable not found: $JAVA_EXE"
      fi
      ;;
  esac
}

# Resolve the JVM and require Java 17 or newer.
arbiter_select_java() {
  arbiter_find_java "${1:-}"

  # `java -version` prints the version as the second double-quoted field:
  #   1.8.0_451, 9.0.4, 10.0.1, 17.0.15, 21-ea, ...
  # Java 8 and earlier use the "1.x" form; 9+ use the first segment.
  JAVA_VERSION=$("$JAVA_EXE" -version 2>&1 \
    | awk -F'"' '/ version "/ { print $2; exit }')
  case $JAVA_VERSION in
    1.*) JAVA_MAJOR=8 ;;
    *)
      JAVA_MAJOR=${JAVA_VERSION%%.*}
      JAVA_MAJOR=${JAVA_MAJOR%%-*}
      ;;
  esac
  case ${JAVA_MAJOR:-} in
    ''|*[!0-9]*)
      arbiter_fail "cannot determine the Java version of $JAVA_EXE"
      ;;
  esac
  if [ "$JAVA_MAJOR" -lt 17 ]; then
    arbiter_fail "Java 17 or newer is required (found '${JAVA_VERSION:-unknown}')"
  fi
}

# Reads one JVM option per line; blank lines and lines whose first non-space
# character is '#' are ignored. CRLF line endings are tolerated.
arbiter_read_jvm_options() {
  ARBITER_JVM_FILE_OPTS=
  if [ ! -f "$1" ]; then
    return 0
  fi
  ARBITER_JVM_FILE_OPTS=$(awk '
    { sub(/\r$/, ""); sub(/^[[:space:]]+/, ""); sub(/[[:space:]]+$/, "") }
    /^[[:space:]]*$/ { next }
    /^[[:space:]]*#/ { next }
    { print }
  ' "$1")
}

# True when any argument asks for help or version output.
arbiter_has_flag() {
  for arbiter_arg in "$@"; do
    case $arbiter_arg in
      --help|-h|--version|-V|--check) return 0 ;;
    esac
  done
  return 1
}

# Append one classpath entry (a JAR or a directory) to ARBITER_CLASSPATH.
arbiter_append_entry() {
  if [ -z "${ARBITER_CLASSPATH:-}" ]; then
    ARBITER_CLASSPATH=$1
  else
    ARBITER_CLASSPATH=$ARBITER_CLASSPATH:$1
  fi
}

# Append every JAR in one directory to ARBITER_CLASSPATH, skipping the main JAR
# so a CLI installation never lists its launcher twice. Never recurses.
arbiter_append_jars() {
  [ -d "$1" ] || return 0
  for jar in "$1"/*.jar; do
    [ -f "$jar" ] || continue
    if [ -n "${ARBITER_MAIN_JAR:-}" ] && [ "$jar" = "$ARBITER_MAIN_JAR" ]; then
      continue
    fi
    arbiter_append_entry "$jar"
  done
}

# Add a directory's JARs and the directory itself, which becomes a resource
# root for loose files. Nested directories are never scanned.
arbiter_append_jars_with_root() {
  arbiter_append_jars "$1"
  arbiter_append_entry "$1"
}

