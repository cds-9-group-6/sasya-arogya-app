#!/bin/bash

# Test script to verify SurfaceFlinger crash fixes
echo "🧪 Testing Insurance Card Crash Fixes"
echo "===================================="

# Clean and build
echo "🔨 Building application..."
./gradlew clean
./gradlew build

if [ $? -eq 0 ]; then
    echo "✅ Build successful"
else
    echo "❌ Build failed"
    exit 1
fi

# Install on device
echo "📱 Installing on device..."
./gradlew installDebug

if [ $? -eq 0 ]; then
    echo "✅ Installation successful"
else
    echo "❌ Installation failed"
    exit 1
fi

# Start logcat monitoring for SurfaceFlinger errors
echo "📊 Monitoring for SurfaceFlinger errors..."
echo "   (Press Ctrl+C to stop monitoring)"
echo "   Now test the insurance functionality in the app..."
echo ""

adb logcat -c  # Clear existing logs
adb logcat | grep -E "(SurfaceFlinger|insurance|ChatAdapter|Out of order buffers|MainActivityFSM)" --line-buffered | while read line; do
    echo "$(date '+%H:%M:%S') $line"
done
