#!/usr/bin/env bash

# Benchmarks the cache warmer against the Blastradius Maven reactor without modifying a checkout
# supplied by the user. A result is deliberately inconclusive unless the warm build log contains
# a cache-warmer restore event, because elapsed time alone cannot prove that the warmer helped.
set -euo pipefail

readonly DEFAULT_SOURCE="https://github.com/baokhang83/blastradius.git"
readonly CACHE_WARMER_GROUP="io.github.baokhang83.blastradius"
readonly CACHE_WARMER_ARTIFACT="blastradius-cache-warmer-maven-extension"
readonly CACHE_WARMER_VERSION="0.1.0-SNAPSHOT"

source_repository="$DEFAULT_SOURCE"
reference="origin/main"
runs=3
output_directory=""
maven_arguments=(-B -Pself-host-blastradius clean verify)

usage() {
    cat <<'USAGE'
Usage: scripts/benchmark-blastradius.sh [options] [-- <Maven arguments>]

Compare equivalent cold and cache-warmer-enabled Maven builds of Blastradius. The default source
is https://github.com/baokhang83/blastradius.git and the default Maven invocation is:

  mvn -B -Pself-host-blastradius clean verify

Options:
  --source <path-or-url>  Blastradius checkout or Git URL
  --ref <git-ref>         Revision to benchmark (default: origin/main)
  --runs <count>          Number of cold and warm trials (default: 3)
  --output <directory>    Directory for logs and results (default: a temporary directory)
  --help                  Show this message

Arguments after -- replace the default Maven arguments. For example:

  scripts/benchmark-blastradius.sh --runs 5 -- -B -Pself-host-blastradius verify

The script exits 0 only when every build succeeds and every warm trial contains a cache-warmer
restore event. Exit 2 means the measurement is inconclusive and the report explains why.
USAGE
}

