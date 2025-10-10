#!/bin/bash

# Script to integrate the real farmer image
echo "🌾 Integrating Real Farmer Image"
echo "================================="
echo ""

# Check if farmer image exists
if [ ! -f "farmer_image.png" ]; then
    echo "❌ farmer_image.png not found in current directory"
    echo ""
    echo "📋 Please follow these steps:"
    echo "1. Save your farmer image as 'farmer_image.png' in this directory"
    echo "2. Make sure it's a high-quality PNG or JPG file"
    echo "3. Run this script again: ./integrate_farmer_image.sh"
    echo ""
    echo "💡 The image should show a smiling farmer with crops/sickle"
    echo "   as you described earlier."
    exit 1
fi

echo "✅ farmer_image.png found!"
echo ""

# Create drawable directories
echo "📁 Creating drawable directories..."
mkdir -p app/src/main/res/drawable-mdpi
mkdir -p app/src/main/res/drawable-hdpi
mkdir -p app/src/main/res/drawable-xhdpi
mkdir -p app/src/main/res/drawable-xxhdpi
mkdir -p app/src/main/res/drawable-xxxhdpi

# Copy the image to all directories
echo "📋 Copying farmer image to all density directories..."
cp farmer_image.png app/src/main/res/drawable-mdpi/
cp farmer_image.png app/src/main/res/drawable-hdpi/
cp farmer_image.png app/src/main/res/drawable-xhdpi/
cp farmer_image.png app/src/main/res/drawable-xxhdpi/
cp farmer_image.png app/src/main/res/drawable-xxxhdpi/

echo "✅ Images copied to all directories!"
echo ""

# Create a proper BitmapDrawable
echo "🔧 Creating proper BitmapDrawable..."
cat > app/src/main/res/drawable/farmer_real_bitmap.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/farmer_image"
    android:gravity="center"
    android:filter="true" />
EOF

echo "✅ BitmapDrawable created!"
echo ""

# Update the layout to use the real farmer image
echo "🎨 Updating layout to use real farmer image..."
sed -i '' 's/@drawable\/ic_local_florist/@drawable\/farmer_real_bitmap/g' app/src/main/res/layout/activity_landing_page.xml

echo "✅ Layout updated!"
echo ""

# Build the app
echo "🔨 Building the app..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 SUCCESS! Real farmer image integrated!"
    echo ""
    echo "📱 Your app now shows:"
    echo "   - Real farmer image with crops and sickle"
    echo "   - High quality at all screen densities"
    echo "   - Professional agricultural theme"
    echo ""
    echo "🚀 The landing page now displays your beautiful farmer image!"
    echo ""
    echo "🔄 To switch back to florist icon:"
    echo "   sed -i '' 's/@drawable\/farmer_real_bitmap/@drawable\/ic_local_florist/g' app/src/main/res/layout/activity_landing_page.xml"
else
    echo ""
    echo "❌ Build failed. Please check the error messages above."
    echo "💡 Make sure your farmer_image.png is a valid image file."
fi


