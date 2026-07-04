#!/bin/bash
# 启动 Minecraft 1.21.1 强化红石模组
cd "$(dirname "$0")"
export JAVA_HOME="C:/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
echo "启动 Minecraft 1.21.1 - 强化红石模组..."
./gradlew runClient
