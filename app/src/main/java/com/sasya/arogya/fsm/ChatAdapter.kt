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
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
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
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message)
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
    
    fun clear() {
        messages.clear()
        notifyDataSetChanged()
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
        
        // Disease card elements
        private val diseaseCardContainer: LinearLayout = itemView.findViewById(R.id.diseaseCardContainer)
        private val diseaseTitle: TextView = itemView.findViewById(R.id.diseaseTitle)
        private val diseaseConfidence: TextView = itemView.findViewById(R.id.diseaseConfidence)
        private val diseaseSeverity: TextView = itemView.findViewById(R.id.diseaseSeverity)
        private val diseaseContent: TextView = itemView.findViewById(R.id.diseaseContent)
        
        // Healthy card elements
        private val healthyCardContainer: LinearLayout = itemView.findViewById(R.id.healthyCardContainer)
        private val healthyTitle: TextView = itemView.findViewById(R.id.healthyTitle)
        private val healthyConfidence: TextView = itemView.findViewById(R.id.healthyConfidence)
        private val healthyStatus: TextView = itemView.findViewById(R.id.healthyStatus)
        private val healthyContent: TextView = itemView.findViewById(R.id.healthyContent)
        
        // Track if card has been populated to prevent duplication
        private var cardPopulated = false
        private var lastMessageText = ""
        
        fun bind(message: ChatMessage) {
            messageTime.text = timeFormatter.format(Date(message.timestamp))
            
            // Handle disease/healthy card display
            if (message.diseaseName != null && message.confidence != null) {
                // Check if this is the same message content to prevent duplication
                val currentMessageKey = "${message.diseaseName}_${message.confidence}_${message.text.hashCode()}"
                val shouldPopulateCard = !cardPopulated || lastMessageText != currentMessageKey
                
                if (message.diseaseName.lowercase() == "healthy") {
                    // Show healthy card
                    healthyCardContainer.visibility = View.VISIBLE
                    diseaseCardContainer.visibility = View.GONE
                    
                    if (shouldPopulateCard) {
                        populateHealthyCard(message)
                        cardPopulated = true
                        lastMessageText = currentMessageKey
                    }
                } else {
                    // Show disease card
                    diseaseCardContainer.visibility = View.VISIBLE
                    healthyCardContainer.visibility = View.GONE
                    
                    if (shouldPopulateCard) {
                        populateDiseaseCard(message)
                        cardPopulated = true
                        lastMessageText = currentMessageKey
                    }
                }
                
                // Show empty message text when card is displayed to prevent duplication
                // Keep it visible to maintain layout constraints, but show no content
                messageText.visibility = View.VISIBLE
                messageText.text = ""
            } else {
                // No special card, show regular message
                diseaseCardContainer.visibility = View.GONE
                healthyCardContainer.visibility = View.GONE
                cardPopulated = false
                lastMessageText = ""
                
                messageText.visibility = View.VISIBLE
                messageText.text = TextFormattingUtil.formatWhatsAppStyle(message.text)
            }
            
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
        
        private fun populateDiseaseCard(message: ChatMessage) {
            // Set disease title
            diseaseTitle.text = "${message.diseaseName} Detected"
            
            // Set confidence percentage
            val confidencePercent = String.format("%.0f", (message.confidence ?: 0.0) * 100)
            diseaseConfidence.text = "$confidencePercent%"
            
            // Extract severity from message text or set default
            val severity = extractSeverity(message.text) ?: "Mild to Moderate"
            diseaseSeverity.text = severity
            
            // Extract and format symptoms and treatment from message text
            val diseaseDetails = extractDiseaseDetails(message.text)
            diseaseContent.text = TextFormattingUtil.formatWhatsAppStyle(diseaseDetails)
        }
        
        private fun populateHealthyCard(message: ChatMessage) {
            // Set healthy title
            healthyTitle.text = "Healthy Plant Detected"
            
            // Set confidence percentage
            val confidencePercent = String.format("%.0f", (message.confidence ?: 0.0) * 100)
            healthyConfidence.text = "$confidencePercent%"
            
            // Extract or set plant status
            val status = extractHealthyStatus(message.text) ?: "Excellent Health"
            healthyStatus.text = status
            
            // Extract and format care recommendations from message text
            val healthyDetails = extractHealthyDetails(message.text)
            healthyContent.text = TextFormattingUtil.formatWhatsAppStyle(healthyDetails)
        }
        
        private fun extractHealthyStatus(text: String): String? {
            // Look for status information in the message text
            val statusRegex = Regex("(?i)status:?\\s*([^\\n]+)")
            val statusMatch = statusRegex.find(text)?.groupValues?.get(1)?.trim()
            
            // Also look for health-related keywords
            if (statusMatch != null) return statusMatch
            
            return when {
                text.contains("excellent", ignoreCase = true) -> "Excellent Health"
                text.contains("very good", ignoreCase = true) -> "Very Good Health"
                text.contains("good", ignoreCase = true) -> "Good Health"
                text.contains("healthy", ignoreCase = true) -> "Healthy"
                else -> null
            }
        }
        
        private fun extractHealthyDetails(text: String): String {
            // Extract care recommendations and observations
            val lines = text.split("\n")
            val detailLines = mutableListOf<String>()
            var foundDetailsStart = false
            
            for (line in lines) {
                val trimmedLine = line.trim()
                
                // Start collecting when we hit care recommendations or observations
                if (!foundDetailsStart && (
                    trimmedLine.lowercase().contains("recommendations:") ||
                    trimmedLine.lowercase().contains("care:") ||
                    trimmedLine.lowercase().contains("continue") ||
                    trimmedLine.lowercase().contains("maintain") ||
                    trimmedLine.lowercase().contains("shows:") ||
                    trimmedLine.lowercase().contains("observations:") ||
                    trimmedLine.startsWith("•") ||
                    trimmedLine.matches(Regex("\\d+\\..*")))) {
                    foundDetailsStart = true
                }
                
                if (foundDetailsStart) {
                    detailLines.add(line)
                }
            }
            
            return if (detailLines.isNotEmpty()) {
                detailLines.joinToString("\n")
            } else {
                // Fallback: return the second half of the message
                val midPoint = lines.size / 2
                lines.drop(midPoint).joinToString("\n")
            }
        }
        
        private fun extractIntroText(text: String): String {
            // Extract the first sentence or paragraph before detailed disease information
            val lines = text.split("\n")
            val introLines = mutableListOf<String>()
            
            for (line in lines) {
                val trimmedLine = line.trim()
                // Stop when we hit structured disease info
                if (trimmedLine.lowercase().contains("detected:") ||
                    trimmedLine.lowercase().contains("confidence:") ||
                    trimmedLine.lowercase().contains("severity:") ||
                    trimmedLine.lowercase().contains("symptoms identified:") ||
                    trimmedLine.lowercase().contains("recommended treatment:")) {
                    break
                }
                introLines.add(line)
            }
            
            return if (introLines.isNotEmpty()) {
                introLines.joinToString("\n").trim()
            } else {
                // Fallback: take first sentence
                text.split(".").firstOrNull()?.plus(".") ?: text
            }
        }
        
        private fun extractSeverity(text: String): String? {
            // Look for severity information in the message text
            val severityRegex = Regex("(?i)severity:?\\s*([^\\n]+)")
            return severityRegex.find(text)?.groupValues?.get(1)?.trim()
        }
        
        private fun extractDiseaseDetails(text: String): String {
            // Extract symptoms and treatment details, excluding the intro text
            val lines = text.split("\n")
            val detailLines = mutableListOf<String>()
            var foundDetailsStart = false
            
            for (line in lines) {
                val trimmedLine = line.trim()
                
                // Start collecting when we hit structured info
                if (!foundDetailsStart && (
                    trimmedLine.lowercase().contains("symptoms identified:") ||
                    trimmedLine.lowercase().contains("recommended treatment:") ||
                    trimmedLine.lowercase().contains("treatment:") ||
                    trimmedLine.startsWith("•") ||
                    trimmedLine.matches(Regex("\\d+\\..*")))) {
                    foundDetailsStart = true
                }
                
                if (foundDetailsStart) {
                    detailLines.add(line)
                }
            }
            
            return if (detailLines.isNotEmpty()) {
                detailLines.joinToString("\n")
            } else {
                // Fallback: return the second half of the message
                val midPoint = lines.size / 2
                lines.drop(midPoint).joinToString("\n")
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
}
