#!/usr/bin/env bash
# Pushes ./logs (and test reports) to an orphan branch so the build output can be read with plain git.
set -u
branch="$1"
mkdir -p logs
cp -r core/build/test-results logs/ 2>/dev/null || true
echo "run ${GITHUB_RUN_NUMBER} sha ${GITHUB_SHA} job ${GITHUB_JOB}" > logs/RUN.txt
cd logs
git init -q
git checkout -q -b "$branch"
git add -A
git -c user.name="ci" -c user.email="ci@users.noreply.github.com" commit -q -m "Logs for run ${GITHUB_RUN_NUMBER}"
git push -q -f "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$branch"
