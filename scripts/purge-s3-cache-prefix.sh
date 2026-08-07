#!/usr/bin/env bash

set -euo pipefail

usage() {
  echo "Usage: $0 [--apply] <bucket> <cache-namespace>" >&2
  echo "Without --apply, this command only shows the objects that would be deleted." >&2
  exit 2
}

apply=false
if [[ ${1:-} == "--apply" ]]; then
  apply=true
  shift
fi

[[ $# -eq 2 ]] || usage

bucket=$1
namespace=$2

if [[ -z "$bucket" || "$bucket" == */* || "$bucket" == *..* ]]; then
  echo "Refusing unsafe bucket name: $bucket" >&2
  exit 2
fi

if [[ -z "$namespace" || "$namespace" == /* || "$namespace" == */ || "$namespace" == *"//"* ]]; then
  echo "Refusing unsafe cache namespace: $namespace" >&2
  exit 2
fi

IFS='/' read -r -a segments <<< "$namespace"
for segment in "${segments[@]}"; do
  if [[ -z "$segment" || "$segment" == "." || "$segment" == ".." ]]; then
    echo "Refusing unsafe cache namespace: $namespace" >&2
    exit 2
  fi
done

target="s3://$bucket/$namespace"
if [[ "$apply" == true ]]; then
  echo "Deleting only cache objects under $target"
  aws s3 rm "$target" --recursive
else
  echo "Dry run: no objects will be deleted under $target"
  echo "Re-run with --apply only after verifying these candidates."
  aws s3 rm "$target" --recursive --dryrun
fi
