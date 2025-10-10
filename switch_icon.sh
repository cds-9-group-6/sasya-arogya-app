#!/bin/bash

# Script to easily switch between different icon options
echo "🔄 Icon Switcher for Sasya Arogya"
echo "================================="
echo ""

if [ $# -eq 0 ]; then
    echo "Usage: ./switch_icon.sh [option]"
    echo ""
    echo "Available options:"
    echo "  farmer-vector    - Use farmer welcome vector drawable"
    echo "  farmer-real      - Use real farmer image (requires farmer_image.png)"
    echo "  florist          - Use colored florist icon"
    echo "  custom           - Use custom farmer character"
    echo ""
    echo "Example: ./switch_icon.sh farmer-vector"
    exit 1
fi

ICON_TYPE=$1
LAYOUT_FILE="app/src/main/res/layout/activity_landing_page.xml"

case $ICON_TYPE in
    "farmer-vector")
        echo "🌾 Switching to farmer welcome vector..."
        sed -i '' 's/android:src="@drawable\/[^"]*"/android:src="@drawable\/farmer_welcome_image"/g' $LAYOUT_FILE
        echo "✅ Switched to farmer welcome vector drawable"
        ;;
    "farmer-real")
        echo "🖼️ Switching to real farmer image..."
        if [ ! -f "farmer_image.png" ]; then
            echo "❌ farmer_image.png not found!"
            echo "💡 Please add your farmer image first, then run:"
            echo "   ./use_real_farmer_image.sh"
            exit 1
        fi
        sed -i '' 's/android:src="@drawable\/[^"]*"/android:src="@drawable\/farmer_bitmap_drawable"/g' $LAYOUT_FILE
        echo "✅ Switched to real farmer image (BitmapDrawable)"
        ;;
    "florist")
        echo "🌸 Switching to colored florist icon..."
        sed -i '' 's/android:src="@drawable\/[^"]*"/android:src="@drawable\/ic_local_florist"/g' $LAYOUT_FILE
        echo "✅ Switched to colored florist icon"
        ;;
    "custom")
        echo "👨‍🌾 Switching to custom farmer character..."
        sed -i '' 's/android:src="@drawable\/[^"]*"/android:src="@drawable\/ic_farmer_welcome"/g' $LAYOUT_FILE
        echo "✅ Switched to custom farmer character"
        ;;
    *)
        echo "❌ Unknown option: $ICON_TYPE"
        echo "💡 Use: ./switch_icon.sh [farmer-vector|farmer-real|florist|custom]"
        exit 1
        ;;
esac

echo ""
echo "🔨 Building app to test changes..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "✅ Build successful! Icon switched to: $ICON_TYPE"
    echo ""
    echo "📱 Your app now uses the $ICON_TYPE icon"
else
    echo "❌ Build failed. Please check the error messages above."
fi


