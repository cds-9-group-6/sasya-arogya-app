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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.content.ContextCompat
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
            messageText.text = TextFormattingUtil.formatWhatsAppStyle(message.text, itemView.context)
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
        private val quickActionsRecyclerView: RecyclerView = itemView.findViewById(R.id.quickActionsRecyclerView)
        private val thumbsUpButton: ImageButton = itemView.findViewById(R.id.thumbsUpButton)
        private val thumbsDownButton: ImageButton = itemView.findViewById(R.id.thumbsDownButton)
        private val attentionOverlayContainer: LinearLayout = itemView.findViewById(R.id.attentionOverlayContainer)
        private val attentionOverlayImage: ImageView = itemView.findViewById(R.id.attentionOverlayImage)
        private val overlayDescription: TextView = itemView.findViewById(R.id.overlayDescription)
        
        // Disease card elements
        private val diseaseCardWrapper: androidx.cardview.widget.CardView = itemView.findViewById(R.id.diseaseCardWrapper)
        private val diseaseCardContainer: LinearLayout = itemView.findViewById(R.id.diseaseCardContainer)
        private val diseaseTitle: TextView = itemView.findViewById(R.id.diseaseTitle)
        private val diseaseConfidence: TextView = itemView.findViewById(R.id.diseaseConfidence)
        
        // Insurance card elements - nullable to prevent crashes
        private val insuranceCardWrapper: androidx.cardview.widget.CardView? = try { 
            itemView.findViewById(R.id.insuranceCardWrapper)
        } catch (e: Exception) { 
            Log.w("ChatAdapter", "Insurance card wrapper not found: ${e.message}")
            null 
        }
        
        // Insurance certificate card elements - nullable to prevent crashes
        private val insuranceCertificateCardWrapper: androidx.cardview.widget.CardView? = try { 
            itemView.findViewById(R.id.insuranceCertificateCardWrapper)
        } catch (e: Exception) { 
            Log.w("ChatAdapter", "Insurance certificate card wrapper not found: ${e.message}")
            null 
        }
        private val insuranceCropInfo: TextView? = try { itemView.findViewById(R.id.insuranceCropInfo) } catch (e: Exception) { null }
        private val totalPremiumAmount: TextView? = try { itemView.findViewById(R.id.totalPremiumAmount) } catch (e: Exception) { null }
        private val subsidyAmount: TextView? = try { itemView.findViewById(R.id.subsidyAmount) } catch (e: Exception) { null }
        private val farmerContributionAmount: TextView? = try { itemView.findViewById(R.id.farmerContributionAmount) } catch (e: Exception) { null }
        private val areaDetails: TextView? = try { itemView.findViewById(R.id.areaDetails) } catch (e: Exception) { null }
        private val premiumPerHectare: TextView? = try { itemView.findViewById(R.id.premiumPerHectare) } catch (e: Exception) { null }
        private val diseaseContext: TextView? = try { itemView.findViewById(R.id.diseaseContext) } catch (e: Exception) { null }
        private val diseaseInfoContainer: LinearLayout? = try { itemView.findViewById(R.id.diseaseInfoContainer) } catch (e: Exception) { null }
        private val applyInsuranceButton: TextView? = try { itemView.findViewById(R.id.applyInsuranceButton) } catch (e: Exception) { null }
        
        // Insurance certificate card elements
        private val certificatePolicyId: TextView? = try { itemView.findViewById(R.id.certificatePolicyId) } catch (e: Exception) { null }
        private val certificatePolicyIdValue: TextView? = try { itemView.findViewById(R.id.certificatePolicyIdValue) } catch (e: Exception) { null }
        private val certificateCompanyName: TextView? = try { itemView.findViewById(R.id.certificateCompanyName) } catch (e: Exception) { null }
        private val certificateTotalCoverage: TextView? = try { itemView.findViewById(R.id.certificateTotalCoverage) } catch (e: Exception) { null }
        private val certificateFarmerName: TextView? = try { itemView.findViewById(R.id.certificateFarmerName) } catch (e: Exception) { null }
        private val certificateCropInfo: TextView? = try { itemView.findViewById(R.id.certificateCropInfo) } catch (e: Exception) { null }
        private val certificatePremiumBreakdown: TextView? = try { itemView.findViewById(R.id.certificatePremiumBreakdown) } catch (e: Exception) { null }
        private val viewCertificateButton: TextView? = try { itemView.findViewById(R.id.viewCertificateButton) } catch (e: Exception) { null }
        
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
            
            // Handle card display based on message type - insurance certificate takes highest priority
            when {
                // Insurance certificate card takes highest priority
                message.insuranceCertificate != null -> {
                    try {
                        if (insuranceCertificateCardWrapper != null) {
                            insuranceCertificateCardWrapper.visibility = View.VISIBLE
                            insuranceCardWrapper?.visibility = View.GONE
                            diseaseCardWrapper.visibility = View.GONE
                            healthyCardContainer.visibility = View.GONE
                            
                            populateInsuranceCertificateCard(message.insuranceCertificate)
                            
                            messageText.visibility = View.VISIBLE
                            messageText.text = TextFormattingUtil.formatWhatsAppStyle("📄 **Insurance Certificate Generated**\n\nYour crop insurance certificate has been successfully generated and is ready for download.", itemView.context)
                        } else {
                            Log.e("ChatAdapter", "Insurance certificate card wrapper not found - showing fallback text")
                            // Show fallback message instead of card
                            insuranceCertificateCardWrapper?.visibility = View.GONE
                            insuranceCardWrapper?.visibility = View.GONE
                            diseaseCardWrapper.visibility = View.GONE  
                            healthyCardContainer.visibility = View.GONE
                            
                            messageText.visibility = View.VISIBLE
                            messageText.text = TextFormattingUtil.formatWhatsAppStyle("📄 **Insurance Certificate Generated**\n\nPolicy ID: ${message.insuranceCertificate.policyId}\nCompany: ${message.insuranceCertificate.companyName}\nCoverage: ₹${String.format("%.2f", message.insuranceCertificate.totalSumInsured)}", itemView.context)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatAdapter", "Critical error displaying insurance certificate card: ${e.message}", e)
                        // Emergency fallback
                        messageText.visibility = View.VISIBLE
                        messageText.text = "📄 Insurance certificate generated - see logs for details"
                    }
                }
                // Insurance premium card
                message.insuranceDetails != null -> {
                    try {
                        if (insuranceCardWrapper != null) {
                            insuranceCardWrapper.visibility = View.VISIBLE
                            diseaseCardWrapper.visibility = View.GONE
                            healthyCardContainer.visibility = View.GONE
                            
                            populateInsuranceCard(message.insuranceDetails)
                            
                            messageText.visibility = View.VISIBLE
                            messageText.text = TextFormattingUtil.formatWhatsAppStyle("🛡️ **Crop Insurance Premium Calculated**\n\nBased on your plant health analysis and location, here are your insurance options:", itemView.context)
                        } else {
                            Log.e("ChatAdapter", "Insurance card wrapper not found - showing fallback text")
                            // Show fallback message instead of card
                            insuranceCardWrapper?.visibility = View.GONE
                            diseaseCardWrapper.visibility = View.GONE  
                            healthyCardContainer.visibility = View.GONE
                            
                            messageText.visibility = View.VISIBLE
                            messageText.text = TextFormattingUtil.formatWhatsAppStyle("🛡️ **Insurance Premium Calculated**\n\nCrop: ${message.insuranceDetails.crop}\nArea: ${message.insuranceDetails.area} hectares\nTotal Premium: ₹${String.format("%.2f", message.insuranceDetails.totalPremium)}\nYour Contribution: ₹${String.format("%.2f", message.insuranceDetails.farmerContribution)}", itemView.context)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatAdapter", "Critical error displaying insurance card: ${e.message}", e)
                        // Emergency fallback
                        messageText.visibility = View.VISIBLE
                        messageText.text = "🛡️ Insurance premium calculated - see logs for details"
                    }
                }
                // Disease/healthy card display
                message.diseaseName != null && message.confidence != null -> {
                    // Check if this is the same message content to prevent duplication
                    val currentMessageKey = "${message.diseaseName}_${message.confidence}_${message.text.hashCode()}"
                    val shouldPopulateCard = !cardPopulated || lastMessageText != currentMessageKey
                    
                    insuranceCardWrapper?.visibility = View.GONE
                    
                    if (message.diseaseName.lowercase() == "healthy") {
                        // Show healthy card
                        healthyCardContainer.visibility = View.VISIBLE
                        diseaseCardWrapper.visibility = View.GONE
                        
                        if (shouldPopulateCard) {
                            populateHealthyCard(message)
                            cardPopulated = true
                            lastMessageText = currentMessageKey
                        }
                    } else {
                        // Show disease card
                        diseaseCardWrapper.visibility = View.VISIBLE
                        healthyCardContainer.visibility = View.GONE
                        
                        if (shouldPopulateCard) {
                            populateDiseaseCard(message)
                            cardPopulated = true
                            lastMessageText = currentMessageKey
                        }
                    }
                    
                    // Show dynamic introductory content based on disease/health classification
                    messageText.visibility = View.VISIBLE
                    messageText.text = if (message.diseaseName.lowercase() == "healthy") {
                        TextFormattingUtil.formatWhatsAppStyle(generateHealthyIntroText(message.confidence), itemView.context)
                    } else {
                        TextFormattingUtil.formatWhatsAppStyle(generateDiseaseIntroText(message.diseaseName, message.confidence), itemView.context)
                    }
                }
                else -> {
                    // No special card, show regular message
                    insuranceCertificateCardWrapper?.visibility = View.GONE
                    insuranceCardWrapper?.visibility = View.GONE
                    diseaseCardWrapper.visibility = View.GONE
                    healthyCardContainer.visibility = View.GONE
                    cardPopulated = false
                    lastMessageText = ""
                    
                    messageText.visibility = View.VISIBLE
                    messageText.text = TextFormattingUtil.formatWhatsAppStyle(message.text, itemView.context)
                }
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
                setupQuickActionsRecyclerView(message.followUpItems)
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
            diseaseContent.text = TextFormattingUtil.formatWhatsAppStyle(diseaseDetails, itemView.context)
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
            healthyContent.text = TextFormattingUtil.formatWhatsAppStyle(healthyDetails, itemView.context)
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
        
        /**
         * Populate insurance certificate card with generated certificate details (with crash prevention)
         */
        private fun populateInsuranceCertificateCard(certificateDetails: InsuranceCertificateDetails) {
            try {
                Log.d("ChatAdapter", "📄 Populating insurance certificate card for ${certificateDetails.policyId}")
                
                // Debug: Log view availability
                Log.d("ChatAdapter", "Certificate views status:")
                Log.d("ChatAdapter", "  - insuranceCertificateCardWrapper: ${if (insuranceCertificateCardWrapper != null) "✅ Found" else "❌ NULL"}")
                Log.d("ChatAdapter", "  - certificatePolicyIdValue: ${if (certificatePolicyIdValue != null) "✅ Found" else "❌ NULL"}")
                Log.d("ChatAdapter", "  - certificateCompanyName: ${if (certificateCompanyName != null) "✅ Found" else "❌ NULL"}")
                
                // Set policy ID
                certificatePolicyIdValue?.text = certificateDetails.policyId
                
                // Set company name
                certificateCompanyName?.text = certificateDetails.companyName
                
                // Set total coverage amount
                certificateTotalCoverage?.text = "₹${formatCurrencyAmount(certificateDetails.totalSumInsured)}"
                
                // Set farmer name
                certificateFarmerName?.text = certificateDetails.farmerName
                
                // Set crop information
                certificateCropInfo?.text = "${certificateDetails.crop} • ${certificateDetails.state} • ${String.format("%.1f", certificateDetails.area)} hectares"
                
                // Set premium breakdown
                certificatePremiumBreakdown?.text = "₹${formatCurrencyAmount(certificateDetails.premiumPaidByFarmer)} (Farmer) + ₹${formatCurrencyAmount(certificateDetails.premiumPaidByGovt)} (Government)"
                
                // Set up PDF viewer button click handler
                viewCertificateButton?.setOnClickListener {
                    try {
                        if (certificateDetails.pdfBase64 != null) {
                            showPdfViewer(certificateDetails.pdfBase64, certificateDetails.policyId)
                        } else {
                            Log.w("ChatAdapter", "No PDF data available for certificate")
                            onFollowUpClick("Please regenerate the insurance certificate PDF")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatAdapter", "Error in viewCertificateButton click: ${e.message}")
                    }
                }
                
                Log.d("ChatAdapter", "✅ Insurance certificate card populated successfully")
                
            } catch (e: Exception) {
                Log.e("ChatAdapter", "❌ Error populating insurance certificate card: ${e.message}", e)
                // Hide certificate card if population fails to prevent crash
                insuranceCertificateCardWrapper?.visibility = View.GONE
            }
        }
        
        /**
         * Show PDF viewer for insurance certificate
         */
        private fun showPdfViewer(pdfBase64: String, policyId: String) {
            try {
                // Create custom dialog for PDF viewing
                val dialog = Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                dialog.setContentView(R.layout.dialog_pdf_viewer)
                
                // Set up dialog views
                val pdfView = dialog.findViewById<androidx.core.widget.NestedScrollView>(R.id.pdfScrollView)
                val pdfContent = dialog.findViewById<TextView>(R.id.pdfContent)
                val btnClose = dialog.findViewById<ImageButton>(R.id.btnClosePdf)
                val pdfTitle = dialog.findViewById<TextView>(R.id.pdfTitle)
                
                // Set title
                pdfTitle.text = "Insurance Certificate - $policyId"
                
                // For now, show certificate details as text (in a real app, you'd use a PDF library)
                pdfContent.text = "📄 Insurance Certificate\n\n" +
                        "Policy ID: $policyId\n" +
                        "PDF Data Available: ${if (pdfBase64.isNotEmpty()) "Yes (${pdfBase64.length} characters)" else "No"}\n\n" +
                        "Note: In a production app, this would display the actual PDF using a PDF viewer library like AndroidPdfViewer or similar."
                
                // Close button functionality
                btnClose.setOnClickListener {
                    dialog.dismiss()
                }
                
                // Allow tapping outside to close
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
                
                // Show the dialog
                dialog.show()
                
                Log.d("ChatAdapter", "📄 Opened PDF viewer for certificate: $policyId")
                
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error showing PDF viewer: ${e.message}", e)
                // Fallback: trigger follow-up action
                onFollowUpClick("Please help me view the insurance certificate PDF")
            }
        }
        
        /**
         * Populate insurance premium card with calculated details (with crash prevention)
         */
        private fun populateInsuranceCard(insuranceDetails: InsuranceDetails) {
            try {
                Log.d("ChatAdapter", "🛡️ Populating insurance card for ${insuranceDetails.crop}")
                
                // Debug: Log view availability
                Log.d("ChatAdapter", "Insurance views status:")
                Log.d("ChatAdapter", "  - insuranceCardWrapper: ${if (insuranceCardWrapper != null) "✅ Found" else "❌ NULL"}")
                Log.d("ChatAdapter", "  - insuranceCropInfo: ${if (insuranceCropInfo != null) "✅ Found" else "❌ NULL"}")
                Log.d("ChatAdapter", "  - totalPremiumAmount: ${if (totalPremiumAmount != null) "✅ Found" else "❌ NULL"}")
                
                // Format crop information with null safety
                insuranceCropInfo?.text = "${insuranceDetails.crop} • ${insuranceDetails.state} • ${String.format("%.1f", insuranceDetails.area)} hectares"
                
                // Format total premium amount with proper currency formatting
                totalPremiumAmount?.text = "₹${formatCurrencyAmount(insuranceDetails.totalPremium)}"
                
                // Format government subsidy
                subsidyAmount?.text = "₹${formatCurrencyAmount(insuranceDetails.governmentSubsidy)}"
                
                // Format farmer contribution (highlighted as what farmer pays)
                farmerContributionAmount?.text = "₹${formatCurrencyAmount(insuranceDetails.farmerContribution)}"
                
                // Format area details
                areaDetails?.text = "${String.format("%.1f", insuranceDetails.area)} hectares"
                
                // Format premium per hectare
                premiumPerHectare?.text = "₹${formatCurrencyAmount(insuranceDetails.premiumPerHectare)} per hectare"
                
                // Handle disease context if present
                if (!insuranceDetails.disease.isNullOrBlank()) {
                    diseaseInfoContainer?.visibility = View.VISIBLE
                    diseaseContext?.text = insuranceDetails.disease.replace("_", " ").split(" ").joinToString(" ") { 
                        it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
                    }
                } else {
                    diseaseInfoContainer?.visibility = View.GONE
                }
                
                // Set up button click handlers with null safety
                applyInsuranceButton?.setOnClickListener {
                    try {
                        onFollowUpClick("Help me apply for crop insurance with these premium details")
                    } catch (e: Exception) {
                        Log.e("ChatAdapter", "Error in applyInsuranceButton click: ${e.message}")
                    }
                }
                
                Log.d("ChatAdapter", "✅ Insurance card populated successfully")
                
            } catch (e: Exception) {
                Log.e("ChatAdapter", "❌ Error populating insurance card: ${e.message}", e)
                // Hide insurance card if population fails to prevent crash
                insuranceCardWrapper?.visibility = View.GONE
            }
        }
        
        /**
         * Format currency amounts with commas for readability
         */
        private fun formatCurrencyAmount(amount: Double): String {
            return if (amount >= 1000) {
                String.format("%,.2f", amount)
            } else {
                String.format("%.2f", amount)
            }
        }
        
        /**
         * Generate dynamic introductory text based on disease classification
         */
        private fun generateDiseaseIntroText(diseaseName: String?, confidence: Double?): String {
            if (diseaseName == null || confidence == null) return ""
            
            val confidencePercent = String.format("%.0f", confidence * 100)
            val diseaseNameClean = diseaseName.replace("_", " ").split(" ")
                .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
            
            // Generic professional analysis message
            val introText = "🔬 **Plant Health Analysis Complete**\n\nOur AI has identified potential health concerns in your plant image. Early detection and proper treatment are essential to prevent spread and maintain plant health."
            
            // Confidence-based assessment and recommendations
            val assessmentMessage = when {
                confidence >= 0.8 -> {
                    "**High Confidence Detection ($confidencePercent%)**\n\n⚠️ Our analysis shows strong indicators of the identified condition. Immediate attention is recommended to prevent potential spread to other parts of the plant or nearby vegetation."
                }
                confidence >= 0.6 -> {
                    "**Moderate Confidence Detection ($confidencePercent%)**\n\n📋 The analysis indicates likely signs of the identified condition. Monitor the plant closely and consider implementing preventive treatments to limit any potential spread."
                }
                confidence >= 0.4 -> {
                    "**Preliminary Detection ($confidencePercent%)**\n\n🔍 Early signs have been detected that warrant attention. Continue monitoring the plant's condition and be prepared to take action if symptoms worsen or spread."
                }
                else -> {
                    "**Initial Assessment ($confidencePercent%)**\n\n❓ Some concerning signs have been noted. Consider obtaining additional images or consulting with a plant specialist for a more definitive diagnosis."
                }
            }
            
            return "$introText\n\n$assessmentMessage"
        }
        
        /**
         * Generate dynamic introductory text for healthy plant detections
         */
        private fun generateHealthyIntroText(confidence: Double?): String {
            if (confidence == null) return ""
            
            val confidencePercent = String.format("%.0f", confidence * 100)
            
            val introText = "🌿 **Plant Health Analysis Complete**\n\nExcellent news! Our AI analysis indicates that your plant appears to be in good health with no immediate concerns detected."
            
            val assessmentMessage = when {
                confidence >= 0.9 -> {
                    "**Excellent Health Assessment ($confidencePercent%)**\n\n✅ Your plant shows strong, healthy characteristics. Continue your current care routine to maintain this excellent condition."
                }
                confidence >= 0.7 -> {
                    "**Good Health Assessment ($confidencePercent%)**\n\n💚 The plant appears healthy with no concerning symptoms visible. Regular monitoring and consistent care will help maintain plant health."
                }
                confidence >= 0.5 -> {
                    "**Positive Health Assessment ($confidencePercent%)**\n\n📍 Generally positive signs detected. Continue regular care monitoring and watch for any changes in plant condition."
                }
                else -> {
                    "**Basic Health Check ($confidencePercent%)**\n\n🔍 No immediate issues detected, but continue observing plant condition and maintain consistent care practices."
                }
            }
            
            return "$introText\n\n$assessmentMessage"
        }
        
        /**
         * Setup horizontal scrolling RecyclerView for quick actions
         */
        private fun setupQuickActionsRecyclerView(quickActions: List<String>) {
            try {
                // Setup horizontal LinearLayoutManager
                val layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
                quickActionsRecyclerView.layoutManager = layoutManager
                
                // Create adapter for quick actions
                val adapter = QuickActionAdapter(quickActions) { action ->
                    onFollowUpClick(action)
                }
                quickActionsRecyclerView.adapter = adapter
                
                Log.d("ChatAdapter", "✅ Quick actions RecyclerView setup complete with ${quickActions.size} actions")
            } catch (e: Exception) {
                Log.e("ChatAdapter", "❌ Error setting up quick actions RecyclerView: ${e.message}", e)
            }
        }
    }
}

/**
 * Adapter for horizontal scrolling quick action buttons
 */
class QuickActionAdapter(
    private val actions: List<String>,
    private val onActionClick: (String) -> Unit
) : RecyclerView.Adapter<QuickActionAdapter.QuickActionViewHolder>() {

    class QuickActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val actionButton: TextView = itemView.findViewById(R.id.quickActionText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_action_button, parent, false)
        return QuickActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuickActionViewHolder, position: Int) {
        val action = actions[position]
        holder.actionButton.text = action
        
        holder.actionButton.setOnClickListener {
            // Visual feedback
            holder.actionButton.text = "✓ $action"
            holder.actionButton.isClickable = false
            
            // Trigger callback
            onActionClick(action)
        }
    }

    override fun getItemCount(): Int = actions.size
}
