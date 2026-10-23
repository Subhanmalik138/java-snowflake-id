#!/usr/bin/env bash
# Build script: compiles the project and (with "test") runs the JUnit suite.
set -euo pipefail

cd "$(dirname "$0")"

JUNIT_JAR="${JUNIT_JAR:-/tmp/javalibs/junit-standalone.jar}"

javac -d out/main $(find src/main/java -name '*.java')
echo "main compiled"

# Build an executable jar
jar --create --file out/snowflake-id.jar --main-class com.subhanmalik.snowflake.Main -C out/main .
echo "jar built: out/snowflake-id.jar"

if [ "${1:-}" = "test" ]; then
    if [ ! -f "$JUNIT_JAR" ]; then
        echo "JUnit jar not found at $JUNIT_JAR; downloading from Maven Central..."
        mkdir -p "$(dirname "$JUNIT_JAR")"
        curl -sSL -o "$JUNIT_JAR" \
            "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
    fi
    javac -cp "out/main:$JUNIT_JAR" -d out/test $(find src/test/java -name '*.java')
    echo "tests compiled"
    java -jar "$JUNIT_JAR" execute -cp "out/main:out/test" --scan-classpath=out/test
fi
