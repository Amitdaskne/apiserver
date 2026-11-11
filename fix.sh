#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "==> Current branch:"
git branch --show-current

echo "==> Staging workflow file..."
git add .github/workflows/build.yml

echo "==> Committing..."
git commit -m "ci: trigger on both main and master" || echo "(nothing to commit)"

echo "==> Pushing..."
git push

echo "==> Waiting 5s for GitHub to register push..."
sleep 5

echo "==> Manual workflow trigger (backup)..."
gh workflow run build.yml || true

echo "==> Waiting 3s..."
sleep 3

echo "==> Recent runs:"
gh run list --limit 5

echo ""
echo "==> Watching latest run (Ctrl+C to exit):"
gh run watch || echo "(no run to watch yet — check GitHub Actions tab)"
