#!/bin/bash

# Setup script for farmer image
# This script helps you set up the farmer image for the app

echo "🌾 Sasya Arogya - Farmer Image Setup"
echo "====================================="
echo ""

# Check if farmer_image.png exists in current directory
if [ ! -f "farmer_image.png" ]; then
    echo "❌ farmer_image.png not found in current directory"
    echo ""
    echo "📋 Instructions:"
    echo "1. Save your farmer image as 'farmer_image.png' in this directory"
    echo "2. Run this script again: ./setup_farmer_image.sh"
    echo ""
    echo "📏 Image sizes needed:"
    echo "   - mdpi: 200x200px"
    echo "   - hdpi: 300x300px"
    echo "   - xhdpi: 400x400px"
    echo "   - xxhdpi: 600x600px"
    echo "   - xxxhdpi: 800x800px"
    echo ""
    echo "💡 You can use online tools like:"
    echo "   - https://resizeimage.net/"
    echo "   - https://www.iloveimg.com/resize-image"
    echo "   - Or any image editor to resize"
    exit 1
fi

echo "✅ farmer_image.png found!"
echo ""

# Create directories if they don't exist
echo "📁 Creating drawable directories..."
mkdir -p app/src/main/res/drawable-mdpi
mkdir -p app/src/main/res/drawable-hdpi
mkdir -p app/src/main/res/drawable-xhdpi
mkdir -p app/src/main/res/drawable-xxhdpi
mkdir -p app/src/main/res/drawable-xxxhdpi

# Copy the image to all directories
echo "📋 Copying farmer_image.png to all density directories..."
cp farmer_image.png app/src/main/res/drawable-mdpi/
cp farmer_image.png app/src/main/res/drawable-hdpi/
cp farmer_image.png app/src/main/res/drawable-xhdpi/
cp farmer_image.png app/src/main/res/drawable-xxhdpi/
cp farmer_image.png app/src/main/res/drawable-xxxhdpi/

echo "✅ Images copied to all directories!"
echo ""

# Update the BitmapDrawable to use the real image
echo "🔧 Updating BitmapDrawable to use real farmer image..."
sed -i '' 's/@drawable\/farmer_welcome_image/@drawable\/farmer_image/g' app/src/main/res/drawable/farmer_bitmap_drawable.xml

echo "✅ BitmapDrawable updated!"
echo ""

# Build the app
echo "🔨 Building the app..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 SUCCESS! Farmer image setup complete!"
    echo ""
    echo "📱 Your app now uses the real farmer image:"
    echo "   - High quality at all screen densities"
    echo "   - Professional appearance"
    echo "   - Perfect for the agricultural theme"
    echo ""
    echo "🚀 You can now run the app and see your farmer image!"
else
    echo ""
    echo "❌ Build failed. Please check the error messages above."
    echo "💡 Make sure your farmer_image.png is a valid image file."
fi


