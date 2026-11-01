#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
repo=https://repo.maven.apache.org/maven2
for artifact in \
 com/google/code/gson/gson/2.13.2/gson-2.13.2.jar \
 com/squareup/okhttp3/okhttp-jvm/5.3.2/okhttp-jvm-5.3.2.jar \
 com/squareup/okio/okio-jvm/3.16.4/okio-jvm-3.16.4.jar \
 org/jetbrains/kotlin/kotlin-stdlib/2.2.21/kotlin-stdlib-2.2.21.jar; do
    curl --fail --silent --show-error --location "$repo/$artifact" -o "$tmp/${artifact##*/}"
done
javac -cp "$tmp/*" -d "$tmp/classes" \
 TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/OpenAiTranscriptionApi.java \
 Tools/asr/OpenAiTranscriptionApiTest.java
java -cp "$tmp/classes:$tmp/*" tw.nekomimi.nekogram.helpers.OpenAiTranscriptionApiTest
