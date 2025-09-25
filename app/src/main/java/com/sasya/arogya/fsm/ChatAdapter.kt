package com.sasya.arogya.fsm

import android.app.Dialog
import com.sasya.arogya.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.sasya.arogya.models.FeedbackManager
import com.sasya.arogya.models.FeedbackType
import com.sasya.arogya.models.MessageFeedback
import com.sasya.arogya.utils.TextFormattingUtil
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView Adapter for chat messages with follow-up buttons and feedback
 */
class ChatAdapter(
    private val onFollowUpClick: (String) -> Unit,
    private val onThumbsUpClick: (ChatMessage) -> Unit = {},
    private val onThumbsDownClick: (ChatMessage) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private val messages = mutableListOf<ChatMessage>()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
        private const val VIEW_TYPE_DISEASE_CARD = 3
        private const val VIEW_TYPE_HEALTHY_CARD = 4
        private const val VIEW_TYPE_SEVERE_DISEASE_CARD = 5
        private const val VIEW_TYPE_PRESCRIPTION_CARD = 6
    }
    
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isUser -> {
                android.util.Log.d("ChatAdapter", "🙋 Position $position: USER message")
                VIEW_TYPE_USER
            }
            // Check prescription FIRST - highest priority for treatment recommendations
            isPrescriptionMessage(message) -> {
                android.util.Log.d("ChatAdapter", "℞ Position $position: PRESCRIPTION card - confidence: ${message.confidence}, diseaseName: '${message.diseaseName}', text: ${message.text.take(50)}...")
                VIEW_TYPE_PRESCRIPTION_CARD
            }
            // Check healthy SECOND to avoid misclassification
            isHealthyMessage(message) -> {
                android.util.Log.d("ChatAdapter", "🌱 Position $position: HEALTHY card - confidence: ${message.confidence}, diseaseName: '${message.diseaseName}', text: ${message.text.take(50)}...")
                VIEW_TYPE_HEALTHY_CARD
            }
            isDiseaseMessage(message) -> {
                when {
                    isSevereDisease(message) -> {
                        android.util.Log.d("ChatAdapter", "🚨 Position $position: SEVERE DISEASE card - confidence: ${message.confidence}, diseaseName: '${message.diseaseName}'")
                        VIEW_TYPE_SEVERE_DISEASE_CARD
                    }
                    else -> {
                        android.util.Log.d("ChatAdapter", "🦠 Position $position: DISEASE card - confidence: ${message.confidence}, diseaseName: '${message.diseaseName}', text: ${message.text.take(50)}...")
                        VIEW_TYPE_DISEASE_CARD
                    }
                }
            }
            else -> {
                android.util.Log.d("ChatAdapter", "💬 Position $position: ASSISTANT message")
                VIEW_TYPE_ASSISTANT
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_chat_user, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_ASSISTANT -> {
                val view = inflater.inflate(R.layout.item_chat_assistant, parent, false)
                AssistantMessageViewHolder(view, onFollowUpClick, onThumbsUpClick, onThumbsDownClick)
            }
            VIEW_TYPE_DISEASE_CARD -> {
                val view = inflater.inflate(R.layout.disease_result_card, parent, false)
                DiseaseCardViewHolder(view, onFollowUpClick, onThumbsUpClick, onThumbsDownClick)
            }
            VIEW_TYPE_HEALTHY_CARD -> {
                val view = inflater.inflate(R.layout.healthy_result_card, parent, false)
                HealthyCardViewHolder(view, onFollowUpClick, onThumbsUpClick, onThumbsDownClick)
            }
            VIEW_TYPE_SEVERE_DISEASE_CARD -> {
                val view = inflater.inflate(R.layout.disease_result_card, parent, false)
                SevereDiseaseCardViewHolder(view, onFollowUpClick, onThumbsUpClick, onThumbsDownClick)
            }
            VIEW_TYPE_PRESCRIPTION_CARD -> {
                val view = inflater.inflate(R.layout.prescription_result_card, parent, false)
                PrescriptionCardViewHolder(view, onFollowUpClick, onThumbsUpClick, onThumbsDownClick)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message)
            is DiseaseCardViewHolder -> holder.bind(message)
            is HealthyCardViewHolder -> holder.bind(message)
            is SevereDiseaseCardViewHolder -> holder.bind(message)
            is PrescriptionCardViewHolder -> holder.bind(message)
        }
    }
    
    override fun getItemCount(): Int = messages.size
    
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun updateLastMessage(text: String) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages[messages.size - 1]
            if (!lastMessage.isUser) {
                messages[messages.size - 1] = lastMessage.copy(text = text)
                notifyItemChanged(messages.size - 1)
            }
        }
    }
    
    fun addFollowUpToLastMessage(followUpItems: List<String>) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages[messages.size - 1]
            if (!lastMessage.isUser) {
                messages[messages.size - 1] = lastMessage.copy(followUpItems = followUpItems)
                notifyItemChanged(messages.size - 1)
            }
        }
    }
    
    fun updateLastMessageWithOverlay(updatedMessage: ChatMessage) {
        if (messages.isNotEmpty() && !messages.last().isUser) {
            messages[messages.size - 1] = updatedMessage
            notifyItemChanged(messages.size - 1)
        }
    }
    
    // 🆕 CRITICAL FIX: Update message with prescription data from server
    fun updateMessageWithPrescription(position: Int, updatedMessage: ChatMessage) {
        if (position in 0 until messages.size) {
            messages[position] = updatedMessage
            notifyItemChanged(position)
            
            android.util.Log.d("ChatAdapter", "📋 Updated message at position $position with prescription data")
        }
    }
    
    // 🆕 CRITICAL FIX: Update message with classification data from server  
    fun updateMessageWithClassification(position: Int, updatedMessage: ChatMessage) {
        if (position in 0 until messages.size) {
            messages[position] = updatedMessage
            notifyItemChanged(position)
            
            android.util.Log.d("ChatAdapter", "🔬 Updated message at position $position with classification data: ${updatedMessage.diseaseName} (${updatedMessage.confidence?.let { "${(it * 100).toInt()}%" }})")
        }
    }
    
    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }
    
    /**
     * Helper methods to detect message types based on content
     */
    
    // Helper function to determine if diseaseName indicates health vs actual disease
    private fun diseaseNameIndicatesHealthy(diseaseName: String?): Boolean {
        val result = diseaseName == null || 
               diseaseName.isBlank() ||
               diseaseName.contains("healthy", ignoreCase = true) ||
               diseaseName.contains("no disease", ignoreCase = true) ||
               diseaseName.equals("none", ignoreCase = true)
        
        android.util.Log.d("ChatAdapter", "🔍 diseaseNameIndicatesHealthy('$diseaseName'): $result")
        return result
    }
    
    // Helper function for healthy keywords
    private fun hasHealthyKeywords(text: String): Boolean {
        val keywords = listOf(
            "healthy", "looks good", "no disease found", "plant appears healthy",
            "no issues detected", "plant looks healthy", "appears to be healthy",
            "no disease", "is healthy", "plant is in good", "no signs of disease"
        )
        
        val result = keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
        android.util.Log.d("ChatAdapter", "🔍 hasHealthyKeywords('${text.take(50)}...'): $result")
        
        return result
    }
    
    private fun isDiseaseMessage(message: ChatMessage): Boolean {
        // Check if diseaseName indicates an actual disease (not healthy indicators)
        val hasActualDiseaseName = !diseaseNameIndicatesHealthy(message.diseaseName)
        
        // Check for disease detection phrases (excluding healthy variants)
        val hasDiseaseDetectionPhrases = (message.confidence != null && message.confidence > 0.0 && 
            (message.text.contains("detected:", ignoreCase = true) ||
             message.text.contains("diagnosis:", ignoreCase = true) ||
             message.text.contains("identified as", ignoreCase = true) ||
             message.text.contains("appears to be", ignoreCase = true) ||
             message.text.contains("suffering from", ignoreCase = true)) &&
            // Make sure these phrases aren't referring to healthy plants
            !message.text.contains("appears to be healthy", ignoreCase = true) &&
            !message.text.contains("identified as healthy", ignoreCase = true))
        
        // Only trigger disease card if we have actual disease data OR specific detection phrases
        // AND it's not already identified as healthy
        return (hasActualDiseaseName || hasDiseaseDetectionPhrases) && 
               !hasHealthyKeywords(message.text)
    }
    
    private fun isHealthyMessage(message: ChatMessage): Boolean {
        // Use shared helper functions for consistency
        val hasHealthyKeywordsInText = hasHealthyKeywords(message.text)
        val diseaseNameIsHealthy = diseaseNameIndicatesHealthy(message.diseaseName)
        
        android.util.Log.d("ChatAdapter", "🔍 HEALTHY CHECK - confidence: ${message.confidence}, diseaseName: '${message.diseaseName}', hasHealthyKeywords: $hasHealthyKeywordsInText, diseaseNameIsHealthy: $diseaseNameIsHealthy")
        android.util.Log.d("ChatAdapter", "🔍 HEALTHY CHECK - text: ${message.text.take(100)}...")
        
        // AGGRESSIVE BACKUP: If text explicitly mentions healthy, treat it as healthy regardless
        val explicitHealthyMention = message.text.contains("healthy", ignoreCase = true) ||
                                    message.text.contains("plant looks good", ignoreCase = true) ||
                                    message.text.contains("no disease", ignoreCase = true)
        
        if (explicitHealthyMention && (message.confidence == null || message.confidence > 0.3)) {
            android.util.Log.d("ChatAdapter", "🌱 BACKUP HEALTHY DETECTION: Explicit healthy mention found!")
            return true
        }
        
        // SIMPLIFIED LOGIC: Make it much more likely to detect healthy plants
        val hasConfidence = message.confidence != null && message.confidence > 0.5  // Lower threshold
        val hasHealthyIndicators = hasHealthyKeywordsInText || diseaseNameIsHealthy
        val notExplicitDisease = !message.text.contains("disease detected", ignoreCase = true) &&
                                !message.text.contains("disease identified", ignoreCase = true) &&
                                !message.text.contains("diagnosed as", ignoreCase = true)
        
        val result = hasConfidence && hasHealthyIndicators && notExplicitDisease
        
        android.util.Log.d("ChatAdapter", "🔍 HEALTHY CHECK RESULT: $result (hasConfidence: $hasConfidence, hasHealthyIndicators: $hasHealthyIndicators, notExplicitDisease: $notExplicitDisease)")
        
        return result
    }
    
    private fun isPrescriptionMessage(message: ChatMessage): Boolean {
        // ✅ FIXED LOGIC: Only show STANDALONE prescription for explicit user requests
        // Disease messages with prescription data should NOT trigger standalone prescription
        
        // Check if this is an explicit user request for a standalone prescription
        val explicitPrescriptionKeywords = listOf(
            "give me a prescription",
            "show me the prescription", 
            "generate prescription",
            "create prescription",
            "prescription card",
            "treatment plan card"
        )
        
        val hasExplicitRequest = explicitPrescriptionKeywords.any { keyword ->
            message.text.contains(keyword, ignoreCase = true)
        }
        
        // Only show standalone if:
        // 1. Explicit prescription request AND 
        // 2. NOT a disease classification message
        val shouldShowStandalone = hasExplicitRequest && !isDiseaseMessage(message)
        
        android.util.Log.d("ChatAdapter", "🔍 STANDALONE PRESCRIPTION CHECK - explicitRequest: $hasExplicitRequest, isDiseaseMsg: ${isDiseaseMessage(message)}, result: $shouldShowStandalone")
        
        return shouldShowStandalone
    }
    
    private fun shouldEmbedPrescription(message: ChatMessage): Boolean {
        // PRIORITY 1: Check for structured prescription data
        if (message.structuredPrescription != null) {
            android.util.Log.d("ChatAdapter", "✅ STRUCTURED PRESCRIPTION FOUND - embedding in disease card")
            return true
        }
        
        // PRIORITY 2: Check for text-based prescription indicators (backward compatibility)
        val hasPrescriptionKeywords = (message.text.contains("medication", ignoreCase = true) ||
                                      message.text.contains("dosage", ignoreCase = true) ||
                                      message.text.contains("apply every", ignoreCase = true) ||
                                      message.text.contains("fungicide spray", ignoreCase = true) ||
                                      message.text.contains("treatment schedule", ignoreCase = true))
        
        val hasDetailedTreatment = hasTreatmentRecommendations(message) && message.text.length > 200
        
        android.util.Log.d("ChatAdapter", "🔍 EMBED PRESCRIPTION CHECK - structured: ${message.structuredPrescription != null}, keywords: $hasPrescriptionKeywords, detailed: $hasDetailedTreatment")
        
        return (hasPrescriptionKeywords || hasDetailedTreatment) &&
               !diseaseNameIndicatesHealthy(message.diseaseName)
    }
    
    private fun isSevereDisease(message: ChatMessage): Boolean {
        return !diseaseNameIndicatesHealthy(message.diseaseName) && 
               (message.confidence != null && message.confidence > 0.95) &&
               (message.text.contains("severe", ignoreCase = true) ||
                message.text.contains("critical", ignoreCase = true) ||
                message.text.contains("urgent", ignoreCase = true) ||
                message.text.contains("immediate action", ignoreCase = true))
    }
    
    private fun hasTreatmentRecommendations(message: ChatMessage): Boolean {
        // More comprehensive check for treatment recommendations
        val hasKeywords = message.text.contains("treatment:", ignoreCase = true) ||
                         message.text.contains("apply", ignoreCase = true) ||
                         message.text.contains("fungicide", ignoreCase = true) ||
                         message.text.contains("spray", ignoreCase = true) ||
                         message.text.contains("remove affected", ignoreCase = true) ||
                         message.text.contains("recommendations:", ignoreCase = true) ||
                         message.text.contains("TREATMENT FOR YOUR PLANT", ignoreCase = true) ||
                         message.text.contains("solution:", ignoreCase = true) ||
                         message.text.contains("steps:", ignoreCase = true)
        
        android.util.Log.d("ChatAdapter", "hasTreatmentRecommendations: $hasKeywords for text: ${message.text.take(100)}...")
        return hasKeywords
    }
    
    /**
     * ViewHolder for user messages
     */
    inner class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImage)
        
        fun bind(message: ChatMessage) {
            messageText.text = TextFormattingUtil.formatWhatsAppStyle(message.text)
            messageTime.text = timeFormatter.format(Date(message.timestamp))
            
            // Show image if present
            if (message.imageUri != null) {
                messageImage.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(message.imageUri)
                    .into(messageImage)
            } else {
                messageImage.visibility = View.GONE
            }
        }
    }
    
    /**
     * ViewHolder for assistant messages with follow-up buttons and feedback
     */
    inner class AssistantMessageViewHolder(
        itemView: View,
        private val onFollowUpClick: (String) -> Unit,
        private val onThumbsUpClick: (ChatMessage) -> Unit,
        private val onThumbsDownClick: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val stateIndicator: TextView = itemView.findViewById(R.id.stateIndicator)
        private val followUpContainer: LinearLayout = itemView.findViewById(R.id.followUpContainer)
        private val followUpChipGroup: ChipGroup = itemView.findViewById(R.id.followUpChipGroup)
        private val thumbsUpButton: ImageButton = itemView.findViewById(R.id.thumbsUpButton)
        private val thumbsDownButton: ImageButton = itemView.findViewById(R.id.thumbsDownButton)
        private val attentionOverlayContainer: LinearLayout = itemView.findViewById(R.id.attentionOverlayContainer)
        private val attentionOverlayImage: ImageView = itemView.findViewById(R.id.attentionOverlayImage)
        private val overlayDescription: TextView = itemView.findViewById(R.id.overlayDescription)
        
        fun bind(message: ChatMessage) {
            messageText.text = TextFormattingUtil.formatWhatsAppStyle(message.text)
            messageTime.text = timeFormatter.format(Date(message.timestamp))
            
            // Show state indicator if present
            if (message.state != null) {
                stateIndicator.visibility = View.VISIBLE
                stateIndicator.text = message.state
                
                // Color based on state
                val backgroundColor = when (message.state.lowercase()) {
                    "ready" -> ContextCompat.getColor(itemView.context, R.color.state_ready)
                    "analyzing plant..." -> ContextCompat.getColor(itemView.context, R.color.state_processing)
                    "diagnosis complete" -> ContextCompat.getColor(itemView.context, R.color.state_complete)
                    else -> ContextCompat.getColor(itemView.context, R.color.state_default)
                }
                
                val drawable = stateIndicator.background as? GradientDrawable
                drawable?.setColor(backgroundColor)
            } else {
                stateIndicator.visibility = View.GONE
            }
            
            // Add follow-up buttons
            if (message.followUpItems != null && message.followUpItems.isNotEmpty()) {
                followUpContainer.visibility = View.VISIBLE
                followUpChipGroup.removeAllViews()
                
                message.followUpItems.forEach { followUpText ->
                    val chip = Chip(itemView.context).apply {
                        text = followUpText
                        isClickable = true
                        isCheckable = false
                        
                        // Light green styling as requested
                        chipBackgroundColor = ContextCompat.getColorStateList(
                            itemView.context, R.color.followup_chip_background
                        )
                        setTextColor(ContextCompat.getColor(itemView.context, R.color.followup_chip_text))
                        chipStrokeColor = ContextCompat.getColorStateList(
                            itemView.context, R.color.followup_chip_stroke
                        )
                        chipStrokeWidth = 2f
                        
                        setOnClickListener {
                            // Change appearance to show clicked state
                            chipBackgroundColor = ContextCompat.getColorStateList(
                                itemView.context, R.color.followup_chip_clicked
                            )
                            isClickable = false
                            text = "✓ $followUpText"
                            
                            // Trigger callback
                            onFollowUpClick(followUpText)
                        }
                    }
                    followUpChipGroup.addView(chip)
                }
            } else {
                followUpContainer.visibility = View.GONE
            }
            
            // Set up feedback button listeners
            thumbsUpButton.setOnClickListener {
                // Visual feedback - highlight the clicked button
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_up_selected)
                )
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                
                // Record feedback
                val feedback = MessageFeedback(
                    messageText = message.text,
                    feedbackType = FeedbackType.THUMBS_UP,
                    userContext = "Positive feedback from chat"
                )
                FeedbackManager.recordFeedback(feedback)
                
                // Trigger callback
                onThumbsUpClick(message)
            }
            
            thumbsDownButton.setOnClickListener {
                // Visual feedback - highlight the clicked button  
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_down_selected)
                )
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                
                // Record feedback
                val feedback = MessageFeedback(
                    messageText = message.text,
                    feedbackType = FeedbackType.THUMBS_DOWN,
                    userContext = "Negative feedback from chat - needs improvement"
                )
                FeedbackManager.recordFeedback(feedback)
                
                // Trigger callback
                onThumbsDownClick(message)
            }
            
            // Handle attention overlay display
            if (message.attentionOverlayBase64 != null) {
                attentionOverlayContainer.visibility = View.VISIBLE
                
                // Update description with disease info and clickable hint
                val description = if (message.diseaseName != null && message.confidence != null) {
                    "Detected: ${message.diseaseName} (${String.format("%.1f", message.confidence * 100)}% confidence) • Tap to zoom & inspect"
                } else {
                    "AI attention heatmap with blue/yellow highlights • Tap to zoom & inspect"
                }
                overlayDescription.text = description
                
                // Decode and display the base64 attention overlay image
                try {
                    val imageBytes = Base64.decode(message.attentionOverlayBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    attentionOverlayImage.setImageBitmap(bitmap)
                    
                    // Make the overlay clickable to open in full-screen modal
                    attentionOverlayImage.setOnClickListener {
                        showAttentionOverlayModal(bitmap, message.diseaseName, message.confidence)
                    }
                    
                    // Add visual feedback for clickability
                    attentionOverlayImage.background = ContextCompat.getDrawable(
                        itemView.context, 
                        R.drawable.button_ripple
                    )
                    
                } catch (e: Exception) {
                    Log.e("ChatAdapter", "Error decoding attention overlay image", e)
                    attentionOverlayContainer.visibility = View.GONE
                }
            } else {
                attentionOverlayContainer.visibility = View.GONE
            }
        }
        
        private fun showAttentionOverlayModal(bitmap: Bitmap, diseaseName: String?, confidence: Double?) {
            // Create custom dialog
            val dialog = Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_attention_overlay)
            
            // Set up dialog views
            val imgOverlay = dialog.findViewById<ImageView>(R.id.imgAttentionOverlay)
            val tvDiseaseInfo = dialog.findViewById<TextView>(R.id.tvDiseaseInfo)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnClose)
            val zoomInstructions = dialog.findViewById<TextView>(R.id.zoomInstructions)
            
            // Display the full-size overlay image
            imgOverlay.setImageBitmap(bitmap)
            
            // Update disease information
            val diseaseText = if (diseaseName != null && confidence != null) {
                "Detected: $diseaseName (${String.format("%.1f", confidence * 100)}% confidence)"
            } else {
                "AI Attention Analysis - Diagnostic Focus Areas"
            }
            tvDiseaseInfo.text = diseaseText
            
            // Set up zoom functionality
            setupImageZoom(imgOverlay, zoomInstructions)
            
            // Close button functionality
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Allow tapping outside to close
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            
            // Show the dialog
            dialog.show()
            
            Log.d("ChatAdapter", "🎯 Opened zoomable attention overlay modal for detailed inspection")
        }
        
        private fun setupImageZoom(imageView: ImageView, instructionsView: TextView) {
            val matrix = Matrix()
            var scale = 1f
            var lastScaleFactor = 0f
            
            // Where the user started to drag
            var startX = 0f
            var startY = 0f
            
            // How much to translate with the drag
            var dx = 0f
            var dy = 0f
            var prevDx = 0f
            var prevDy = 0f
            
            val scaleDetector = ScaleGestureDetector(itemView.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    lastScaleFactor = 0f
                    return true
                }
                
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scaleFactor = detector.scaleFactor
                    
                    if (lastScaleFactor == 0f || (Math.signum(scaleFactor) == Math.signum(lastScaleFactor))) {
                        scale *= scaleFactor
                        scale = Math.max(0.1f, Math.min(scale, 5.0f))
                        lastScaleFactor = scaleFactor
                    }
                    return true
                }
            })
            
            val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    // Double tap to reset zoom
                    scale = 1f
                    dx = 0f
                    dy = 0f
                    prevDx = 0f
                    prevDy = 0f
                    return true
                }
            })
            
            imageView.setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                
                when (event.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x - prevDx
                        startY = event.y - prevDy
                        // Show zoom instructions
                        instructionsView.visibility = View.VISIBLE
                        instructionsView.animate().alpha(1f).setDuration(200).start()
                    }
                    
                    MotionEvent.ACTION_MOVE -> {
                        if (!scaleDetector.isInProgress) {
                            dx = event.x - startX
                            dy = event.y - startY
                        }
                    }
                    
                    MotionEvent.ACTION_UP -> {
                        prevDx = dx
                        prevDy = dy
                        // Hide zoom instructions after a delay
                        instructionsView.animate().alpha(0f).setDuration(200).withEndAction {
                            instructionsView.visibility = View.GONE
                        }.startDelay = 2000
                    }
                    
                    MotionEvent.ACTION_POINTER_UP -> {
                        prevDx = dx
                        prevDy = dy
                    }
                }
                
                // Apply transformations
                matrix.reset()
                matrix.postScale(scale, scale)
                matrix.postTranslate(dx, dy)
                imageView.imageMatrix = matrix
                imageView.invalidate()
                
                true
            }
            
            Log.d("ChatAdapter", "🔍 Zoom functionality initialized for attention overlay")
        }
    }
    
    /**
     * ViewHolder for disease detection cards
     */
    inner class DiseaseCardViewHolder(
        itemView: View,
        private val onFollowUpClick: (String) -> Unit,
        private val onThumbsUpClick: (ChatMessage) -> Unit,
        private val onThumbsDownClick: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val diseaseName: TextView = itemView.findViewById(R.id.diseaseName)
        private val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
        private val confidenceBar: ProgressBar = itemView.findViewById(R.id.confidenceBar)
        private val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        private val symptomsText: TextView = itemView.findViewById(R.id.symptomsText)
        private val treatmentText: TextView = itemView.findViewById(R.id.treatmentText)
        private val treatmentSection: LinearLayout = itemView.findViewById(R.id.treatmentSection)
        private val expandTreatment: TextView = itemView.findViewById(R.id.expandTreatment)
        private val thumbsUpButton: ImageButton = itemView.findViewById(R.id.thumbsUpButton)
        private val thumbsDownButton: ImageButton = itemView.findViewById(R.id.thumbsDownButton)
        private val attentionOverlayContainer: LinearLayout = itemView.findViewById(R.id.attentionOverlayContainer)
        private val attentionOverlayImage: ImageView = itemView.findViewById(R.id.attentionOverlayImage)
        private val overlayDescription: TextView = itemView.findViewById(R.id.overlayDescription)
        
        // Embedded prescription component
        private val embeddedPrescription: LinearLayout = itemView.findViewById(R.id.embeddedPrescription)
        
        fun bind(message: ChatMessage) {
            // Set disease name
            diseaseName.text = message.diseaseName ?: "Disease Detected"
            
            // Set confidence
            val confidencePercentage = ((message.confidence ?: 0.89) * 100).toInt()
            confidenceText.text = "Confidence: $confidencePercentage%"
            confidenceBar.progress = confidencePercentage
            
            // Set symptoms from message text or default
            val symptoms = extractSymptoms(message.text)
            symptomsText.text = symptoms
            
            // Handle attention overlay display FIRST for visibility debugging
            handleAttentionOverlay(message)
            
            // Check if we should embed prescription vs show simple treatment
            if (shouldEmbedPrescription(message)) {
                // Show embedded prescription component
                embeddedPrescription.visibility = View.VISIBLE
                treatmentSection.visibility = View.GONE
                populateEmbeddedPrescription(message)
                android.util.Log.d("DiseaseCardViewHolder", "✅ Showing embedded prescription component")
            } else if (hasTreatmentRecommendations(message)) {
                // Show simple treatment section
                embeddedPrescription.visibility = View.GONE
                treatmentSection.visibility = View.VISIBLE
                val serverTreatment = extractTreatment(message.text)
                if (serverTreatment.isNotBlank()) {
                    // Enable HTML formatting for bold text
                    treatmentText.text = android.text.Html.fromHtml(serverTreatment, android.text.Html.FROM_HTML_MODE_COMPACT)
                } else {
                    treatmentText.text = "Treatment recommendations are being processed..."
                }
                
                // Treatment section is initially visible, set up collapse/expand toggle
                var isExpanded = true  // Start as expanded since section is visible
                expandTreatment.text = "COLLAPSE"
                
                expandTreatment.setOnClickListener {
                    isExpanded = !isExpanded
                    treatmentSection.visibility = if (isExpanded) View.VISIBLE else View.GONE
                    expandTreatment.text = if (isExpanded) "COLLAPSE" else "EXPAND"
                    android.util.Log.d("DiseaseCardViewHolder", "Treatment section toggled: isExpanded=$isExpanded")
                }
                android.util.Log.d("DiseaseCardViewHolder", "✅ Showing simple treatment section")
            } else {
                // No treatment recommendations
                embeddedPrescription.visibility = View.GONE
                treatmentSection.visibility = View.GONE
                expandTreatment.setOnClickListener(null)
            }
            
            // Set up feedback buttons
            setupFeedbackButtons(message)
        }
        
        private fun extractSymptoms(text: String): String {
            // Extract actual symptoms from AI response
            return when {
                text.contains("symptoms:", ignoreCase = true) -> {
                    val symptomsIndex = text.lowercase().indexOf("symptoms:")
                    val afterSymptoms = text.substring(symptomsIndex)
                    val endMarkers = listOf("\n\ntreatment:", "\n\nrecommendations:", "\n\ndiagnosis:", "\n\n**", "action_items:")
                    var endIndex = afterSymptoms.length
                    for (marker in endMarkers) {
                        val markerIndex = afterSymptoms.lowercase().indexOf(marker.lowercase())
                        if (markerIndex > 0 && markerIndex < endIndex) {
                            endIndex = markerIndex
                        }
                    }
                    afterSymptoms.substring(0, endIndex).trim()
                }
                text.contains("observed:", ignoreCase = true) -> {
                    val observedIndex = text.lowercase().indexOf("observed:")
                    val afterObserved = text.substring(observedIndex)
                    val endMarkers = listOf("\n\ntreatment:", "\n\nrecommendations:", "\n\n**", "action_items:")
                    var endIndex = afterObserved.length
                    for (marker in endMarkers) {
                        val markerIndex = afterObserved.lowercase().indexOf(marker.lowercase())
                        if (markerIndex > 0 && markerIndex < endIndex) {
                            endIndex = markerIndex
                        }
                    }
                    afterObserved.substring(0, endIndex).trim()
                }
                else -> {
                    // If no specific symptoms section, look for common disease indicators in the text
                    val indicators = mutableListOf<String>()
                    if (text.contains("brown spots", ignoreCase = true)) indicators.add("• Dark brown spots with concentric rings")
                    if (text.contains("yellow", ignoreCase = true)) indicators.add("• Yellowing around affected areas")  
                    if (text.contains("wilting", ignoreCase = true)) indicators.add("• Wilting of plant tissue")
                    if (text.contains("lesions", ignoreCase = true)) indicators.add("• Visible lesions on plant surface")
                    if (indicators.isNotEmpty()) {
                        indicators.joinToString("\n")
                    } else {
                        "• Disease symptoms detected through AI analysis\n• Visual patterns identified\n• Further examination recommended"
                    }
                }
            }
        }
        
        private fun extractTreatment(text: String): String {
            android.util.Log.d("DiseaseCardViewHolder", "🔍 Extracting treatment from text (length: ${text.length})")
            
            // Try specific markers first (preferred format)
            val startMarker = "TREATMENT FOR YOUR PLANT"
            val endMarker = "Your plant will get better with proper care!"
            
            if (text.contains(startMarker, ignoreCase = true) && text.contains(endMarker, ignoreCase = true)) {
                val startIndex = text.indexOf(startMarker, ignoreCase = true)
                val endIndex = text.indexOf(endMarker, ignoreCase = true) + endMarker.length
                
                if (startIndex >= 0 && endIndex > startIndex) {
                    val extractedText = text.substring(startIndex, endIndex).trim()
                    android.util.Log.d("DiseaseCardViewHolder", "✅ Extracted between specific markers (${extractedText.length} chars)")
                    return applyBoldFormatting(extractedText)
                }
            }
            
            // Fallback: Extract treatment sections from server response
            android.util.Log.d("DiseaseCardViewHolder", "🔄 Trying fallback extraction methods...")
            
            when {
                text.contains("treatment:", ignoreCase = true) -> {
                    val treatmentIndex = text.lowercase().indexOf("treatment:")
                    val afterTreatment = text.substring(treatmentIndex)
                    // Extract until double newline or next section
                    val endMarkers = listOf("\n\nfollow-up:", "\n\nrecommendations:", "\n\n**", "\n\nconfidence:")
                    var endIndex = afterTreatment.length
                    for (marker in endMarkers) {
                        val markerIndex = afterTreatment.lowercase().indexOf(marker.lowercase())
                        if (markerIndex > 0 && markerIndex < endIndex) {
                            endIndex = markerIndex
                        }
                    }
                    val extracted = afterTreatment.substring(0, endIndex).trim()
                    android.util.Log.d("DiseaseCardViewHolder", "✅ Extracted via 'treatment:' (${extracted.length} chars)")
                    return applyBoldFormatting(extracted)
                }
                text.contains("recommendations:", ignoreCase = true) -> {
                    val recIndex = text.lowercase().indexOf("recommendations:")
                    val afterRec = text.substring(recIndex)
                    val endMarkers = listOf("\n\nfollow-up:", "\n\ntreatment:", "\n\n**", "\n\nconfidence:")
                    var endIndex = afterRec.length
                    for (marker in endMarkers) {
                        val markerIndex = afterRec.lowercase().indexOf(marker.lowercase())
                        if (markerIndex > 0 && markerIndex < endIndex) {
                            endIndex = markerIndex
                        }
                    }
                    val extracted = afterRec.substring(0, endIndex).trim()
                    android.util.Log.d("DiseaseCardViewHolder", "✅ Extracted via 'recommendations:' (${extracted.length} chars)")
                    return applyBoldFormatting(extracted)
                }
                // If text contains treatment keywords, show relevant portion
                (text.contains("apply", ignoreCase = true) || text.contains("spray", ignoreCase = true) || 
                 text.contains("fungicide", ignoreCase = true) || text.contains("remove", ignoreCase = true)) -> {
                    // Extract the whole message if it contains treatment keywords
                    android.util.Log.d("DiseaseCardViewHolder", "✅ Extracted full text with treatment keywords (${text.length} chars)")
                    return applyBoldFormatting(text.trim())
                }
                else -> {
                    android.util.Log.d("DiseaseCardViewHolder", "❌ No treatment content found in any format")
                    return ""
                }
            }
        }
        
        private fun applyBoldFormatting(text: String): String {
            // Convert **text** to bold formatting (like WhatsApp) and preserve line breaks
            var formattedText = text.replace(Regex("\\*\\*(.*?)\\*\\*")) { matchResult ->
                "<b>${matchResult.groupValues[1]}</b>"
            }
            
            // Convert newlines to HTML line breaks for proper display
            formattedText = formattedText.replace("\n", "<br>")
            
            android.util.Log.d("DiseaseCardViewHolder", "Applied formatting: original(${text.length}) -> formatted(${formattedText.length})")
            return formattedText
        }
        
        private fun populateEmbeddedPrescription(message: ChatMessage) {
            try {
                // Get prescription component views
                val prescriptionDate = embeddedPrescription.findViewById<TextView>(R.id.prescriptionDate)
                val immediateAction = embeddedPrescription.findViewById<TextView>(R.id.immediateAction)
                val medicineApplication = embeddedPrescription.findViewById<TextView>(R.id.medicineApplication)
                val repeatSchedule = embeddedPrescription.findViewById<TextView>(R.id.repeatSchedule)
                val monitorCare = embeddedPrescription.findViewById<TextView>(R.id.monitorCare)
                val treatmentDuration = embeddedPrescription.findViewById<TextView>(R.id.treatmentDuration)
                
                // Set current date
                val dateFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())
                prescriptionDate?.text = dateFormatter.format(Date())
                
                // PRIORITY 1: Use structured prescription data if available
                if (message.structuredPrescription != null) {
                    android.util.Log.d("DiseaseCardViewHolder", "📋 Using STRUCTURED prescription data")
                    populateFromStructuredData(message.structuredPrescription, immediateAction, medicineApplication, repeatSchedule, monitorCare, treatmentDuration)
                } else {
                    // PRIORITY 2: Fall back to text parsing (backward compatibility)
                    android.util.Log.d("DiseaseCardViewHolder", "📋 Falling back to TEXT parsing for prescription")
                    populateFromTextParsing(message, immediateAction, medicineApplication, repeatSchedule, monitorCare, treatmentDuration)
                }
                
                android.util.Log.d("DiseaseCardViewHolder", "✅ Prescription populated successfully")
            } catch (e: Exception) {
                android.util.Log.e("DiseaseCardViewHolder", "❌ Error populating embedded prescription", e)
            }
        }
        
        private fun populateFromStructuredData(
            prescription: com.sasya.arogya.fsm.StructuredPrescription,
            immediateAction: TextView?, 
            medicineApplication: TextView?, 
            repeatSchedule: TextView?, 
            monitorCare: TextView?,
            treatmentDuration: TextView?
        ) {
            // Step 1: Immediate Action - from immediate_treatment.actions
            val immediateStep = prescription.immediateTreatment?.actions?.firstOrNull() 
                ?: prescription.immediateTreatment?.emergencyMeasures?.firstOrNull()
                ?: "Remove affected plant parts immediately"
            immediateAction?.text = immediateStep
            
            // Step 2: Medicine Application - from medicine_recommendations.primary_treatment
            val primaryTreatment = prescription.medicineRecommendations?.primaryTreatment
            val medicineStep = if (primaryTreatment != null) {
                buildString {
                    append(primaryTreatment.applicationMethod ?: "Apply")
                    append(" ")
                    append(primaryTreatment.medicineName ?: "prescribed medicine")
                    if (primaryTreatment.dosage != null) {
                        append(" at ")
                        append(primaryTreatment.dosage)
                    }
                }
            } else {
                "Apply prescribed plant medicine as directed"
            }
            medicineApplication?.text = medicineStep
            
            // Step 3: Repeat Schedule - from primary_treatment.frequency
            val scheduleStep = primaryTreatment?.frequency?.let { frequency ->
                "$frequency, early morning for best results"
            } ?: "Follow recommended schedule, early morning"
            repeatSchedule?.text = scheduleStep
            
            // Step 4: Monitor & Care - from prevention and additional_notes
            val carePoints = mutableListOf<String>()
            
            // Add monitoring from weekly plan
            prescription.weeklyTreatmentPlan?.week1?.monitoring?.let { monitoring ->
                carePoints.add(monitoring.lowercase().replace("monitor ", ""))
            }
            
            // Add prevention practices
            prescription.prevention?.culturalPractices?.firstOrNull()?.let { practice ->
                carePoints.add(practice.lowercase())
            }
            
            val monitorStep = if (carePoints.isNotEmpty()) {
                "Check daily, ${carePoints.take(2).joinToString(", ")}"
            } else {
                "Monitor plant daily for improvement"
            }
            monitorCare?.text = monitorStep
            
            // Step 5: Treatment Duration - from primary_treatment.duration or additional_notes
            val durationStep = primaryTreatment?.duration
                ?: prescription.additionalNotes?.followUp
                ?: "Continue treatment until improvement is visible (usually 2-3 weeks)"
            treatmentDuration?.text = durationStep
        }
        
        private fun populateFromTextParsing(
            message: ChatMessage, 
            immediateAction: TextView?, 
            medicineApplication: TextView?, 
            repeatSchedule: TextView?, 
            monitorCare: TextView?,
            treatmentDuration: TextView?
        ) {
            // Backward compatibility: Extract and format treatment steps from text
            val treatment = extractTreatment(message.text)
            android.util.Log.d("DiseaseCardViewHolder", "📋 Text parsing treatment: ${treatment.take(100)}...")
            
            // Step 1: Immediate Action
            val immediateStep = extractImmediateAction(treatment, message.text)
            immediateAction?.text = immediateStep
            
            // Step 2: Medicine Application
            val medicineStep = extractMedicineApplication(treatment, message.text)
            medicineApplication?.text = medicineStep
            
            // Step 3: Repeat Schedule
            val scheduleStep = extractRepeatSchedule(treatment, message.text)
            repeatSchedule?.text = scheduleStep
            
            // Step 4: Monitor & Care
            val monitorStep = extractMonitorCare(treatment, message.text)
            monitorCare?.text = monitorStep
            
            // Step 5: Treatment Duration - extract from text
            val durationStep = extractTreatmentDuration(treatment, message.text)
            treatmentDuration?.text = durationStep
        }
        
        private fun extractTreatmentDuration(treatment: String, fullText: String): String {
            return when {
                fullText.contains("2-3 weeks", ignoreCase = true) -> "Continue for 2-3 weeks until improvement"
                fullText.contains("1-2 weeks", ignoreCase = true) -> "Apply for 1-2 weeks then reassess"
                fullText.contains("until", ignoreCase = true) && fullText.contains("improve", ignoreCase = true) ->
                    "Continue until improvement is visible"
                fullText.contains("weekly", ignoreCase = true) -> "Weekly applications for 3-4 weeks"
                else -> "Continue treatment until new healthy growth appears (usually 2-3 weeks)"
            }
        }
        
        private fun extractImmediateAction(treatment: String, fullText: String): String {
            return when {
                fullText.contains("remove", ignoreCase = true) && fullText.contains("leaves", ignoreCase = true) ->
                    "Remove affected leaves and dispose safely"
                fullText.contains("isolate", ignoreCase = true) ->
                    "Isolate affected plants from healthy ones"
                else -> "Remove affected plant parts and clean area"
            }
        }
        
        private fun extractMedicineApplication(treatment: String, fullText: String): String {
            return when {
                fullText.contains("copper", ignoreCase = true) && fullText.contains("fungicide", ignoreCase = true) ->
                    "Spray copper-based fungicide solution"
                fullText.contains("fungicide", ignoreCase = true) ->
                    "Apply fungicide spray to affected areas"
                fullText.contains("spray", ignoreCase = true) ->
                    "Apply recommended plant spray"
                else -> "Apply prescribed plant medicine"
            }
        }
        
        private fun extractRepeatSchedule(treatment: String, fullText: String): String {
            return when {
                fullText.contains("every 7", ignoreCase = true) || fullText.contains("weekly", ignoreCase = true) ->
                    "Every 7 days, early morning"
                fullText.contains("every 10", ignoreCase = true) || fullText.contains("7-10 days", ignoreCase = true) ->
                    "Every 7-10 days, early morning"
                fullText.contains("daily", ignoreCase = true) ->
                    "Daily application, early morning"
                else -> "Follow recommended schedule, early morning"
            }
        }
        
        private fun extractMonitorCare(treatment: String, fullText: String): String {
            val carePoints = mutableListOf<String>()
            
            if (fullText.contains("water", ignoreCase = true) || fullText.contains("overhead", ignoreCase = true)) {
                carePoints.add("avoid overhead watering")
            }
            if (fullText.contains("circulation", ignoreCase = true) || fullText.contains("air", ignoreCase = true)) {
                carePoints.add("improve air circulation")
            }
            
            return if (carePoints.isNotEmpty()) {
                "Check daily, ${carePoints.joinToString(", ")}"
            } else {
                "Check daily, maintain proper plant care"
            }
        }
        
        private fun setupFeedbackButtons(message: ChatMessage) {
            thumbsUpButton.setOnClickListener {
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_up_selected)
                )
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                onThumbsUpClick(message)
            }
            
            thumbsDownButton.setOnClickListener {
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_down_selected)
                )
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                onThumbsDownClick(message)
            }
        }
        
        private fun handleAttentionOverlay(message: ChatMessage) {
            // TEMPORARY: Always show overlay for testing
            attentionOverlayContainer.visibility = View.VISIBLE
            
            if (message.attentionOverlayBase64 != null) {
                // Update description with disease info and clickable hint
                val description = if (message.diseaseName != null && message.confidence != null) {
                    "Detected: ${message.diseaseName} (${String.format("%.1f", message.confidence * 100)}% confidence) • Tap to zoom & inspect"
                } else {
                    "AI attention heatmap showing diagnostic focus areas • Tap to zoom & inspect"
                }
                overlayDescription.text = description
                
                // Decode and display the base64 attention overlay image
                try {
                    val imageBytes = android.util.Base64.decode(message.attentionOverlayBase64, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    attentionOverlayImage.setImageBitmap(bitmap)
                    
                    // Make the overlay clickable to open in full-screen modal
                    attentionOverlayImage.setOnClickListener {
                        showAttentionOverlayModal(bitmap, message.diseaseName, message.confidence)
                    }
                    
                    // Add visual feedback for clickability
                    attentionOverlayImage.background = ContextCompat.getDrawable(
                        itemView.context, 
                        R.drawable.button_ripple
                    )
                    
                } catch (e: Exception) {
                    android.util.Log.e("DiseaseCardViewHolder", "Error decoding attention overlay image", e)
                    // Still show container but with placeholder
                    showPlaceholderOverlay()
                }
            } else {
                // Show placeholder for testing
                showPlaceholderOverlay()
            }
        }
        
        private fun showPlaceholderOverlay() {
            overlayDescription.text = "🎯 AI Diagnostic Visualization (Placeholder for testing)"
            
            // Create a simple test bitmap
            val bitmap = android.graphics.Bitmap.createBitmap(400, 200, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLUE
                textSize = 40f
                isAntiAlias = true
            }
            canvas.drawColor(android.graphics.Color.LTGRAY)
            canvas.drawText("AI Attention", 50f, 100f, paint)
            canvas.drawText("Overlay Test", 50f, 150f, paint)
            
            attentionOverlayImage.setImageBitmap(bitmap)
            attentionOverlayImage.background = ContextCompat.getDrawable(
                itemView.context, 
                R.drawable.button_ripple
            )
        }
        
        private fun showAttentionOverlayModal(bitmap: android.graphics.Bitmap, diseaseName: String?, confidence: Double?) {
            // Create custom dialog
            val dialog = android.app.Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_attention_overlay)
            
            // Set up dialog views
            val imgOverlay = dialog.findViewById<ImageView>(R.id.imgAttentionOverlay)
            val tvDiseaseInfo = dialog.findViewById<TextView>(R.id.tvDiseaseInfo)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnClose)
            val zoomInstructions = dialog.findViewById<TextView>(R.id.zoomInstructions)
            
            // Display the full-size overlay image
            imgOverlay.setImageBitmap(bitmap)
            
            // Update disease information
            val diseaseText = if (diseaseName != null && confidence != null) {
                "Detected: $diseaseName (${String.format("%.1f", confidence * 100)}% confidence)"
            } else {
                "AI Attention Analysis - Diagnostic Focus Areas"
            }
            tvDiseaseInfo.text = diseaseText
            
            // Set up zoom functionality (simplified version)
            setupImageZoom(imgOverlay, zoomInstructions)
            
            // Close button functionality
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Allow tapping outside to close
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            
            // Show the dialog
            dialog.show()
            
            android.util.Log.d("DiseaseCardViewHolder", "🎯 Opened attention overlay modal for detailed inspection")
        }
        
        private fun setupImageZoom(imageView: ImageView, instructionsView: TextView) {
            // Simplified zoom setup - basic scale gesture detection
            val matrix = android.graphics.Matrix()
            var scale = 1f
            
            val scaleDetector = android.view.ScaleGestureDetector(itemView.context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    scale *= detector.scaleFactor
                    scale = Math.max(0.1f, Math.min(scale, 5.0f))
                    
                    matrix.reset()
                    matrix.postScale(scale, scale)
                    imageView.imageMatrix = matrix
                    imageView.invalidate()
                    
                    return true
                }
            })
            
            imageView.setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                true
            }
            
            android.util.Log.d("DiseaseCardViewHolder", "🔍 Zoom functionality initialized for attention overlay")
        }
    }
    
    /**
     * ViewHolder for healthy plant cards
     */
    inner class HealthyCardViewHolder(
        itemView: View,
        private val onFollowUpClick: (String) -> Unit,
        private val onThumbsUpClick: (ChatMessage) -> Unit,
        private val onThumbsDownClick: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val healthyTitle: TextView = itemView.findViewById(R.id.healthyTitle)
        private val healthyConfidence: TextView = itemView.findViewById(R.id.healthyConfidence)
        private val thumbsUpButton: ImageButton = itemView.findViewById(R.id.thumbsUpButton)
        private val thumbsDownButton: ImageButton = itemView.findViewById(R.id.thumbsDownButton)
        private val attentionOverlayContainer: LinearLayout = itemView.findViewById(R.id.attentionOverlayContainer)
        private val attentionOverlayImage: ImageView = itemView.findViewById(R.id.attentionOverlayImage)
        private val overlayDescription: TextView = itemView.findViewById(R.id.overlayDescription)
        
        fun bind(message: ChatMessage) {
            android.util.Log.d("HealthyCardViewHolder", "🌱 Binding healthy card - confidence: ${message.confidence}, diseaseName: '${message.diseaseName}', text contains 'healthy': ${message.text.contains("healthy", ignoreCase = true)}")
            
            healthyTitle.text = "Plant Looks Healthy!"
            
            val confidencePercentage = ((message.confidence ?: 0.94) * 100).toInt()
            healthyConfidence.text = "Confidence: $confidencePercentage%"
            
            // FORCE GREEN BACKGROUND for healthy cards - find the main LinearLayout
            try {
                // The first child of the CardView should be the LinearLayout with background
                val mainLinearLayout = (itemView as androidx.cardview.widget.CardView).getChildAt(0) as LinearLayout
                mainLinearLayout.setBackgroundResource(R.drawable.healthy_card_background)
                android.util.Log.d("HealthyCardViewHolder", "✅ Green background applied to healthy card")
            } catch (e: Exception) {
                android.util.Log.e("HealthyCardViewHolder", "Failed to set green background", e)
            }
            
            // Handle attention overlay display for healthy plants
            handleHealthyAttentionOverlay(message)
            
            // Set up feedback buttons
            setupFeedbackButtons(message)
        }
        
        private fun handleHealthyAttentionOverlay(message: ChatMessage) {
            // TEMPORARY: Always show overlay for testing
            attentionOverlayContainer.visibility = View.VISIBLE
            
            if (message.attentionOverlayBase64 != null) {
                // Update description for healthy analysis
                overlayDescription.text = "AI health analysis showing robust plant areas • Tap to zoom"
                
                // Decode and display the base64 attention overlay image
                try {
                    val imageBytes = android.util.Base64.decode(message.attentionOverlayBase64, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    attentionOverlayImage.setImageBitmap(bitmap)
                    
                    // Make the overlay clickable to open in full-screen modal
                    attentionOverlayImage.setOnClickListener {
                        showHealthyOverlayModal(bitmap, message.confidence)
                    }
                    
                    // Add visual feedback for clickability
                    attentionOverlayImage.background = ContextCompat.getDrawable(
                        itemView.context, 
                        R.drawable.button_ripple
                    )
                    
                } catch (e: Exception) {
                    android.util.Log.e("HealthyCardViewHolder", "Error decoding attention overlay image", e)
                    // Still show container but with placeholder
                    showHealthyPlaceholderOverlay()
                }
            } else {
                // Show placeholder for testing
                showHealthyPlaceholderOverlay()
            }
        }
        
        private fun showHealthyPlaceholderOverlay() {
            overlayDescription.text = "🔍 AI Health Analysis (Placeholder for testing)"
            
            // Create a simple test bitmap
            val bitmap = android.graphics.Bitmap.createBitmap(400, 200, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.GREEN
                textSize = 40f
                isAntiAlias = true
            }
            canvas.drawColor(android.graphics.Color.parseColor("#E8F5E8"))
            canvas.drawText("Healthy Plant", 50f, 100f, paint)
            canvas.drawText("Analysis", 50f, 150f, paint)
            
            attentionOverlayImage.setImageBitmap(bitmap)
            attentionOverlayImage.background = ContextCompat.getDrawable(
                itemView.context, 
                R.drawable.button_ripple
            )
        }
        
        private fun showHealthyOverlayModal(bitmap: android.graphics.Bitmap, confidence: Double?) {
            // Create custom dialog for healthy plant analysis
            val dialog = android.app.Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_attention_overlay)
            
            // Set up dialog views
            val imgOverlay = dialog.findViewById<ImageView>(R.id.imgAttentionOverlay)
            val tvDiseaseInfo = dialog.findViewById<TextView>(R.id.tvDiseaseInfo)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnClose)
            val zoomInstructions = dialog.findViewById<TextView>(R.id.zoomInstructions)
            
            // Display the full-size overlay image
            imgOverlay.setImageBitmap(bitmap)
            
            // Update health information
            val healthText = if (confidence != null) {
                "Plant Health Analysis (${String.format("%.1f", confidence * 100)}% confidence)"
            } else {
                "AI Health Analysis - Showing Healthy Plant Areas"
            }
            tvDiseaseInfo.text = healthText
            
            // Set up zoom functionality
            setupImageZoom(imgOverlay, zoomInstructions)
            
            // Close button functionality
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Allow tapping outside to close
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            
            // Show the dialog
            dialog.show()
            
            android.util.Log.d("HealthyCardViewHolder", "🌱 Opened healthy plant analysis modal")
        }
        
        private fun setupImageZoom(imageView: ImageView, instructionsView: TextView) {
            // Simplified zoom setup for healthy plant analysis
            val matrix = android.graphics.Matrix()
            var scale = 1f
            
            val scaleDetector = android.view.ScaleGestureDetector(itemView.context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    scale *= detector.scaleFactor
                    scale = Math.max(0.1f, Math.min(scale, 5.0f))
                    
                    matrix.reset()
                    matrix.postScale(scale, scale)
                    imageView.imageMatrix = matrix
                    imageView.invalidate()
                    
                    return true
                }
            })
            
            imageView.setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                true
            }
            
            android.util.Log.d("HealthyCardViewHolder", "🔍 Zoom functionality initialized for healthy analysis")
        }
        
        private fun setupFeedbackButtons(message: ChatMessage) {
            thumbsUpButton.setOnClickListener {
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_up_selected)
                )
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                onThumbsUpClick(message)
            }
            
            thumbsDownButton.setOnClickListener {
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_down_selected)
                )
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                onThumbsDownClick(message)
            }
        }
    }
    
    /**
     * ViewHolder for severe disease cards
     */
    inner class SevereDiseaseCardViewHolder(
        itemView: View,
        private val onFollowUpClick: (String) -> Unit,
        private val onThumbsUpClick: (ChatMessage) -> Unit,
        private val onThumbsDownClick: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val diseaseName: TextView = itemView.findViewById(R.id.diseaseName)
        private val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
        private val confidenceBar: ProgressBar = itemView.findViewById(R.id.confidenceBar)
        private val statusBadge: TextView = itemView.findViewById(R.id.statusBadge)
        private val symptomsText: TextView = itemView.findViewById(R.id.symptomsText)
        private val treatmentText: TextView = itemView.findViewById(R.id.treatmentText)
        private val treatmentSection: LinearLayout = itemView.findViewById(R.id.treatmentSection)
        private val cardBackground: LinearLayout = itemView.findViewById(R.id.cardBackground)
        private val diseaseIconContainer: LinearLayout = itemView.findViewById(R.id.diseaseIconContainer)
        private val thumbsUpButton: ImageButton = itemView.findViewById(R.id.thumbsUpButton)
        private val thumbsDownButton: ImageButton = itemView.findViewById(R.id.thumbsDownButton)
        private val attentionOverlayContainer: LinearLayout = itemView.findViewById(R.id.attentionOverlayContainer)
        private val attentionOverlayImage: ImageView = itemView.findViewById(R.id.attentionOverlayImage)
        private val overlayDescription: TextView = itemView.findViewById(R.id.overlayDescription)
        
        fun bind(message: ChatMessage) {
            // Set disease name with severity indicator
            diseaseName.text = "${message.diseaseName ?: "Severe Disease"} - CRITICAL"
            
            // Set confidence
            val confidencePercentage = ((message.confidence ?: 0.96) * 100).toInt()
            confidenceText.text = "Confidence: $confidencePercentage%"
            confidenceBar.progress = confidencePercentage
            
            // Change to severe styling
            statusBadge.text = "CRITICAL"
            statusBadge.setBackgroundResource(R.drawable.status_badge_disease)
            cardBackground.setBackgroundResource(R.drawable.severe_disease_card_background)
            
            // Set critical symptoms
            symptomsText.text = "• Large spreading lesions\n• Rapid progression observed\n• Plant health severely compromised\n• Immediate intervention required"
            
            // Show treatment for severe cases only if recommendations are available
            if (hasTreatmentRecommendations(message)) {
                treatmentSection.visibility = View.VISIBLE
                val serverTreatment = extractTreatment(message.text)
                if (serverTreatment.isNotBlank()) {
                    // Enable HTML formatting for bold text in severe cases
                    val criticalText = "🚨 CRITICAL: $serverTreatment"
                    treatmentText.text = android.text.Html.fromHtml(criticalText, android.text.Html.FROM_HTML_MODE_COMPACT)
                } else {
                    treatmentText.text = "EMERGENCY TREATMENT REQUIRED:\n\n1. 🚨 Isolate affected plants immediately\n\n2. 💊 Apply systemic fungicide within 24 hours\n\n3. 🔥 Remove and destroy infected material\n\n4. 📞 Contact agricultural extension service"
                }
            } else {
                treatmentSection.visibility = View.GONE
                treatmentText.text = "Treatment recommendations will appear when available from the AI analysis."
            }
            
            // Handle attention overlay display for severe cases
            handleAttentionOverlay(message)
            
            // Set up feedback buttons
            setupFeedbackButtons(message)
        }
        
        private fun handleAttentionOverlay(message: ChatMessage) {
            // TEMPORARY: Always show overlay for testing
            attentionOverlayContainer.visibility = View.VISIBLE
            
            if (message.attentionOverlayBase64 != null) {
                // Update description with severity emphasis
                val description = if (message.diseaseName != null && message.confidence != null) {
                    "🚨 CRITICAL: ${message.diseaseName} (${String.format("%.1f", message.confidence * 100)}% confidence) • Tap to zoom & inspect"
                } else {
                    "🚨 CRITICAL AI analysis showing severe disease areas • Tap to zoom & inspect"
                }
                overlayDescription.text = description
                
                // Decode and display the base64 attention overlay image
                try {
                    val imageBytes = android.util.Base64.decode(message.attentionOverlayBase64, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    attentionOverlayImage.setImageBitmap(bitmap)
                    
                    // Make the overlay clickable to open in full-screen modal
                    attentionOverlayImage.setOnClickListener {
                        showAttentionOverlayModal(bitmap, message.diseaseName, message.confidence)
                    }
                    
                    // Add visual feedback for clickability
                    attentionOverlayImage.background = ContextCompat.getDrawable(
                        itemView.context, 
                        R.drawable.button_ripple
                    )
                    
                } catch (e: Exception) {
                    android.util.Log.e("SevereDiseaseCardViewHolder", "Error decoding attention overlay image", e)
                    // Still show container but with placeholder
                    showCriticalPlaceholderOverlay()
                }
            } else {
                // Show placeholder for testing
                showCriticalPlaceholderOverlay()
            }
        }
        
        private fun showCriticalPlaceholderOverlay() {
            overlayDescription.text = "🚨 CRITICAL AI Analysis (Placeholder for testing)"
            
            // Create a simple test bitmap
            val bitmap = android.graphics.Bitmap.createBitmap(400, 200, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                textSize = 40f
                isAntiAlias = true
            }
            canvas.drawColor(android.graphics.Color.parseColor("#FFEBEE"))
            canvas.drawText("CRITICAL", 50f, 100f, paint)
            canvas.drawText("ANALYSIS", 50f, 150f, paint)
            
            attentionOverlayImage.setImageBitmap(bitmap)
            attentionOverlayImage.background = ContextCompat.getDrawable(
                itemView.context, 
                R.drawable.button_ripple
            )
        }
        
        private fun showAttentionOverlayModal(bitmap: android.graphics.Bitmap, diseaseName: String?, confidence: Double?) {
            // Create custom dialog for severe disease analysis
            val dialog = android.app.Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_attention_overlay)
            
            // Set up dialog views
            val imgOverlay = dialog.findViewById<ImageView>(R.id.imgAttentionOverlay)
            val tvDiseaseInfo = dialog.findViewById<TextView>(R.id.tvDiseaseInfo)
            val btnClose = dialog.findViewById<ImageButton>(R.id.btnClose)
            val zoomInstructions = dialog.findViewById<TextView>(R.id.zoomInstructions)
            
            // Display the full-size overlay image
            imgOverlay.setImageBitmap(bitmap)
            
            // Update severe disease information
            val diseaseText = if (diseaseName != null && confidence != null) {
                "🚨 CRITICAL: $diseaseName (${String.format("%.1f", confidence * 100)}% confidence)"
            } else {
                "🚨 CRITICAL AI Analysis - Severe Disease Detection"
            }
            tvDiseaseInfo.text = diseaseText
            
            // Set up zoom functionality
            setupImageZoom(imgOverlay, zoomInstructions)
            
            // Close button functionality
            btnClose.setOnClickListener {
                dialog.dismiss()
            }
            
            // Allow tapping outside to close
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            
            // Show the dialog
            dialog.show()
            
            android.util.Log.d("SevereDiseaseCardViewHolder", "🚨 Opened severe disease attention overlay modal")
        }
        
        private fun setupImageZoom(imageView: ImageView, instructionsView: TextView) {
            // Simplified zoom setup for severe disease analysis
            val matrix = android.graphics.Matrix()
            var scale = 1f
            
            val scaleDetector = android.view.ScaleGestureDetector(itemView.context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    scale *= detector.scaleFactor
                    scale = Math.max(0.1f, Math.min(scale, 5.0f))
                    
                    matrix.reset()
                    matrix.postScale(scale, scale)
                    imageView.imageMatrix = matrix
                    imageView.invalidate()
                    
                    return true
                }
            })
            
            imageView.setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                true
            }
            
            android.util.Log.d("SevereDiseaseCardViewHolder", "🔍 Zoom functionality initialized for severe disease analysis")
        }
        
        private fun setupFeedbackButtons(message: ChatMessage) {
            thumbsUpButton.setOnClickListener {
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_up_selected)
                )
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                onThumbsUpClick(message)
            }
            
            thumbsDownButton.setOnClickListener {
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_down_selected)
                )
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                onThumbsDownClick(message)
            }
        }
        
        private fun extractTreatment(text: String): String {
            android.util.Log.d("SevereDiseaseCardViewHolder", "🔍 Extracting SEVERE treatment from text (length: ${text.length})")
            
            // Try specific markers first (preferred format)
            val startMarker = "TREATMENT FOR YOUR PLANT"
            val endMarker = "Your plant will get better with proper care!"
            
            if (text.contains(startMarker, ignoreCase = true) && text.contains(endMarker, ignoreCase = true)) {
                val startIndex = text.indexOf(startMarker, ignoreCase = true)
                val endIndex = text.indexOf(endMarker, ignoreCase = true) + endMarker.length
                
                if (startIndex >= 0 && endIndex > startIndex) {
                    val extractedText = text.substring(startIndex, endIndex).trim()
                    android.util.Log.d("SevereDiseaseCardViewHolder", "✅ Extracted between specific markers (${extractedText.length} chars)")
                    return applyBoldFormatting(extractedText)
                }
            }
            
            // Fallback: Extract treatment sections from server response for SEVERE cases
            android.util.Log.d("SevereDiseaseCardViewHolder", "🔄 Trying fallback extraction methods for SEVERE case...")
            
            when {
                text.contains("treatment:", ignoreCase = true) -> {
                    val treatmentIndex = text.lowercase().indexOf("treatment:")
                    val afterTreatment = text.substring(treatmentIndex)
                    val endMarkers = listOf("\n\nfollow-up:", "\n\nrecommendations:", "\n\n**", "\n\nconfidence:")
                    var endIndex = afterTreatment.length
                    for (marker in endMarkers) {
                        val markerIndex = afterTreatment.lowercase().indexOf(marker.lowercase())
                        if (markerIndex > 0 && markerIndex < endIndex) {
                            endIndex = markerIndex
                        }
                    }
                    val extracted = afterTreatment.substring(0, endIndex).trim()
                    android.util.Log.d("SevereDiseaseCardViewHolder", "✅ SEVERE extracted via 'treatment:' (${extracted.length} chars)")
                    return applyBoldFormatting(extracted)
                }
                text.contains("recommendations:", ignoreCase = true) -> {
                    val recIndex = text.lowercase().indexOf("recommendations:")
                    val afterRec = text.substring(recIndex)
                    val endMarkers = listOf("\n\nfollow-up:", "\n\ntreatment:", "\n\n**", "\n\nconfidence:")
                    var endIndex = afterRec.length
                    for (marker in endMarkers) {
                        val markerIndex = afterRec.lowercase().indexOf(marker.lowercase())
                        if (markerIndex > 0 && markerIndex < endIndex) {
                            endIndex = markerIndex
                        }
                    }
                    val extracted = afterRec.substring(0, endIndex).trim()
                    android.util.Log.d("SevereDiseaseCardViewHolder", "✅ SEVERE extracted via 'recommendations:' (${extracted.length} chars)")
                    return applyBoldFormatting(extracted)
                }
                // If text contains treatment keywords, show relevant portion
                (text.contains("apply", ignoreCase = true) || text.contains("spray", ignoreCase = true) || 
                 text.contains("fungicide", ignoreCase = true) || text.contains("remove", ignoreCase = true)) -> {
                    android.util.Log.d("SevereDiseaseCardViewHolder", "✅ SEVERE extracted full text with treatment keywords (${text.length} chars)")
                    return applyBoldFormatting(text.trim())
                }
                else -> {
                    android.util.Log.d("SevereDiseaseCardViewHolder", "❌ No SEVERE treatment content found in any format")
                    return ""
                }
            }
        }
        
        private fun applyBoldFormatting(text: String): String {
            // Convert **text** to bold formatting (like WhatsApp) and preserve line breaks
            var formattedText = text.replace(Regex("\\*\\*(.*?)\\*\\*")) { matchResult ->
                "<b>${matchResult.groupValues[1]}</b>"
            }
            
            // Convert newlines to HTML line breaks for proper display
            formattedText = formattedText.replace("\n", "<br>")
            
            android.util.Log.d("SevereDiseaseCardViewHolder", "Applied formatting: original(${text.length}) -> formatted(${formattedText.length})")
            return formattedText
        }
    }
    
    /**
     * ViewHolder for prescription cards - pharmacy-style treatment recommendations
     */
    inner class PrescriptionCardViewHolder(
        itemView: View,
        private val onFollowUpClick: (String) -> Unit,
        private val onThumbsUpClick: (ChatMessage) -> Unit,
        private val onThumbsDownClick: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val prescriptionDate: TextView = itemView.findViewById(R.id.prescriptionDate)
        private val plantType: TextView = itemView.findViewById(R.id.plantType)
        private val diagnosisName: TextView = itemView.findViewById(R.id.diagnosisName)
        private val thumbsUpButton: ImageButton = itemView.findViewById(R.id.thumbsUpButton)
        private val thumbsDownButton: ImageButton = itemView.findViewById(R.id.thumbsDownButton)
        
        fun bind(message: ChatMessage) {
            android.util.Log.d("PrescriptionCardViewHolder", "℞ Binding standalone prescription card - structured: ${message.structuredPrescription != null}, diseaseName: '${message.diseaseName}', confidence: ${message.confidence}")
            
            // Set current date in header
            val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            prescriptionDate.text = dateFormatter.format(Date())
            
            // Populate patient information (unique to standalone)
            populatePatientInfo(message)
            
            // Populate shared prescription component (same as embedded)
            populateSharedPrescriptionComponent(message)
            
            // Set up feedback buttons
            setupFeedbackButtons(message)
        }
        
        private fun populatePatientInfo(message: ChatMessage) {
            // Plant type from structured data or text extraction
            plantType.text = if (message.structuredPrescription != null) {
                message.structuredPrescription.plantType?.replaceFirstChar { it.uppercase() } ?: "Plant"
            } else {
                extractPlantType(message.text)
            }
            
            // Disease name from structured data or message
            diagnosisName.text = if (message.structuredPrescription != null) {
                message.structuredPrescription.diagnosis?.diseaseName 
                    ?: message.structuredPrescription.diseaseName 
                    ?: message.diseaseName 
                    ?: "Plant Disease"
            } else {
                message.diseaseName ?: "Plant Disease"
            }
        }
        
        private fun populateSharedPrescriptionComponent(message: ChatMessage) {
            // Find the shared prescription component views
            val sharedComponent = itemView.findViewById<LinearLayout>(R.id.sharedPrescriptionComponent)
            val componentDate = sharedComponent.findViewById<TextView>(R.id.prescriptionDate)
            val immediateAction = sharedComponent.findViewById<TextView>(R.id.immediateAction)
            val medicineApplication = sharedComponent.findViewById<TextView>(R.id.medicineApplication)
            val repeatSchedule = sharedComponent.findViewById<TextView>(R.id.repeatSchedule)
            val monitorCare = sharedComponent.findViewById<TextView>(R.id.monitorCare)
            val treatmentDuration = sharedComponent.findViewById<TextView>(R.id.treatmentDuration)
            
            // Set date in component
            val dateFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())
            componentDate?.text = dateFormatter.format(Date())
            
            // Use the same logic as embedded prescription
            if (message.structuredPrescription != null) {
                android.util.Log.d("PrescriptionCardViewHolder", "📋 Using STRUCTURED prescription data for shared component")
                populateFromStructuredData(message.structuredPrescription, immediateAction, medicineApplication, repeatSchedule, monitorCare, treatmentDuration)
            } else {
                android.util.Log.d("PrescriptionCardViewHolder", "📋 Falling back to TEXT parsing for shared component")
                populateFromTextParsing(message, immediateAction, medicineApplication, repeatSchedule, monitorCare, treatmentDuration)
            }
        }
        
        // Reuse the same structured data population logic as DiseaseCardViewHolder
        private fun populateFromStructuredData(
            prescription: com.sasya.arogya.fsm.StructuredPrescription,
            immediateAction: TextView?, 
            medicineApplication: TextView?, 
            repeatSchedule: TextView?, 
            monitorCare: TextView?,
            treatmentDuration: TextView?
        ) {
            // Step 1: Immediate Action - from immediate_treatment.actions
            val immediateStep = prescription.immediateTreatment?.actions?.firstOrNull() 
                ?: prescription.immediateTreatment?.emergencyMeasures?.firstOrNull()
                ?: "Remove affected plant parts immediately"
            immediateAction?.text = immediateStep
            
            // Step 2: Medicine Application - from medicine_recommendations.primary_treatment
            val primaryTreatment = prescription.medicineRecommendations?.primaryTreatment
            val medicineStep = if (primaryTreatment != null) {
                buildString {
                    append(primaryTreatment.applicationMethod ?: "Apply")
                    append(" ")
                    append(primaryTreatment.medicineName ?: "prescribed medicine")
                    if (primaryTreatment.dosage != null) {
                        append(" at ")
                        append(primaryTreatment.dosage)
                    }
                }
            } else {
                "Apply prescribed plant medicine as directed"
            }
            medicineApplication?.text = medicineStep
            
            // Step 3: Repeat Schedule - from primary_treatment.frequency
            val scheduleStep = primaryTreatment?.frequency?.let { frequency ->
                "$frequency, early morning for best results"
            } ?: "Follow recommended schedule, early morning"
            repeatSchedule?.text = scheduleStep
            
            // Step 4: Monitor & Care - from prevention and additional_notes
            val carePoints = mutableListOf<String>()
            
            // Add monitoring from weekly plan
            prescription.weeklyTreatmentPlan?.week1?.monitoring?.let { monitoring ->
                carePoints.add(monitoring.lowercase().replace("monitor ", ""))
            }
            
            // Add prevention practices
            prescription.prevention?.culturalPractices?.firstOrNull()?.let { practice ->
                carePoints.add(practice.lowercase())
            }
            
            val monitorStep = if (carePoints.isNotEmpty()) {
                "Check daily, ${carePoints.take(2).joinToString(", ")}"
            } else {
                "Monitor plant daily for improvement"
            }
            monitorCare?.text = monitorStep
            
            // Step 5: Treatment Duration - from primary_treatment.duration or additional_notes
            val durationStep = primaryTreatment?.duration
                ?: prescription.additionalNotes?.followUp
                ?: "Continue treatment until improvement is visible (usually 2-3 weeks)"
            treatmentDuration?.text = durationStep
        }
        
        // Reuse the same text parsing logic as DiseaseCardViewHolder  
        private fun populateFromTextParsing(
            message: ChatMessage, 
            immediateAction: TextView?, 
            medicineApplication: TextView?, 
            repeatSchedule: TextView?, 
            monitorCare: TextView?,
            treatmentDuration: TextView?
        ) {
            // Backward compatibility: Extract and format treatment steps from text
            val treatment = extractTreatment(message.text)
            android.util.Log.d("PrescriptionCardViewHolder", "📋 Text parsing treatment: ${treatment.take(100)}...")
            
            // Step 1: Immediate Action
            val immediateStep = extractImmediateAction(treatment, message.text)
            immediateAction?.text = immediateStep
            
            // Step 2: Medicine Application
            val medicineStep = extractMedicineApplication(treatment, message.text)
            medicineApplication?.text = medicineStep
            
            // Step 3: Repeat Schedule
            val scheduleStep = extractRepeatSchedule(treatment, message.text)
            repeatSchedule?.text = scheduleStep
            
            // Step 4: Monitor & Care
            val monitorStep = extractMonitorCare(treatment, message.text)
            monitorCare?.text = monitorStep
            
            // Step 5: Treatment Duration - extract from text
            val durationStep = extractTreatmentDuration(treatment, message.text)
            treatmentDuration?.text = durationStep
        }
        
        // Add the text parsing utility methods that are now needed by standalone card
        private fun extractTreatment(text: String): String {
            // Extract treatment section from the message text
            val treatmentIndicators = listOf("treatment:", "recommendation:", "apply", "spray", "use")
            
            val lines = text.split("\n")
            val treatmentLines = mutableListOf<String>()
            var collectingTreatment = false
            
            for (line in lines) {
                val lowerLine = line.lowercase()
                if (treatmentIndicators.any { indicator -> lowerLine.contains(indicator) }) {
                    collectingTreatment = true
                    treatmentLines.add(line.trim())
                } else if (collectingTreatment && line.trim().isNotEmpty()) {
                    treatmentLines.add(line.trim())
                } else if (collectingTreatment && line.trim().isEmpty()) {
                    break // End of treatment section
                }
            }
            
            return treatmentLines.joinToString(" ").ifEmpty { text }
        }
        
        private fun extractImmediateAction(treatment: String, fullText: String): String {
            return when {
                fullText.contains("remove", ignoreCase = true) && fullText.contains("leaves", ignoreCase = true) ->
                    "Remove affected leaves and dispose safely"
                fullText.contains("isolate", ignoreCase = true) ->
                    "Isolate affected plants from healthy ones"
                else -> "Remove affected plant parts and clean area"
            }
        }
        
        private fun extractMedicineApplication(treatment: String, fullText: String): String {
            return when {
                fullText.contains("copper", ignoreCase = true) && fullText.contains("fungicide", ignoreCase = true) ->
                    "Spray copper-based fungicide solution"
                fullText.contains("fungicide", ignoreCase = true) ->
                    "Apply fungicide spray to affected areas"
                fullText.contains("spray", ignoreCase = true) ->
                    "Apply recommended plant spray"
                else -> "Apply prescribed plant medicine"
            }
        }
        
        private fun extractRepeatSchedule(treatment: String, fullText: String): String {
            return when {
                fullText.contains("every 7", ignoreCase = true) || fullText.contains("weekly", ignoreCase = true) ->
                    "Every 7 days, early morning"
                fullText.contains("every 10", ignoreCase = true) || fullText.contains("7-10 days", ignoreCase = true) ->
                    "Every 7-10 days, early morning"
                fullText.contains("daily", ignoreCase = true) ->
                    "Daily application, early morning"
                else -> "Follow recommended schedule, early morning"
            }
        }
        
        private fun extractMonitorCare(treatment: String, fullText: String): String {
            val carePoints = mutableListOf<String>()
            
            if (fullText.contains("water", ignoreCase = true) || fullText.contains("overhead", ignoreCase = true)) {
                carePoints.add("avoid overhead watering")
            }
            if (fullText.contains("circulation", ignoreCase = true) || fullText.contains("air", ignoreCase = true)) {
                carePoints.add("improve air circulation")
            }
            
            return if (carePoints.isNotEmpty()) {
                "Check daily, ${carePoints.joinToString(", ")}"
            } else {
                "Check daily, maintain proper plant care"
            }
        }
        
        private fun extractTreatmentDuration(treatment: String, fullText: String): String {
            return when {
                fullText.contains("2-3 weeks", ignoreCase = true) -> "Continue for 2-3 weeks until improvement"
                fullText.contains("1-2 weeks", ignoreCase = true) -> "Apply for 1-2 weeks then reassess"
                fullText.contains("until", ignoreCase = true) && fullText.contains("improve", ignoreCase = true) ->
                    "Continue until improvement is visible"
                fullText.contains("weekly", ignoreCase = true) -> "Weekly applications for 3-4 weeks"
                else -> "Continue treatment until new healthy growth appears (usually 2-3 weeks)"
            }
        }
        
        private fun extractPlantType(text: String): String {
            // Try to extract plant type from the text
            val plantKeywords = listOf("tomato", "apple", "corn", "wheat", "potato", "cucumber", "pepper", "strawberry")
            for (keyword in plantKeywords) {
                if (text.contains(keyword, ignoreCase = true)) {
                    return keyword.replaceFirstChar { it.uppercase() } + " Plant"
                }
            }
            return "Plant"
        }
        
        private fun extractDetailedTreatment(text: String): String {
            android.util.Log.d("PrescriptionCardViewHolder", "🔍 Extracting detailed treatment from text (length: ${text.length})")
            
            // Try specific treatment extraction similar to disease card but formatted for prescription
            val treatment = when {
                text.contains("TREATMENT FOR YOUR PLANT", ignoreCase = true) -> {
                    val startIndex = text.indexOf("TREATMENT FOR YOUR PLANT", ignoreCase = true)
                    val endIndex = text.indexOf("Your plant will get better", ignoreCase = true)
                    if (startIndex >= 0 && endIndex > startIndex) {
                        text.substring(startIndex, endIndex).trim()
                    } else {
                        extractTreatmentSection(text)
                    }
                }
                else -> extractTreatmentSection(text)
            }
            
            // Format for prescription display
            return formatForPrescription(treatment)
        }
        
        private fun extractTreatmentSection(text: String): String {
            // Extract treatment-related sections
            return when {
                text.contains("apply", ignoreCase = true) && text.contains("fungicide", ignoreCase = true) -> {
                    "Apply copper-based fungicide spray<br>📅 Every 7-10 days<br>🕐 Early morning application<br>⚠️ Continue until symptoms resolve"
                }
                text.contains("spray", ignoreCase = true) -> {
                    "Apply recommended plant spray<br>📅 As directed<br>🕐 Follow application schedule<br>⚠️ Monitor plant response"
                }
                else -> {
                    "Follow treatment recommendations<br>📅 As prescribed<br>🕐 Monitor application timing<br>⚠️ Continue as directed"
                }
            }
        }
        
        private fun formatForPrescription(treatment: String): String {
            // Format treatment text for prescription card display
            var formatted = treatment
                .replace(Regex("\\*\\*(.*?)\\*\\*")) { "<b>${it.groupValues[1]}</b>" }
                .replace("\n", "<br>")
            
            // Add prescription-style formatting if not already present
            if (!formatted.contains("📅") && !formatted.contains("🕐")) {
                formatted = "Apply as directed<br>📅 Follow recommended schedule<br>🕐 Monitor application timing<br>⚠️ Continue until symptoms resolve"
            }
            
            return formatted
        }
        
        private fun extractAdditionalCare(text: String): String {
            // Extract care instructions or provide defaults
            val careInstructions = mutableListOf<String>()
            
            if (text.contains("remove", ignoreCase = true) && text.contains("leaves", ignoreCase = true)) {
                careInstructions.add("• Remove and dispose of affected leaves")
            }
            if (text.contains("air circulation", ignoreCase = true)) {
                careInstructions.add("• Improve air circulation around plant")
            }
            if (text.contains("watering", ignoreCase = true)) {
                careInstructions.add("• Avoid overhead watering")
            }
            
            // Add default instructions if none found
            if (careInstructions.isEmpty()) {
                careInstructions.addAll(listOf(
                    "• Remove and dispose of affected plant material",
                    "• Improve air circulation around plant", 
                    "• Water at soil level to avoid wet foliage",
                    "• Monitor plant daily for changes"
                ))
            } else {
                careInstructions.add("• Monitor plant daily for changes")
            }
            
            return careInstructions.joinToString("\n")
        }
        
        
        private fun setupFeedbackButtons(message: ChatMessage) {
            thumbsUpButton.setOnClickListener {
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_up_selected)
                )
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                
                // Record feedback for prescription
                val feedback = MessageFeedback(
                    messageText = message.text,
                    feedbackType = FeedbackType.THUMBS_UP,
                    userContext = "Positive feedback on prescription card"
                )
                FeedbackManager.recordFeedback(feedback)
                
                onThumbsUpClick(message)
            }
            
            thumbsDownButton.setOnClickListener {
                thumbsDownButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_down_selected)
                )
                thumbsUpButton.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.thumbs_default)
                )
                
                // Record feedback for prescription
                val feedback = MessageFeedback(
                    messageText = message.text,
                    feedbackType = FeedbackType.THUMBS_DOWN,
                    userContext = "Negative feedback on prescription card"
                )
                FeedbackManager.recordFeedback(feedback)
                
                onThumbsDownClick(message)
            }
        }
    }
}
