#!/bin/bash

# Script to properly integrate real farmer image
echo "🌾 Proper Farmer Image Integration"
echo "=================================="
echo ""

# Check if farmer image exists
if [ ! -f "farmer_image.png" ]; then
    echo "❌ farmer_image.png not found in current directory"
    echo ""
    echo "📋 To use your real farmer image:"
    echo "1. Save your farmer image as 'farmer_image.png' in this directory"
    echo "2. Run this script again: ./use_real_farmer_image.sh"
    echo ""
    echo "💡 Your farmer image should show:"
    echo "   - Smiling farmer with crops and sickle"
    echo "   - Traditional attire (turban, dhoti)"
    echo "   - Agricultural theme"
    echo ""
    echo "📏 Recommended sizes:"
    echo "   - mdpi: 200x200px"
    echo "   - hdpi: 300x300px"
    echo "   - xhdpi: 400x400px"
    echo "   - xxhdpi: 600x600px"
    echo "   - xxxhdpi: 800x800px"
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

# Update BitmapDrawable to use real image
echo "🔧 Updating BitmapDrawable to use real farmer image..."
cat > app/src/main/res/drawable/farmer_bitmap_drawable.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<!-- 
    BitmapDrawable for real farmer images (PNG/JPG)
    Now using the actual farmer_image.png
-->
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/farmer_image"
    android:gravity="center"
    android:filter="true" />
EOF

echo "✅ BitmapDrawable updated to use real image!"
echo ""

# Update the layout to use BitmapDrawable
echo "🎨 Updating layout to use BitmapDrawable..."
sed -i '' 's/@drawable\/farmer_welcome_image/@drawable\/farmer_bitmap_drawable/g' app/src/main/res/layout/activity_landing_page.xml

echo "✅ Layout updated to use BitmapDrawable!"
echo ""

# Build the app
echo "🔨 Building the app..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 SUCCESS! Real farmer image properly integrated!"
    echo ""
    echo "📱 Your app now shows:"
    echo "   - Real farmer image with crops and sickle"
    echo "   - High quality at all screen densities"
    echo "   - Professional agricultural theme"
    echo "   - Proper BitmapDrawable implementation"
    echo ""
    echo "🚀 The landing page now displays your beautiful farmer image!"
    echo ""
    echo "🔄 To switch back to vector farmer:"
    echo "   sed -i '' 's/@drawable\/farmer_bitmap_drawable/@drawable\/farmer_welcome_image/g' app/src/main/res/layout/activity_landing_page.xml"
    echo ""
    echo "🔄 To switch back to florist icon:"
    echo "   sed -i '' 's/@drawable\/farmer_bitmap_drawable/@drawable\/ic_local_florist/g' app/src/main/res/layout/activity_landing_page.xml"
else
    echo ""
    echo "❌ Build failed. Please check the error messages above."
    echo "💡 Make sure your farmer_image.png is a valid image file."
fi


