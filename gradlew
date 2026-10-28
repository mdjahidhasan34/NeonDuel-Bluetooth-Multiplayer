#!/bin/sh
set -e
cd "$(dirname "$0")"
if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then
  echo 'Missing Gradle wrapper. Download official v8.7.0 gradle-wrapper.jar to gradle/wrapper/ first.' >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ]; then
  exec "$JAVA_HOME/bin/java" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@"
fi
exec java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@"