fail() {
    printf 'benchmark: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

copy_directory() {
    local source=$1
    local destination=$2
    mkdir -p "$destination"
    cp -R "$source"/. "$destination"
}

median() {
    sort -n | awk '
        { values[NR] = $1 }
        END {
            if (NR == 0) {
                exit 1
            }
            if (NR % 2 == 1) {
                printf "%.3f", values[(NR + 1) / 2]
            } else {
                printf "%.3f", (values[NR / 2] + values[(NR / 2) + 1]) / 2
            }
        }'
}

write_extension_descriptor() {
    local checkout=$1
    mkdir -p "$checkout/.mvn"
    cat > "$checkout/.mvn/extensions.xml" <<XML
<extensions>
  <extension>
    <groupId>${CACHE_WARMER_GROUP}</groupId>
    <artifactId>${CACHE_WARMER_ARTIFACT}</artifactId>
    <version>${CACHE_WARMER_VERSION}</version>
  </extension>
</extensions>
XML
}

install_extension() {
    local local_repository=$1
    local script_directory
    script_directory=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
    local cache_warmer_root
    cache_warmer_root=$(cd "$script_directory/.." && pwd)

    (
        cd "$cache_warmer_root"
        mvn -B --no-transfer-progress -DskipTests -Dmaven.repo.local="$local_repository" install
    )
}

bootstrap_blastradius_plugin() {
    local checkout=$1
    local local_repository=$2
    local release_version
    release_version=$(cd "$checkout" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)
    local self_host_version="${release_version}-selfhost"
    local plugin_jar="blastradius-maven-plugin/target/blastradius-maven-plugin-${release_version}.jar"
    export SELF_HOST_VERSION="$self_host_version"

    (
        cd "$checkout"
        mvn -B --no-transfer-progress -DskipTests -Dinvoker.skip=true \
            -Dmaven.repo.local="$local_repository" -pl blastradius-maven-plugin -am package
        mkdir -p target/self-host/META-INF/maven
        cp "$plugin_jar" "target/blastradius-maven-plugin-${self_host_version}.jar"
        unzip -p "$plugin_jar" META-INF/maven/plugin.xml \
            | perl -0pe 's{(<artifactId>blastradius-maven-plugin</artifactId>\s*<version>)[^<]+(</version>)}{$1 . $ENV{SELF_HOST_VERSION} . $2}e' \
            > target/self-host/META-INF/maven/plugin.xml
        (cd target/self-host && jar uf "../blastradius-maven-plugin-${self_host_version}.jar" META-INF/maven/plugin.xml)
        mvn -B --no-transfer-progress -Dmaven.repo.local="$local_repository" \
            org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file \
            -Dfile="target/blastradius-maven-plugin-${self_host_version}.jar" \
            -DgroupId=io.github.baokhang83.blastradius \
            -DartifactId=blastradius-maven-plugin \
            -Dversion="$self_host_version" \
            -Dpackaging=maven-plugin \
            -DgeneratePom=true
    )
}

run_trial() {
    local mode=$1
    local trial=$2
    local checkout=$3
    local local_repository=$4
    local log_file=$5
    local time_file=$6

    if [[ "$mode" == "warm" ]]; then
        write_extension_descriptor "$checkout"
        install_extension "$local_repository"
    fi

    set +e
    (
        cd "$checkout"
        MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=$local_repository" \
            /usr/bin/time -p -o "$time_file" mvn -Dmaven.repo.local="$local_repository" "${maven_arguments[@]}"
    ) > "$log_file" 2>&1
    local exit_code=$?
    set -e

    local seconds
    seconds=$(awk '$1 == "real" { print $2 }' "$time_file")
    [[ -n "$seconds" ]] || seconds="NA"

    local evidence="none"
    if grep -Eq '\[cache-warmer\].*(restored|RESTORED)' "$log_file"; then
        evidence="restored"
    elif grep -Eq '\[cache-warmer\]' "$log_file"; then
        evidence="no-restore"
    fi

    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$mode" "$trial" "$seconds" "$exit_code" "$evidence" "$log_file"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --source)
            source_repository=${2:?--source requires a value}
            shift 2
            ;;
        --ref)
            reference=${2:?--ref requires a value}
            shift 2
            ;;
        --runs)
            runs=${2:?--runs requires a value}
            shift 2
            ;;
        --output)
            output_directory=${2:?--output requires a value}
            shift 2
            ;;
        --help)
            usage
            exit 0
            ;;
        --)
            shift
            [[ $# -gt 0 ]] || fail "-- requires Maven arguments"
            maven_arguments=("$@")
            break
            ;;
        *)
            fail "unknown option: $1"
            ;;
    esac
done

[[ "$runs" =~ ^[1-9][0-9]*$ ]] || fail "--runs must be a positive integer"
require_command git
require_command mvn
require_command /usr/bin/time
require_command unzip
require_command perl
require_command jar

if [[ -z "$output_directory" ]]; then
    output_directory=$(mktemp -d "${TMPDIR:-/tmp}/blastradius-cache-warmer-benchmark.XXXXXX")
else
    mkdir -p "$output_directory"
fi
output_directory=$(cd "$output_directory" && pwd)

readonly repository="$output_directory/repository"
readonly worktrees="$output_directory/worktrees"
readonly repository_template="$output_directory/m2-template"
readonly results="$output_directory/results.tsv"

printf 'Benchmark output: %s\n' "$output_directory"
git clone --quiet --no-checkout "$source_repository" "$repository"
readonly commit=$(git -C "$repository" rev-parse "$reference")
printf 'Blastradius revision: %s\n' "$commit"

mkdir -p "$worktrees" "$repository_template"
git -C "$repository" worktree add --quiet --detach "$worktrees/prepare" "$commit"
bootstrap_blastradius_plugin "$worktrees/prepare" "$repository_template"
(
    cd "$worktrees/prepare"
    mvn -Dmaven.repo.local="$repository_template" "${maven_arguments[@]}"
) > "$output_directory/prepare.log" 2>&1 || fail "preparation build failed: $output_directory/prepare.log"
git -C "$repository" worktree remove --force "$worktrees/prepare"

printf 'mode\ttrial\tseconds\texit_code\twarm_evidence\tlog\n' > "$results"
all_builds_passed=true
all_warm_runs_restored=true
for trial in $(seq 1 "$runs"); do
    for mode in cold warm; do
        checkout="$worktrees/$mode-$trial"
        local_repository="$worktrees/$mode-$trial-m2"
        log_file="$output_directory/$mode-$trial.log"
        time_file="$output_directory/$mode-$trial.time"
        git -C "$repository" worktree add --quiet --detach "$checkout" "$commit"
        copy_directory "$repository_template" "$local_repository"
        trial_result=$(run_trial "$mode" "$trial" "$checkout" "$local_repository" "$log_file" "$time_file")
        printf '%s\n' "$trial_result" >> "$results"
        IFS=$'\t' read -r _ _ _ exit_code evidence _ <<< "$trial_result"
        [[ "$exit_code" == "0" ]] || all_builds_passed=false
        if [[ "$mode" == "warm" && "$evidence" != "restored" ]]; then
            all_warm_runs_restored=false
        fi
        git -C "$repository" worktree remove --force "$checkout"
        rm -rf "$local_repository"
    done
done

cold_median=$(awk -F '\t' '$1 == "cold" && $4 == 0 && $3 != "NA" { print $3 }' "$results" | median || true)
warm_median=$(awk -F '\t' '$1 == "warm" && $4 == 0 && $3 != "NA" { print $3 }' "$results" | median || true)
printf 'Cold median: %s seconds\n' "${cold_median:-NA}"
printf 'Warm median: %s seconds\n' "${warm_median:-NA}"
if [[ -n "${cold_median:-}" && -n "${warm_median:-}" ]]; then
    awk -v cold="$cold_median" -v warm="$warm_median" 'BEGIN {
        printf "Median delta: %.3f seconds (%.1f%%)\n", cold - warm, ((cold - warm) / cold) * 100
    }'
fi

if [[ "$all_builds_passed" != true ]]; then
    printf 'Result: INCONCLUSIVE (one or more Maven builds failed)\n' >&2
    exit 2
fi
if [[ "$all_warm_runs_restored" != true ]]; then
    printf 'Result: INCONCLUSIVE (no cache-warmer restore evidence in one or more warm logs)\n' >&2
    exit 2
fi
printf 'Result: MEASURED (all warm runs contained restore evidence)\n'
