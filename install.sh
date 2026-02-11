#!/bin/bash

APK="app/build/outputs/apk/debug/app-debug.apk"

echo "🔨 Building debug APK..."
./gradlew assembleDebug || exit 1

echo "📱 Installing fresh build..."
adb uninstall com.panda.sideshelf 2>/dev/null
adb install "$APK" || exit 1

echo "🚀 Launching app..."
adb shell monkey -p com.panda.sideshelf -c android.intent.category.LAUNCHER 1

echo "✅ Done."
