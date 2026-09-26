#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
wrapper_dir="$project_dir/gradle/wrapper"
wrapper_jar="$wrapper_dir/gradle-wrapper.jar"
if [ ! -f "$wrapper_jar" ]; then
    jar_url="https://services.gradle.org/distributions/gradle-8.9-wrapper.jar"
    tmp_jar="$wrapper_jar.tmp"
    trap 'rm -f "$tmp_jar"' EXIT HUP INT TERM
    curl -fsSL "$jar_url" -o "$tmp_jar"
    expected=$(curl -fsSL "$jar_url.sha256" | tr -d '[:space:]')
    if command -v shasum >/dev/null 2>&1; then
        actual=$(shasum -a 256 "$tmp_jar" | cut -d ' ' -f 1)
    else
        actual=$(sha256sum "$tmp_jar" | cut -d ' ' -f 1)
    fi
    [ "$expected" = "$actual" ] || { echo "Gradle wrapper checksum mismatch" >&2; exit 1; }
    mv "$tmp_jar" "$wrapper_jar"
    trap - EXIT HUP INT TERM
fi
exec java -classpath "$wrapper_jar" org.gradle.wrapper.GradleWrapperMain "$@"
