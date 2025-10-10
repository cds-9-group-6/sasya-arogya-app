# Farmer Image Setup for BitmapDrawable

## Overview
The landing page now uses BitmapDrawable for the farmer image, which supports real PNG/JPG images at different screen densities.

## Current Setup
- **Current Image**: `@drawable/farmer_welcome_image` (vector drawable - working)
- **BitmapDrawable Ready**: `@drawable/farmer_bitmap_drawable` (for real images)
- **Layout Reference**: `activity_landing_page.xml` ImageView

## How to Add Real Farmer Images

### 1. Prepare Your Images
Create farmer images in these sizes:
- **mdpi**: 200x200px (1x)
- **hdpi**: 300x300px (1.5x)
- **xhdpi**: 400x400px (2x)
- **xxhdpi**: 600x600px (3x)
- **xxxhdpi**: 800x800px (4x)

### 2. Add Images to Drawable Directories
Place your farmer images in:
```
app/src/main/res/drawable-mdpi/farmer_image.png
app/src/main/res/drawable-hdpi/farmer_image.png
app/src/main/res/drawable-xhdpi/farmer_image.png
app/src/main/res/drawable-xxhdpi/farmer_image.png
app/src/main/res/drawable-xxxhdpi/farmer_image.png
```

### 3. Update BitmapDrawable Reference
Edit `app/src/main/res/drawable/farmer_bitmap_drawable.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/farmer_image"
    android:gravity="center"
    android:filter="true" />
```

### 4. Update Layout Reference
Edit `app/src/main/res/layout/activity_landing_page.xml`:
```xml
<ImageView
    android:src="@drawable/farmer_bitmap_drawable"
    ... />
```

### 5. Image Requirements
- **Format**: PNG (preferred) or JPG
- **Background**: Transparent or white
- **Content**: Farmer character, agricultural theme
- **Quality**: High resolution for crisp display
- **Aspect Ratio**: Square (1:1) recommended

### 6. Benefits of BitmapDrawable
- ✅ **Real Images**: Use actual photos/PNG graphics
- ✅ **High Quality**: No vector scaling artifacts
- ✅ **Density Support**: Different sizes for different screens
- ✅ **Performance**: Optimized for bitmap rendering
- ✅ **Flexibility**: Easy to update with new images

### 7. Current Status
- **Working**: App uses vector drawable `farmer_welcome_image.xml`
- **Ready**: BitmapDrawable setup complete for real images
- **Next Step**: Add your PNG/JPG farmer images following the steps above

## Testing
After adding real images:
1. Build the app: `./gradlew assembleDebug`
2. Test on different screen densities
3. Verify image quality and scaling
4. Check performance on various devices

## Notes
- Keep original vector drawable as backup: `farmer_welcome_image.xml`
- BitmapDrawable automatically handles density selection
- Consider file size for app bundle optimization
- Test on both GPU and Non-GPU variants
