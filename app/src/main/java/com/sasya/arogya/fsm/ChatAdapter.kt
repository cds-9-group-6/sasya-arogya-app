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
import android.widget.Button
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
    private val onThumbsDownClick: (ChatMessage) -> Unit = {},
    private val onRetryClick: (ChatMessage) -> Unit = {}
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
                AssistantMessageViewHolder(view, onFollowUpClick, onThumbsUpClick, onThumbsDownClick, onRetryClick)
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
    
    fun updateLastMessage(text: String, state: String?) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages[messages.size - 1]
            if (!lastMessage.isUser) {
                messages[messages.size - 1] = lastMessage.copy(text = text, state = state)
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
     * Add an error message to the chat
     */
    fun addErrorMessage(errorMessage: String, originalUserMessage: String? = null, originalImageB64: String? = null) {
        val message = ChatMessage(
            text = "❌ Error",
            isUser = false,
            isError = true,
            errorMessage = errorMessage,
            canRetry = true,
            originalUserMessage = originalUserMessage,
            originalImageB64 = originalImageB64
        )
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    /**
     * Update the last message to show an error state
     */
    fun updateLastMessageAsError(errorMessage: String, originalUserMessage: String? = null, originalImageB64: String? = null) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages[messages.size - 1]
            if (!lastMessage.isUser) {
                val errorMessage = lastMessage.copy(
                    text = "❌ Error",
                    isError = true,
                    errorMessage = errorMessage,
                    canRetry = true,
                    originalUserMessage = originalUserMessage,
                    originalImageB64 = originalImageB64,
                    state = null // Clear any processing state
                )
                messages[messages.size - 1] = errorMessage
                notifyItemChanged(messages.size - 1)
            }
        }
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
        private val onThumbsDownClick: (ChatMessage) -> Unit,
        private val onRetryClick: (ChatMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val stateIndicator: TextView = itemView.findViewById(R.id.stateIndicator)
        private val processingSubtitle: TextView = itemView.findViewById(R.id.processingSubtitle)
        private val followUpContainer: LinearLayout = itemView.findViewById(R.id.followUpContainer)
        private val quickActionsContainer: LinearLayout = itemView.findViewById(R.id.quickActionsContainer)
        private val thumbsUpButton: ImageButton = itemView.findViewById(R.id.thumbsUpButton)
        private val thumbsDownButton: ImageButton = itemView.findViewById(R.id.thumbsDownButton)
        private val attentionOverlayContainer: LinearLayout = itemView.findViewById(R.id.attentionOverlayContainer)
        private val attentionOverlayImage: ImageView = itemView.findViewById(R.id.attentionOverlayImage)
        private val errorContainer: LinearLayout = itemView.findViewById(R.id.errorContainer)
        private val errorMessageText: TextView = itemView.findViewById(R.id.errorMessageText)
        private val retryButton: Button = itemView.findViewById(R.id.retryButton)
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
        private val insuranceCompanyName: TextView? = try { itemView.findViewById(R.id.insuranceCompanyName) } catch (e: Exception) { null }
        private val companyInfoContainer: LinearLayout? = try { itemView.findViewById(R.id.companyInfoContainer) } catch (e: Exception) { null }
        private val sumInsuredAmount: TextView? = try { itemView.findViewById(R.id.sumInsuredAmount) } catch (e: Exception) { null }
        private val sumInsuredContainer: LinearLayout? = try { itemView.findViewById(R.id.sumInsuredContainer) } catch (e: Exception) { null }
        private val diseaseInfoContainer: LinearLayout? = try { itemView.findViewById(R.id.diseaseInfoContainer) } catch (e: Exception) { null }
        private val applyInsuranceButton: TextView? = try { itemView.findViewById(R.id.applyInsuranceButton) } catch (e: Exception) { null }
        
        // Insurance certificate card elements
        private val certificatePolicyId: TextView? = try { itemView.findViewById(R.id.certificatePolicyId) } catch (e: Exception) { null }
        private val certificatePolicyIdValue: TextView? = try { itemView.findViewById(R.id.certificatePolicyIdValue) } catch (e: Exception) { null }
        private val certificateCompanyName: TextView? = try { itemView.findViewById(R.id.certificateCompanyName) } catch (e: Exception) { null }
        private val certificateSumInsured: TextView? = try { itemView.findViewById(R.id.certificateSumInsured) } catch (e: Exception) { null }
        private val certificateTotalPremium: TextView? = try { itemView.findViewById(R.id.certificateTotalPremium) } catch (e: Exception) { null }
        private val certificateGovernmentSubsidy: TextView? = try { itemView.findViewById(R.id.certificateGovernmentSubsidy) } catch (e: Exception) { null }
        private val certificateFarmerContribution: TextView? = try { itemView.findViewById(R.id.certificateFarmerContribution) } catch (e: Exception) { null }
        private val certificateFarmerName: TextView? = try { itemView.findViewById(R.id.certificateFarmerName) } catch (e: Exception) { null }
        private val certificateCropInfo: TextView? = try { itemView.findViewById(R.id.certificateCropInfo) } catch (e: Exception) { null }
        private val viewCertificateButton: TextView? = try { itemView.findViewById(R.id.viewCertificateButton) } catch (e: Exception) { null }
        private val downloadCertificateButton: TextView? = try { itemView.findViewById(R.id.downloadCertificateButton) } catch (e: Exception) { null }
        private val pdfPreviewContainer: LinearLayout? = try { itemView.findViewById(R.id.pdfPreviewContainer) } catch (e: Exception) { null }
        private val pdfPreviewPolicyId: TextView? = try { itemView.findViewById(R.id.pdfPreviewPolicyId) } catch (e: Exception) { null }
        private val pdfPreviewSumInsured: TextView? = try { itemView.findViewById(R.id.pdfPreviewSumInsured) } catch (e: Exception) { null }
        private val pdfPreviewFarmerContribution: TextView? = try { itemView.findViewById(R.id.pdfPreviewFarmerContribution) } catch (e: Exception) { null }
        
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
                            val companyInfo = if (!message.insuranceDetails.companyName.isNullOrBlank()) {
                                "\nInsurance Company: ${message.insuranceDetails.companyName}"
                            } else {
                                ""
                            }
                            messageText.text = TextFormattingUtil.formatWhatsAppStyle("🛡️ **Insurance Premium Calculated**\n\nCrop: ${message.insuranceDetails.crop}\nArea: ${message.insuranceDetails.area} hectares\nTotal Premium: ₹${String.format("%.2f", message.insuranceDetails.totalPremium)}\nYour Contribution: ₹${String.format("%.2f", message.insuranceDetails.farmerContribution)}$companyInfo", itemView.context)
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
                // Parse state to separate main state from processing details
                val (mainState, processingDetails) = parseStateMessage(message.state)
                
                stateIndicator.visibility = View.VISIBLE
                stateIndicator.text = mainState
                
                // Show processing subtitle if present
                if (processingDetails != null) {
                    processingSubtitle.visibility = View.VISIBLE
                    processingSubtitle.text = processingDetails
                } else {
                    processingSubtitle.visibility = View.GONE
                }
                
                // Color based on main state
                val backgroundColor = when (mainState.lowercase()) {
                    "ready" -> ContextCompat.getColor(itemView.context, R.color.state_ready)
                    "thinking..." -> ContextCompat.getColor(itemView.context, R.color.state_processing)
                    "analyzing plant..." -> ContextCompat.getColor(itemView.context, R.color.state_processing)
                    "diagnosis complete" -> ContextCompat.getColor(itemView.context, R.color.state_complete)
                    else -> ContextCompat.getColor(itemView.context, R.color.state_default)
                }
                
                val drawable = stateIndicator.background as? GradientDrawable
                drawable?.setColor(backgroundColor)
            } else {
                stateIndicator.visibility = View.GONE
                processingSubtitle.visibility = View.GONE
            }
            
            // Add follow-up buttons
            if (message.followUpItems != null && message.followUpItems.isNotEmpty()) {
                followUpContainer.visibility = View.VISIBLE
                setupQuickActionsContainer(message.followUpItems)
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
            
            // Handle error state
            if (message.isError) {
                errorContainer.visibility = View.VISIBLE
                errorMessageText.text = message.errorMessage ?: "Request failed"
                
                // Setup retry button if retry is possible
                if (message.canRetry) {
                    retryButton.visibility = View.VISIBLE
                    retryButton.setOnClickListener {
                        onRetryClick(message)
                    }
                } else {
                    retryButton.visibility = View.GONE
                }
            } else {
                errorContainer.visibility = View.GONE
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
                
                // Set sum insured amount - this should be the actual sum insured, not total premium
                val sumInsured = certificateDetails.totalSumInsured
                certificateSumInsured?.text = "₹${formatCurrencyAmount(sumInsured)}"
                
                // Set premium breakdown amounts
                val totalPremium = certificateDetails.totalPremium
                val governmentSubsidy = certificateDetails.governmentSubsidy
                val farmerContribution = certificateDetails.farmerContribution
                
                certificateTotalPremium?.text = "₹${formatCurrencyAmount(totalPremium)}"
                certificateGovernmentSubsidy?.text = "₹${formatCurrencyAmount(governmentSubsidy)}"
                certificateFarmerContribution?.text = "₹${formatCurrencyAmount(farmerContribution)}"
                
                // Set farmer name
                certificateFarmerName?.text = certificateDetails.farmerName
                
                // Set crop information
                certificateCropInfo?.text = "${certificateDetails.crop} • ${certificateDetails.state} • ${String.format("%.1f", certificateDetails.area)} hectares"
                
                // Show PDF preview if available
                if (certificateDetails.pdfBase64 != null) {
                    pdfPreviewContainer?.visibility = View.VISIBLE
                    
                    // Populate PDF preview with key information
                    pdfPreviewPolicyId?.text = certificateDetails.policyId
                    pdfPreviewSumInsured?.text = "₹${String.format("%.2f", certificateDetails.totalSumInsured)}"
                    pdfPreviewFarmerContribution?.text = "₹${String.format("%.2f", certificateDetails.farmerContribution)}"
                    
                    // Make PDF preview clickable to open full PDF viewer
                    pdfPreviewContainer?.setOnClickListener {
                        try {
                            showPdfViewer(certificateDetails.pdfBase64, certificateDetails.policyId)
                        } catch (e: Exception) {
                            Log.e("ChatAdapter", "Error opening PDF viewer from preview: ${e.message}")
                            onFollowUpClick("Please regenerate the insurance certificate PDF")
                        }
                    }
                    
                    // Add long press listener for tooltip
                    pdfPreviewContainer?.setOnLongClickListener {
                        showTooltip(pdfPreviewContainer, "Tap to view full PDF")
                        true // Consume the long press event
                    }
                } else {
                    pdfPreviewContainer?.visibility = View.GONE
                }
                
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
                
                // Set up PDF download button click handler
                downloadCertificateButton?.setOnClickListener {
                    try {
                        if (certificateDetails.pdfBase64 != null) {
                            downloadPdfCertificate(certificateDetails.pdfBase64, certificateDetails.policyId)
                        } else {
                            Log.w("ChatAdapter", "No PDF data available for certificate download")
                            onFollowUpClick("Please regenerate the insurance certificate PDF")
                        }
                    } catch (e: Exception) {
                        Log.e("ChatAdapter", "Error in downloadCertificateButton click: ${e.message}")
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
         * Show PDF viewer for insurance certificate using WebView
         */
        private fun showPdfViewer(pdfBase64: String, policyId: String) {
            try {
                Log.d("ChatAdapter", "📄 Opening PDF viewer for certificate: $policyId, PDF size: ${pdfBase64.length} chars")
                
                // Create full-screen dialog
                val dialog = Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                
                // Create layout programmatically for WebView-based PDF viewing
                val layout = android.widget.LinearLayout(itemView.context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setBackgroundColor(android.graphics.Color.WHITE)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                
                // Header with title and close button
                val header = android.widget.LinearLayout(itemView.context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setBackgroundColor(android.graphics.Color.parseColor("#2E7D32"))
                    setPadding(16, 16, 16, 16)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                
                val title = android.widget.TextView(itemView.context).apply {
                    text = "🛡️ Insurance Certificate - $policyId"
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                
                val closeButton = android.widget.Button(itemView.context).apply {
                    text = "✕"
                    textSize = 20f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.parseColor("#1B5E20"))
                    setPadding(24, 8, 24, 8)
                }
                
                header.addView(title)
                header.addView(closeButton)
                layout.addView(header)
                
                // WebView for PDF display
                val webView = android.webkit.WebView(itemView.context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                    settings.javaScriptEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.setSupportZoom(true)
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                }
                
                layout.addView(webView)
                
                // Set the layout to dialog
                dialog.setContentView(layout)
                
                // Close button functionality
                closeButton.setOnClickListener {
                    dialog.dismiss()
                }
                
                // Load PDF using Mozilla PDF.js viewer (works reliably across all Android versions)
                try {
                    // Use PDF.js viewer to render the PDF with zoom controls
                    val htmlContent = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=5.0, user-scalable=yes">
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                html {
                                    overflow: auto;
                                    height: 100%;
                                }
                                body { 
                                    background: #525659;
                                    font-family: Arial, sans-serif;
                                    overflow: hidden;
                                    touch-action: pan-x pan-y pinch-zoom;
                                    margin: 0;
                                    padding: 0;
                                    min-height: 100%;
                                    position: relative;
                                }
                                #container {
                                    width: 100%;
                                    padding: 5px;
                                    display: flex;
                                    flex-direction: column;
                                    align-items: center;
                                    transform-origin: top center;
                                    transition: transform 0.3s ease;
                                }
                                #container.zoomed {
                                    width: max-content;
                                    transform-origin: top left;
                                }
                                .page-wrapper {
                                    width: 100%;
                                    margin-bottom: 15px;
                                    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
                                    background: white;
                                }
                                canvas {
                                    display: block;
                                    width: 100%;
                                    height: auto;
                                }
                                .loading {
                                    color: white;
                                    padding: 20px;
                                    text-align: center;
                                    font-size: 16px;
                                }
                                .error {
                                    color: #ff6b6b;
                                    padding: 20px;
                                    text-align: center;
                                    background: white;
                                    margin: 20px;
                                    border-radius: 8px;
                                }
                                .page-info {
                                    color: white;
                                    padding: 8px 16px;
                                    text-align: center;
                                    background: rgba(0,0,0,0.7);
                                    position: fixed;
                                    top: 60px;
                                    left: 50%;
                                    transform: translateX(-50%);
                                    border-radius: 20px;
                                    font-size: 13px;
                                    z-index: 1000;
                                    font-weight: bold;
                                }
                                .zoom-controls {
                                    position: fixed;
                                    bottom: 30px;
                                    right: 20px;
                                    display: flex;
                                    flex-direction: column;
                                    gap: 10px;
                                    z-index: 1000;
                                }
                                .zoom-btn {
                                    width: 50px;
                                    height: 50px;
                                    border-radius: 50%;
                                    background: rgba(46, 125, 50, 0.95);
                                    color: white;
                                    border: 2px solid white;
                                    font-size: 24px;
                                    font-weight: bold;
                                    cursor: pointer;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    box-shadow: 0 4px 12px rgba(0,0,0,0.4);
                                    touch-action: manipulation;
                                }
                                .zoom-btn:active {
                                    background: rgba(27, 94, 32, 0.95);
                                    transform: scale(0.95);
                                }
                                .zoom-level {
                                    background: rgba(0,0,0,0.7);
                                    color: white;
                                    padding: 8px 12px;
                                    border-radius: 20px;
                                    font-size: 12px;
                                    text-align: center;
                                    font-weight: bold;
                                }
                            </style>
                            <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"></script>
                        </head>
                        <body>
                            <div class="page-info" id="pageInfo">Loading PDF...</div>
                            <div id="container">
                                <div class="loading" id="loading">📄 Loading Insurance Certificate...</div>
                            </div>
                            
                            <!-- Zoom Controls -->
                            <div class="zoom-controls" id="zoomControls" style="display: none;">
                                <div class="zoom-btn" onclick="zoomIn()">+</div>
                                <div class="zoom-level" id="zoomLevel">100%</div>
                                <div class="zoom-btn" onclick="zoomOut()">−</div>
                                <div class="zoom-btn" onclick="resetZoom()" style="font-size: 18px;">⟲</div>
                            </div>
                            
                            <script>
                                // Set PDF.js worker
                                pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
                                
                                const pdfData = atob('$pdfBase64');
                                const container = document.getElementById('container');
                                const loading = document.getElementById('loading');
                                const pageInfo = document.getElementById('pageInfo');
                                const zoomControls = document.getElementById('zoomControls');
                                const zoomLevel = document.getElementById('zoomLevel');
                                
                                let currentZoom = 1.0;
                                const minZoom = 0.5;
                                const maxZoom = 4.0;
                                const zoomStep = 0.25;
                                
                                // Convert base64 to Uint8Array
                                const pdfBytes = new Uint8Array(pdfData.length);
                                for (let i = 0; i < pdfData.length; i++) {
                                    pdfBytes[i] = pdfData.charCodeAt(i);
                                }
                                
                                // Zoom functions
                                function zoomIn() {
                                    if (currentZoom < maxZoom) {
                                        currentZoom += zoomStep;
                                        applyZoom();
                                    }
                                }
                                
                                function zoomOut() {
                                    if (currentZoom > minZoom) {
                                        currentZoom -= zoomStep;
                                        applyZoom();
                                    }
                                }
                                
                                function resetZoom() {
                                    currentZoom = 1.0;
                                    applyZoom();
                                }
                                
                                function applyZoom() {
                                    container.style.transform = 'scale(' + currentZoom + ')';
                                    zoomLevel.textContent = Math.round(currentZoom * 100) + '%';
                                    
                                    // Enable/disable pan based on zoom level
                                    if (currentZoom > 1.0) {
                                        // Zoomed in - enable pan by adding class
                                        container.classList.add('zoomed');
                                        document.body.style.overflow = 'auto';
                                    } else {
                                        // At 100% - disable pan by removing class
                                        container.classList.remove('zoomed');
                                        document.body.style.overflow = 'hidden';
                                        // Reset scroll position
                                        window.scrollTo(0, 0);
                                    }
                                }
                                
                                // Load and render PDF with better quality
                                pdfjsLib.getDocument({data: pdfBytes}).promise.then(function(pdf) {
                                    loading.style.display = 'none';
                                    pageInfo.textContent = '📄 ' + pdf.numPages + ' page(s) - Tap + or pinch to zoom for details';
                                    zoomControls.style.display = 'flex';
                                    
                                    // Render all pages with higher scale for better text rendering
                                    const renderPromises = [];
                                    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
                                        renderPromises.push(
                                            pdf.getPage(pageNum).then(function(page) {
                                                // Use scale 2.5 for crisp text rendering
                                                const scale = 2.5;
                                                const viewport = page.getViewport({scale: scale});
                                                
                                                // Create page wrapper
                                                const pageWrapper = document.createElement('div');
                                                pageWrapper.className = 'page-wrapper';
                                                
                                                const canvas = document.createElement('canvas');
                                                const context = canvas.getContext('2d');
                                                
                                                // Set canvas to actual PDF dimensions at scale (high-res)
                                                canvas.height = viewport.height;
                                                canvas.width = viewport.width;
                                                
                                                // Canvas CSS will be 100% width (controlled by page-wrapper)
                                                // This ensures it always fits the window initially
                                                // When zoomed, the scale transform handles enlargement
                                                
                                                pageWrapper.appendChild(canvas);
                                                container.appendChild(pageWrapper);
                                                
                                                return page.render({
                                                    canvasContext: context,
                                                    viewport: viewport
                                                }).promise;
                                            })
                                        );
                                    }
                                    
                                    return Promise.all(renderPromises);
                                }).then(function() {
                                    console.log('✅ All pages rendered successfully');
                                    setTimeout(() => {
                                        pageInfo.style.opacity = '0';
                                        setTimeout(() => pageInfo.style.display = 'none', 300);
                                    }, 3000);
                                }).catch(function(error) {
                                    console.error('❌ Error loading PDF:', error);
                                    loading.innerHTML = '<div class="error">❌ Error loading PDF<br><br>' + 
                                        'Policy ID: $policyId<br>' +
                                        'Error: ' + error.message + '<br><br>' +
                                        'PDF Size: ${pdfBase64.length} chars</div>';
                                    pageInfo.style.display = 'none';
                                });
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    
                    // Load HTML with PDF.js renderer
                    webView.loadDataWithBaseURL("https://example.com", htmlContent, "text/html", "UTF-8", null)
                    
                    Log.d("ChatAdapter", "✅ PDF.js viewer loaded into WebView")
                } catch (e: Exception) {
                    Log.e("ChatAdapter", "Error loading PDF.js viewer: ${e.message}", e)
                    // Fallback: Show error message
                    val errorHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { 
                                    font-family: Arial; 
                                    padding: 20px; 
                                    text-align: center;
                                    background: #f5f5f5;
                                }
                                .container {
                                    background: white;
                                    padding: 30px;
                                    border-radius: 12px;
                                    margin-top: 50px;
                                    box-shadow: 0 4px 8px rgba(0,0,0,0.1);
                                }
                                .error { 
                                    color: #c62828; 
                                    margin: 20px 0;
                                    font-size: 18px;
                                    font-weight: bold;
                                }
                                .info { 
                                    color: #666; 
                                    margin: 10px 0;
                                    font-size: 14px;
                                    line-height: 1.6;
                                }
                                .icon { font-size: 48px; margin-bottom: 20px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="icon">📄</div>
                                <h2>Insurance Certificate</h2>
                                <p class="error">Unable to display PDF</p>
                                <p class="info">Policy ID: <strong>$policyId</strong></p>
                                <p class="info">PDF Size: ${pdfBase64.length} characters</p>
                                <p class="info">Error: ${e.message}</p>
                                <hr style="margin: 20px 0;">
                                <p class="info">Please contact support to receive your certificate via email</p>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
                }
                
                // Allow tapping outside to close
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
                
                // Show the dialog
                dialog.show()
                
                Log.d("ChatAdapter", "📄 PDF viewer dialog displayed")
                
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error showing PDF viewer: ${e.message}", e)
                android.widget.Toast.makeText(
                    itemView.context,
                    "Unable to display PDF. Please try again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        
        /**
         * Show tooltip for PDF preview
         */
        private fun showTooltip(view: View, message: String) {
            try {
                val context = itemView.context
                
                // Create a custom tooltip popup
                val popupWindow = android.widget.PopupWindow(context)
                
                // Create tooltip layout
                val tooltipLayout = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(android.graphics.Color.parseColor("#E0F2F1"))
                }
                
                // Add icon
                val icon = android.widget.TextView(context).apply {
                    text = "ℹ️"
                    textSize = 16f
                    setTextColor(android.graphics.Color.parseColor("#00695C"))
                }
                
                // Add message
                val messageText = android.widget.TextView(context).apply {
                    text = message
                    textSize = 14f
                    setTextColor(android.graphics.Color.parseColor("#00695C"))
                    setPadding(8, 0, 0, 0)
                }
                
                tooltipLayout.addView(icon)
                tooltipLayout.addView(messageText)
                
                // Create drawable for tooltip background
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(android.graphics.Color.parseColor("#E0F2F1"))
                    cornerRadius = 8f
                    setStroke(2, android.graphics.Color.parseColor("#4DB6AC"))
                }
                tooltipLayout.background = drawable
                
                popupWindow.contentView = tooltipLayout
                popupWindow.width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                popupWindow.height = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                popupWindow.isFocusable = false
                popupWindow.isOutsideTouchable = true
                
                // Show tooltip above the view
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                popupWindow.showAtLocation(view, android.view.Gravity.NO_GRAVITY, 
                    location[0] + view.width / 2 - 100, location[1] - 100)
                
                // Auto-dismiss after 2 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (popupWindow.isShowing) {
                        popupWindow.dismiss()
                    }
                }, 2000)
                
            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error showing tooltip: ${e.message}")
                // Fallback: Show toast
                android.widget.Toast.makeText(
                    itemView.context,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        /**
         * Download PDF certificate to device storage
         */
        private fun downloadPdfCertificate(pdfBase64: String, policyId: String) {
            try {
                Log.d("ChatAdapter", "⬇️ Downloading PDF certificate: $policyId, PDF size: ${pdfBase64.length} chars")
                
                val context = itemView.context
                
                // Show downloading toast
                android.widget.Toast.makeText(
                    context,
                    "📥 Downloading certificate...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
                // Decode base64 PDF data
                val pdfBytes = android.util.Base64.decode(pdfBase64, android.util.Base64.DEFAULT)
                
                // Create filename with timestamp
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val filename = "Insurance_Certificate_${policyId}_${timestamp}.pdf"
                
                // Try multiple storage locations for better compatibility
                val file = try {
                    // First try: External files directory (most accessible)
                    val downloadsDir = java.io.File(context.getExternalFilesDir(null), "Downloads")
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    java.io.File(downloadsDir, filename)
                } catch (e: Exception) {
                    Log.w("ChatAdapter", "External storage not available, using internal storage: ${e.message}")
                    // Fallback: Internal files directory
                    val internalDir = java.io.File(context.filesDir, "Downloads")
                    if (!internalDir.exists()) {
                        internalDir.mkdirs()
                    }
                    java.io.File(internalDir, filename)
                }
                
                // Write PDF to file
                java.io.FileOutputStream(file).use { fos ->
                    fos.write(pdfBytes)
                }
                
                Log.d("ChatAdapter", "✅ PDF certificate downloaded successfully: ${file.absolutePath}")
                
                // Show success message with file location
                android.widget.Toast.makeText(
                    context,
                    "📄 Certificate saved successfully!\nLocation: ${file.parentFile?.name}/$filename",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                
                // Try to open the file with a PDF viewer
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    intent.setDataAndType(uri, "application/pdf")
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    // Check if there's an app that can handle PDFs
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        // No PDF viewer available, show a message
                        android.widget.Toast.makeText(
                            context,
                            "📱 No PDF viewer found. File saved to: ${file.absolutePath}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.w("ChatAdapter", "Could not open PDF viewer: ${e.message}")
                    // File is still saved, show location
                    android.widget.Toast.makeText(
                        context,
                        "📄 File saved but couldn't open automatically.\nLocation: ${file.absolutePath}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                Log.e("ChatAdapter", "❌ Error downloading PDF certificate: ${e.message}", e)
                android.widget.Toast.makeText(
                    itemView.context,
                    "Unable to download certificate. Please try again.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
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
                
                // Handle sum insured if present
                if (insuranceDetails.sumInsured > 0) {
                    sumInsuredContainer?.visibility = View.VISIBLE
                    sumInsuredAmount?.text = "₹${formatCurrencyAmount(insuranceDetails.sumInsured)}"
                } else {
                    sumInsuredContainer?.visibility = View.GONE
                }
                
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
                
                // Handle company name if present
                if (!insuranceDetails.companyName.isNullOrBlank()) {
                    companyInfoContainer?.visibility = View.VISIBLE
                    insuranceCompanyName?.text = insuranceDetails.companyName
                } else {
                    companyInfoContainer?.visibility = View.GONE
                }
                
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
                        // Include specific crop name and details in the apply message
                        val cropName = insuranceDetails.crop
                        val state = insuranceDetails.state
                        val area = insuranceDetails.area
                        val applyMessage = "Help me apply for crop insurance for my $cropName farm of ${String.format("%.1f", area)} hectares in $state with these premium details"
                        
                        Log.d("ChatAdapter", "🛡️ Apply for insurance clicked for crop: $cropName")
                        onFollowUpClick(applyMessage)
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
         * Setup quick actions in a 2-column grid layout without horizontal scrolling
         */
        private fun setupQuickActionsContainer(quickActions: List<String>) {
            try {
                // Get the quick actions container
                val quickActionsContainer = itemView.findViewById<LinearLayout>(R.id.quickActionsContainer)
                
                // Define the 6 quick action buttons
                val actionButtons = listOf(
                    R.id.quickAction1,
                    R.id.quickAction2,
                    R.id.quickAction3,
                    R.id.quickAction4,
                    R.id.quickAction5,
                    R.id.quickAction6
                )
                
                // Set up each button with click listeners
                actionButtons.forEachIndexed { index, buttonId ->
                    val button = itemView.findViewById<TextView>(buttonId)
                    if (index < quickActions.size) {
                        button.text = quickActions[index]
                        button.visibility = View.VISIBLE
                        button.setOnClickListener {
                            // Visual feedback
                            button.text = "✓ ${quickActions[index]}"
                            button.isClickable = false
                            
                            // Trigger callback
                            onFollowUpClick(quickActions[index])
                        }
                    } else {
                        button.visibility = View.GONE
                    }
                }
                
                // Show the container
                quickActionsContainer.visibility = View.VISIBLE
                
                Log.d("ChatAdapter", "✅ Quick actions LinearLayout setup complete with ${quickActions.size} actions")
            } catch (e: Exception) {
                Log.e("ChatAdapter", "❌ Error setting up quick actions LinearLayout: ${e.message}", e)
            }
        }
        
        /**
         * Parse state message to separate main state from processing details
         * Returns Pair(mainState, processingDetails)
         */
        private fun parseStateMessage(state: String): Pair<String, String?> {
            return when {
                state.startsWith("Processing on Non-GPU cluster") -> 
                    Pair("THINKING...", state)
                state.startsWith("Processing on GPU cluster") -> 
                    Pair("THINKING...", state)
                state.startsWith("Processing your request") -> 
                    Pair("THINKING...", state)
                else -> Pair(state, null)
            }
        }
    }
}

