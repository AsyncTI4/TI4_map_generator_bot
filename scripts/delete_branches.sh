#!/bin/bash

# ==============================================================================
# Script: cleanup_branches.sh
# Description: Deletes remote git branches that haven't seen a commit in 1+ years.
# Usage: ./cleanup_branches.sh [-delete] [remote_name]
# ==============================================================================

# Default settings
PERFORM_DELETE=false
REMOTE="origin"
ONE_YEAR_AGO=$(date -d "1 year ago" +%s)

# Check for flags
for arg in "$@"; do
    if [[ "$arg" == "-delete" ]]; then
        PERFORM_DELETE=true
    else
        # If it's not a flag, assume it's the remote name (e.g., upstream)
        REMOTE="$arg"
    fi
done

echo "------------------------------------------------------------"
echo "Target Remote: $REMOTE"
if [ "$PERFORM_DELETE" = false ]; then
    echo "Mode: PREVIEW (No branches will be deleted. Use -delete to execute)"
else
    echo "Mode: LIVE DELETE (Branches WILL be removed from remote)"
fi
echo "------------------------------------------------------------"

# Fetch latest state from remote and prune local tracking references
git fetch "$REMOTE" --prune > /dev/null 2>&1

# Get list of all remote branches for the specified remote
# Format: <authordate_unix> <branch_name>
branches=$(git for-each-ref --sort=authordate --format='%(authordate:unix) %(refname:short)' "refs/remotes/$REMOTE")

count=0

while read -r last_commit_unix full_branch_name; do
    # Skip if the branch name is empty (end of list)
    [ -z "$full_branch_name" ] && continue

    # Extract the branch name (removing the remote prefix, e.g., 'origin/')
    branch_name=${full_branch_name#$REMOTE/}

    # Protection: Never delete 'main', 'master', or 'develop'
    if [[ "$branch_name" =~ ^(main|master|develop)$ ]]; then
        continue
    fi

    # Check if the last commit was more than a year ago
    if [ "$last_commit_unix" -lt "$ONE_YEAR_AGO" ]; then
        last_date=$(date -d "@$last_commit_unix" "+%Y-%m-%d")

        if [ "$PERFORM_DELETE" = false ]; then
            echo "[STALE] Preview: $branch_name (Last commit: $last_date)"
        else
            echo "[DELETING] $branch_name (Last commit: $last_date)..."
            git push "$REMOTE" --delete "$branch_name"
        fi
        ((count++))
    fi
done <<< "$branches"

echo "------------------------------------------------------------"
if [ "$PERFORM_DELETE" = false ]; then
    echo "Finished. Total stale branches identified: $count"
    echo "Run with -delete to remove them."
else
    echo "Finished. Total branches deleted: $count"
fi