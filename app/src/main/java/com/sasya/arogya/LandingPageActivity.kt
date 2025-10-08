package com.sasya.arogya

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class LandingPageActivity : AppCompatActivity() {
    
    private lateinit var farmerImage: ImageView
    private lateinit var welcomeText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var continueButton: Button
    private lateinit var appTitle: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing_page)
        
        initializeViews()
        setupAnimations()
        setupClickListeners()
        
        // Removed auto-navigation - user must click the start button
    }
    
    private fun initializeViews() {
        farmerImage = findViewById(R.id.farmerImage)
        welcomeText = findViewById(R.id.welcomeText)
        subtitleText = findViewById(R.id.subtitleText)
        continueButton = findViewById(R.id.continueButton)
        appTitle = findViewById(R.id.appTitle)
    }
    
    private fun setupAnimations() {
        // Fade in animation for the farmer image
        val fadeInAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeInAnimation.duration = 1000
        farmerImage.startAnimation(fadeInAnimation)
        
        // Slide up animation for the welcome text
        val slideUpAnimation = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        slideUpAnimation.duration = 1200
        slideUpAnimation.startOffset = 500
        welcomeText.startAnimation(slideUpAnimation)
        
        // Slide up animation for subtitle
        val slideUpAnimation2 = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        slideUpAnimation2.duration = 1200
        slideUpAnimation2.startOffset = 800
        subtitleText.startAnimation(slideUpAnimation2)
        
        // Fade in animation for the continue button
        val buttonAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        buttonAnimation.duration = 1500
        buttonAnimation.startOffset = 1500
        continueButton.startAnimation(buttonAnimation)
    }
    
    private fun setupClickListeners() {
        continueButton.setOnClickListener {
            navigateToMainActivity()
        }
        
        // Remove auto-navigation - only proceed when button is clicked
        // findViewById<View>(R.id.landingPageContainer).setOnClickListener {
        //     navigateToMainActivity()
        // }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivityFSM::class.java)
        startActivity(intent)
        finish()
        
        // Add smooth transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    
    override fun onBackPressed() {
        // Disable back button on landing page
        // User should use the continue button or wait for auto-navigation
    }
}
