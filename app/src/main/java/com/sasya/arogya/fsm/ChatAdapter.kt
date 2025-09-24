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
    }
    
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isUser -> VIEW_TYPE_USER
            // Check healthy FIRST to avoid misclassification
            isHealthyMessage(message) -> VIEW_TYPE_HEALTHY_CARD
            isDiseaseMessage(message) -> {
                when {
                    isSevereDisease(message) -> VIEW_TYPE_SEVERE_DISEASE_CARD
                    else -> VIEW_TYPE_DISEASE_CARD
                }
            }
            else -> VIEW_TYPE_ASSISTANT
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
     * Helper methods to detect message types based on content
     */
    private fun isDiseaseMessage(message: ChatMessage): Boolean {
        // Only trigger disease card if we have actual disease data OR specific detection phrases
        return message.diseaseName != null || 
               (message.confidence != null && message.confidence!! > 0.0 && 
                (message.text.contains("detected:", ignoreCase = true) ||
                 message.text.contains("diagnosis:", ignoreCase = true) ||
                 message.text.contains("identified as", ignoreCase = true) ||
                 message.text.contains("appears to be", ignoreCase = true) ||
                 message.text.contains("suffering from", ignoreCase = true)))
    }
    
    private fun isHealthyMessage(message: ChatMessage): Boolean {
        // Check for explicit healthy indicators first
        val hasHealthyKeywords = (message.text.contains("healthy", ignoreCase = true) ||
                                 message.text.contains("looks good", ignoreCase = true) ||
                                 message.text.contains("no disease found", ignoreCase = true) ||
                                 message.text.contains("plant appears healthy", ignoreCase = true) ||
                                 message.text.contains("no issues detected", ignoreCase = true))
        
        // High confidence with healthy keywords and no disease name should be healthy
        return (message.confidence != null && message.confidence!! > 0.85 && 
                message.diseaseName == null && 
                hasHealthyKeywords &&
                // Make sure it's not a disease message with high confidence
                !message.text.contains("disease detected", ignoreCase = true))
    }
    
    private fun isSevereDisease(message: ChatMessage): Boolean {
        return message.diseaseName != null && 
               (message.confidence != null && message.confidence!! > 0.95) &&
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
            
            // Only show treatment section if actual treatment recommendations are present
            if (hasTreatmentRecommendations(message)) {
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
            } else {
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
}
