#!/bin/sh
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
if [ -z "$DIRNAME" ]; then DIRNAME="."; fi
GRADLE_ROUTER=""
exec gradle "$@"
