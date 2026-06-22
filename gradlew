#!/bin/sh
APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
PRG="$0"
while [ -h "$PRG" ] ; do ls=`ls -ld "$PRG"`; link=`expr "$ls" : '.*-> \(.*\)$'`; PRG=`dirname "$PRG"`/"$link"; done
SAVED="`pwd`"; cd "`dirname \"$PRG\"`/" >/dev/null; APP_HOME="`pwd -P`"; cd "$SAVED" >/dev/null
exec java $JAVA_OPTS $GRADLE_OPTS -Dorg.gradle.appname="$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
