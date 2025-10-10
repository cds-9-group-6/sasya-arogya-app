#!/bin/bash

# Script to update crop-care image with new version
echo "🌾 Update Crop-Care Image"
echo "========================"
echo ""

# Check if crop-care.png exists
if [ ! -f "crop-care.png" ]; then
    echo "❌ crop-care.png not found in current directory"
    echo ""
    echo "📋 To update the crop-care image:"
    echo "1. Save your new crop-care image as 'crop-care.png' in this directory"
    echo "2. Run this script again: ./update_crop_care.sh"
    exit 1
fi

echo "✅ crop-care.png found!"
echo ""

# Create drawable directories if they don't exist
echo "📁 Ensuring drawable directories exist..."
mkdir -p app/src/main/res/drawable-mdpi
mkdir -p app/src/main/res/drawable-hdpi
mkdir -p app/src/main/res/drawable-xhdpi
mkdir -p app/src/main/res/drawable-xxhdpi
mkdir -p app/src/main/res/drawable-xxxhdpi

# Resize and copy the image to all directories
echo "📋 Resizing and updating crop-care image for all density directories..."

# Use Python to resize images properly
python3 -c "
from PIL import Image
import os

# Load the original image
original = Image.open('crop-care.png')
print(f'Original image size: {original.size}')

# Create resized versions for different densities
sizes = [
    ('mdpi', 48),
    ('hdpi', 72),
    ('xhdpi', 96),
    ('xxhdpi', 144),
    ('xxxhdpi', 192)
]

for density, size in sizes:
    # Resize image maintaining aspect ratio
    resized = original.resize((size, size), Image.Resampling.LANCZOS)
    
    # Save as PNG
    resized.save(f'app/src/main/res/drawable-{density}/crop_care.png')
    print(f'Updated crop_care.png for {density} ({size}x{size})')

print('All crop-care bitmap images updated successfully!')
"

if [ $? -eq 0 ]; then
    echo "✅ Crop-care images updated successfully!"
    echo ""
    
    # Build the app
    echo "🔨 Building the app..."
    ./gradlew assembleDebug
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "🎉 SUCCESS! New crop-care image integrated!"
        echo ""
        echo "📱 Your app now shows:"
        echo "   - Updated crop-care image (bitmap, not vector)"
        echo "   - High quality at all screen densities"
        echo "   - Professional agricultural theme"
        echo ""
        echo "🚀 The app now uses your new crop-care image!"
    else
        echo ""
        echo "❌ Build failed. Please check the error messages above."
    fi
else
    echo ""
    echo "❌ Image processing failed. Please check if crop-care.png is a valid image file."
fi


