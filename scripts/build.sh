#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

usage() {
    cat <<'USAGE'
Usage: scripts/build.sh [options] [-- <maven-args>...]

Build the full Maven reactor and produce the distribution tar.gz.

Build id resolution:
  1. --build-id ID
  2. --commit (git commit count)
  3. ARBITER_BUILD_ID
  4. BUILD_NUMBER / GITHUB_RUN_NUMBER / CI_PIPELINE_IID / CIRCLE_BUILD_NUM
  5. git commit count
  6. pom.xml <build.id> (fallback)

Options:
  --build-id ID   override the build id
  --commit        use the git commit count
  -h, --help      show this help

Environment:
  ARBITER_MAVEN_REPO  optional Maven local repository path
  MVN                  Maven executable, default: mvn

Examples:
  scripts/build.sh
  scripts/build.sh --build-id 1102
  scripts/build.sh --commit
  scripts/build.sh -- -DskipTests
USAGE
}

property() {
    sed -n "s:.*<$1>\\(.*\\)</$1>.*:\\1:p" pom.xml | head -n 1
}

git_count() {
    command -v git >/dev/null 2>&1 \
        && git rev-list --count HEAD 2>/dev/null
}

build_id=
force_commit=0
extra=()

while (($#)); do
    case "$1" in
        --build-id=*)
            build_id=${1#--build-id=}
            shift
            ;;
        --build-id)
            if (($# < 2)); then
                echo "build: --build-id requires a value" >&2
                exit 2
            fi
            build_id=$2
            shift 2
            ;;
        --commit)
            force_commit=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --)
            shift
            extra+=("$@")
            break
            ;;
        *)
            extra+=("$1")
            shift
            ;;
    esac
done

if [[ -z "$build_id" ]]; then
    if ((force_commit == 1)); then
        build_id=$(git_count || true)
    else
        build_id=${ARBITER_BUILD_ID:-}
        if [[ -z "$build_id" ]]; then
            for name in BUILD_NUMBER GITHUB_RUN_NUMBER CI_PIPELINE_IID CIRCLE_BUILD_NUM; do
                if [[ -n "${!name:-}" ]]; then
                    build_id=${!name}
                    break
                fi
            done
        fi
        if [[ -z "$build_id" ]]; then
            build_id=$(git_count || true)
        fi
    fi
fi
if [[ -z "$build_id" ]]; then
    build_id=$(property build.id)
fi
if [[ -z "$build_id" ]]; then
    echo "build: build.id is missing from pom.xml" >&2
    exit 2
fi

major=$(property version.major)
minor=$(property version.minor)
patch=$(property version.patch)
base_version="${major:-1}.${minor:-0}.${patch:-0}"
display_version="$build_id.$base_version"

maven=("${MVN:-mvn}")
if [[ -n "${ARBITER_MAVEN_REPO:-}" ]]; then
    maven+=("-Dmaven.repo.local=$ARBITER_MAVEN_REPO")
fi

echo "build: arbiter version $display_version"
maven_args=("-Dbuild.id=$build_id" clean verify)
if [[ ${#extra[@]} -gt 0 ]]; then
    maven_args+=("${extra[@]}")
fi
"${maven[@]}" "${maven_args[@]}"
