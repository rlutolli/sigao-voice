---
description: Pre-push validation checklist to prevent CI failures
---

# Pre-Push Validation Workflow

Before pushing to `main`, always run these local checks:

## 1. Lint and Format Checks
```bash
# Flutter/Dart
flutter analyze
dart format --set-exit-if-changed .

# Python (if applicable)
python -m py_compile simulation/*.py tests/*.py
```

## 2. Build Verification
```bash
# Android APK Build
cd /home/rlutolli/Desktop/uni/INDIVIDUAL_PROJECT/sigao-voice
flutter build apk --debug

# C++ Core Build (Host)
cd sigao_core
mkdir -p build_host && cd build_host
cmake .. && make
```

## 3. Git Health Checks
```bash
# Check for broken submodule references
git submodule status

# Check for embedded git repos (should return nothing)
find . -name ".git" -type d -not -path "./.git"

# Check for large files that shouldn't be committed
find . -type f -size +10M -not -path "./.git/*" -not -path "./sigao_core/external/*"
```

## 4. Workflow Validation
```bash
# Validate GitHub Actions YAML syntax
# turbo
cat .github/workflows/*.yml | head -1
```

## Quick Pre-Push Command
```bash
# turbo
git status && flutter analyze --no-fatal-infos && git submodule status 2>&1 | grep -v "^$"
```

If all checks pass, you are safe to push.
