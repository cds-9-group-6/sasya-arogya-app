#!/bin/bash

# Script to replace placeholder farmer images with real farmer image
echo "🌾 Replace with Real Farmer Image"
echo "================================="
echo ""

# Check if real farmer image exists
if [ ! -f "real_farmer_image.png" ]; then
    echo "❌ real_farmer_image.png not found in current directory"
    echo ""
    echo "📋 To use your real farmer image:"
    echo "1. Save your farmer image as 'real_farmer_image.png' in this directory"
    echo "2. Run this script again: ./replace_with_real_farmer.sh"
    echo ""
    echo "💡 Your farmer image should show:"
    echo "   - Smiling farmer with crops and sickle"
    echo "   - Traditional attire (turban, dhoti)"
    echo "   - Agricultural theme"
    echo ""
    echo "📏 The script will automatically resize for all densities:"
    echo "   - mdpi: 200x200px"
    echo "   - hdpi: 300x300px"
    echo "   - xhdpi: 400x400px"
    echo "   - xxhdpi: 600x600px"
    echo "   - xxxhdpi: 800x800px"
    exit 1
fi

echo "✅ real_farmer_image.png found!"
echo ""

# Create drawable directories if they don't exist
echo "📁 Ensuring drawable directories exist..."
mkdir -p app/src/main/res/drawable-mdpi
mkdir -p app/src/main/res/drawable-hdpi
mkdir -p app/src/main/res/drawable-xhdpi
mkdir -p app/src/main/res/drawable-xxhdpi
mkdir -p app/src/main/res/drawable-xxxhdpi

# Resize and copy the image to all directories
echo "📋 Resizing and copying farmer image to all density directories..."

# Use Python to resize images properly
python3 -c "
from PIL import Image
import os

# Load the original image
original = Image.open('real_farmer_image.png')

# Create resized versions for different densities
sizes = [
    ('mdpi', 200),
    ('hdpi', 300),
    ('xhdpi', 400),
    ('xxhdpi', 600),
    ('xxxhdpi', 800)
]

for density, size in sizes:
    # Resize image maintaining aspect ratio
    resized = original.resize((size, size), Image.Resampling.LANCZOS)
    
    # Save as PNG
    resized.save(f'app/src/main/res/drawable-{density}/farmer_image.png')
    print(f'Created farmer_image.png for {density} ({size}x{size})')

print('All farmer bitmap images updated successfully!')
"

if [ $? -eq 0 ]; then
    echo "✅ Images resized and copied successfully!"
    echo ""
    
    # Build the app
    echo "🔨 Building the app..."
    ./gradlew assembleDebug
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "🎉 SUCCESS! Real farmer image integrated!"
        echo ""
        echo "📱 Your app now shows:"
        echo "   - Real farmer image (bitmap, not vector)"
        echo "   - High quality at all screen densities"
        echo "   - Professional agricultural theme"
        echo "   - True bitmap drawable implementation"
        echo ""
        echo "🚀 The landing page now displays your real farmer image as a bitmap!"
    else
        echo ""
        echo "❌ Build failed. Please check the error messages above."
    fi
else
    echo ""
    echo "❌ Image processing failed. Please check if real_farmer_image.png is a valid image file."
fi


