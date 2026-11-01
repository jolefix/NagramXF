#!/usr/bin/env bash
# Run from the repository; no worktree, resets, force pushes or automatic conflict resolution.
set -euo pipefail
base_ref=${1:?Usage: bash Tools/asr/port-update.sh BASE_REF asr/NEW_VERSION}
branch=${2:?Usage: bash Tools/asr/port-update.sh BASE_REF asr/NEW_VERSION}
cd "$(git rev-parse --show-toplevel)"
[[ "$branch" == asr/* ]] || { echo 'Branch must start with asr/'; exit 1; }
git check-ref-format --branch "$branch" >/dev/null
[[ -z "$(git status --porcelain)" ]] || { echo 'Commit or stash local changes first.'; exit 1; }
if git show-ref --verify --quiet "refs/heads/$branch"; then echo 'Branch already exists.'; exit 1; fi
git fetch origin "$base_ref"
base=$(git rev-parse --verify 'FETCH_HEAD^{commit}')
git fetch origin feature/audio-transcriptions
source=$(git rev-parse --verify 'FETCH_HEAD^{commit}')
original_base=c31a2ba1ab1134ea1a423bb401899b53e5310bb6
git merge-base --is-ancestor "$original_base" "$source"
[[ "$(git rev-list --count --merges "$original_base..$source")" == 0 ]] || {
    echo 'Feature branch contains merges. Review the commit range manually.'; exit 1;
}
mapfile -t commits < <(git rev-list --reverse "$original_base..$source")
((${#commits[@]})) || { echo 'No ASR commits found.'; exit 1; }
git switch -c "$branch" "$base"
if ! git -c rerere.enabled=true cherry-pick "${commits[@]}"; then
    echo 'Stopped at a conflict or an already applied change.'
    echo 'Inspect git status; resolve and run git cherry-pick --continue, or --abort.'
    echo 'Use --skip only after verifying that the current change already exists.'
    exit 1
fi
printf '\nPort prepared on %s. Review the diff, then run:\n' "$branch"
printf 'git submodule update --init --recursive\n'
printf 'git push -u origin %q\n' "$branch"
printf 'The push starts the ASR ARM64 workflow. It does not create a public release.\n'
