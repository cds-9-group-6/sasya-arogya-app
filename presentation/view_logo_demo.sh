#!/bin/bash

# Sasya Arogya Logo Demo Launcher
echo "🌱 Opening Sasya Arogya Logo Presentation..."
echo "📱 This will show your beautiful agricultural app logo"
echo "🎯 Perfect for presentations and demos!"
echo ""

# Open the HTML file in default browser
if command -v open > /dev/null; then
    # macOS
    open logo_demo.html
elif command -v xdg-open > /dev/null; then
    # Linux
    xdg-open logo_demo.html  
elif command -v start > /dev/null; then
    # Windows
    start logo_demo.html
else
    echo "Please open 'presentation/logo_demo.html' in your web browser"
fi

echo "✅ Logo presentation should now be open in your browser!"
echo ""
echo "🎯 Use this for:"
echo "   • Client presentations"
echo "   • Investment pitches"  
echo "   • Team meetings"
echo "   • Play Store screenshots"
echo ""
echo "🌟 Your Sasya Arogya logo looks amazing!"
