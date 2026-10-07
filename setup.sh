#!/usr/bin/env bash
#
# Renames this template into a new app.
#
#   ./setup.sh com.yourcompany.myapp "My App Name"
#
# Rewrites namespace, applicationId and the benchmark package, moves the source
# directories to match, and updates the launcher label. Run it once, right after
# cloning, before you write any code.
#
set -euo pipefail

OLD_PACKAGE="com.example.template"
NEW_PACKAGE="${1:-}"
NEW_LABEL="${2:-}"

if [[ -z "$NEW_PACKAGE" ]]; then
  echo "usage: $0 <package.name> [app label]" >&2
  echo "  e.g. $0 com.yourcompany.myapp \"My App Name\"" >&2
  exit 1
fi

if [[ ! "$NEW_PACKAGE" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "error: '$NEW_PACKAGE' is not a valid Android package name" >&2
  echo "       expected something like com.yourcompany.myapp" >&2
  exit 1
fi

# ApplicationId cannot carry Kotlin keywords.
if grep -qE "(^|\.)${NEW_PACKAGE##*.}(in|is|as|fun|for|object|class|when|if)$" /dev/null; then
  echo "error: last segment looks like a Kotlin keyword" >&2
  exit 1
fi

cd "$(dirname "$0")"

echo "Renaming $OLD_PACKAGE -> $NEW_PACKAGE"
echo "Label        -> ${NEW_LABEL:-$NEW_PACKAGE}"
echo

# 1. Move source directories to mirror the new package.
move_package() {
  local module="$1"
  local from="$module/src"
  local to_rel="$NEW_PACKAGE"

  # Find every source set that has the old package directory.
  while IFS= read -r dir; do
    local target="$dir/$to_rel"
    mkdir -p "$(dirname "$target")"
    mkdir -p "$target"
    git mv "$dir/$OLD_PACKAGE" "$target" 2>/dev/null \
      || mv "$dir/$OLD_PACKAGE" "$target"
    # git mv leaves an empty parent chain behind when the old package was nested.
    rmdir -p --ignore-fail-on-non-empty "$(dirname "$dir/$OLD_PACKAGE")" 2>/dev/null || true
    echo "  moved $dir/$OLD_PACKAGE -> $target"
  done < <(find "$from" -type d -path "*/$OLD_PACKAGE" 2>/dev/null)
}

move_package app
move_package benchmark

# 2. Rewrite the package name in every tracked text file.
echo
echo "Rewriting references"
while IFS= read -r file; do
  # Skip binaries; sed would corrupt them and none of them hold our strings.
  if grep -Iq . "$file" 2>/dev/null && grep -q "$OLD_PACKAGE" "$file" 2>/dev/null; then
    sed -i "s/$OLD_PACKAGE/$NEW_PACKAGE/g" "$file"
    echo "  $file"
  fi
done < <(find . -type f \
  -not -path "./.git/*" \
  -not -path "*/build/*" \
  -not -path "./.gradle/*" \
  -not -name "setup.sh" \
  -not -name "*.jar" -not -name "*.apk" -not -name "*.aab" -not -name "*.png")

# 3. The launcher label.
if [[ -n "$NEW_LABEL" ]]; then
  label_file="app/src/main/res/values/strings.xml"
  if [[ -f "$label_file" ]]; then
    # Escape XML entities so a label like "Tom & Jerry" cannot break the file.
    safe_label="${NEW_LABEL//&/&amp;}"
    safe_label="${safe_label//\"/&quot;}"
    sed -i "s|<string name=\"app_name\">.*</string>|<string name=\"app_name\">${safe_label}</string>|" \
      "$label_file"
    echo
    echo "Label set in $label_file"
  fi
fi

# 4. rootProject.name follows the last package segment.
segment="${NEW_PACKAGE##*.}"
# Gradle project names may not contain a dash.
segment="${segment//-/_}"
sed -i "s|rootProject.name = .*|rootProject.name = \"$segment\"|" settings.gradle.kts

cat <<EOF

Done. Next:

  ./gradlew :app:assembleDebug

Then drop the template's history if you want a clean repo:

  rm -rf .git && git init && git add -A && git commit -m "Initial commit"

Note: local.properties is gitignored. If the SDK cannot be found, create it:

  echo "sdk.dir=\$ANDROID_HOME" > local.properties
EOF