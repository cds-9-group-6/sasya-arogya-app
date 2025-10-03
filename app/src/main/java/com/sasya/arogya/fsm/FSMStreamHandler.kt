package com.sasya.arogya.fsm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.*
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Handler for FSM agent streaming responses (Server-Sent Events)
 */
class FSMStreamHandler {
    
    private val gson = Gson()
    private val TAG = "FSMStreamHandler"
    
    interface StreamCallback {
        fun onStateUpdate(stateUpdate: FSMStateUpdate)
        fun onMessage(message: String)
        fun onFollowUpItems(items: List<String>)
        fun onAttentionOverlay(overlayData: AttentionOverlayData)
        fun onError(error: String)
        fun onStreamComplete()
        fun onInsuranceDetails(insuranceDetails: InsuranceDetails)
        fun onInsuranceCertificate(certificateDetails: InsuranceCertificateDetails)
    }
    
    /**
     * Process streaming response from FSM agent with timeout handling
     */
    suspend fun processStream(responseBody: ResponseBody, callback: StreamCallback) {
        // Set up a timeout for the entire streaming operation
        val streamTimeoutMillis = 300_000L // 5 minutes
        
        try {
            withTimeout(streamTimeoutMillis) {
                withContext(Dispatchers.IO) {
                    try {
                        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                        var line: String?
                        var currentEvent = ""
                        var currentData = ""
                        var lastActivityTime = System.currentTimeMillis()
                        val activityTimeoutMillis = 120_000L // 2 minutes of inactivity
                        
                        while (isActive) { // Check if coroutine is still active
                            // Check for inactivity timeout
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastActivityTime > activityTimeoutMillis) {
                                Log.w(TAG, "Stream inactive for ${activityTimeoutMillis / 1000} seconds, timing out")
                                throw TimeoutException("Server response timeout - no data received for ${activityTimeoutMillis / 1000} seconds")
                            }
                            
                            try {
                                line = reader.readLine()
                                if (line == null) {
                                    break // End of stream
                                }
                                
                                lastActivityTime = currentTime // Reset activity timer
                                
                                line?.let { currentLine ->
                                    Log.d(TAG, "Received line: $currentLine")
                                    
                                    when {
                                        currentLine.startsWith("event: ") -> {
                                            currentEvent = currentLine.substringAfter("event: ").trim()
                                        }
                                        
                                        currentLine.startsWith("data: ") -> {
                                            currentData = currentLine.substringAfter("data: ").trim()
                                        }
                                        
                                        currentLine.isEmpty() -> {
                                            // End of event, process it
                                            if (currentEvent.isNotEmpty() && currentData.isNotEmpty()) {
                                                processEvent(currentEvent, currentData, callback)
                                            }
                                            // Reset for next event
                                            currentEvent = ""
                                            currentData = ""
                                        }
                                    }
                                }
                            } catch (e: IOException) {
                                if (isActive) { // Only log if we're still supposed to be reading
                                    Log.e(TAG, "IO error while reading stream", e)
                                    throw e
                                }
                                break
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            callback.onStreamComplete()
                        }
                        
                    } catch (e: SocketTimeoutException) {
                        Log.e(TAG, "Socket timeout while processing stream", e)
                        withContext(Dispatchers.Main) {
                            callback.onError("Server response timeout. Please check your connection and try again.")
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "IO error processing stream", e)
                        withContext(Dispatchers.Main) {
                            callback.onError("Connection error: ${getErrorMessage(e)}")
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Stream processing timeout", e)
            withContext(Dispatchers.Main) {
                callback.onError("Request timeout. The server is taking too long to respond. Please try again.")
            }
        } catch (e: TimeoutException) {
            Log.e(TAG, "Stream activity timeout", e)
            withContext(Dispatchers.Main) {
                callback.onError(e.message ?: "Server response timeout")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error processing stream", e)
            withContext(Dispatchers.Main) {
                callback.onError("Unexpected error: ${getErrorMessage(e)}")
            }
        } finally {
            try {
                responseBody.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing response body", e)
            }
        }
    }
    
    /**
     * Get user-friendly error message from exception
     */
    private fun getErrorMessage(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "Connection timeout. Please check your internet connection."
            is java.net.ConnectException -> "Cannot connect to server. Please check server availability."
            is java.net.UnknownHostException -> "Cannot resolve server address. Please check your internet connection."
            is IOException -> "Network error occurred. Please try again."
            else -> e.message ?: "Unknown error occurred"
        }
    }
    
    /**
     * Process individual SSE events
     */
    private suspend fun processEvent(event: String, data: String, callback: StreamCallback) {
        Log.d(TAG, "Processing event: $event, data: $data")
        
        withContext(Dispatchers.Main) {
            try {
                when (event) {
                    "state_update" -> {
                        val stateUpdate = gson.fromJson(data, FSMStateUpdate::class.java)
                        callback.onStateUpdate(stateUpdate)
                        
                        // Handle specific parts of state update
                        stateUpdate.assistantResponse?.let { message ->
                            if (message.isNotBlank()) {
                                // Only show response from insurance node, not followup node for insurance
                                if (stateUpdate.currentNode == "insurance" || stateUpdate.currentNode != "followup") {
                                    callback.onMessage(message)
                                }
                            }
                        }
                        
                        // Handle insurance premium details
                        if (stateUpdate.currentNode == "insurance" && 
                            stateUpdate.insuranceContext != null && 
                            stateUpdate.insurancePremiumDetails != null) {
                            
                            Log.d(TAG, "Processing insurance premium details")
                            val insuranceDetails = parseInsuranceDetails(
                                stateUpdate.insuranceContext,
                                stateUpdate.insurancePremiumDetails
                            )
                            insuranceDetails?.let { details ->
                                callback.onInsuranceDetails(details)
                            }
                        }
                        
                        // Handle insurance certificate
                        if (stateUpdate.currentNode == "insurance" && 
                            stateUpdate.insuranceCertificate != null) {
                            
                            Log.d(TAG, "Processing insurance certificate")
                            val certificateDetails = parseInsuranceCertificate(stateUpdate.insuranceCertificate)
                            certificateDetails?.let { details ->
                                callback.onInsuranceCertificate(details)
                            }
                        }
                        
                        stateUpdate.followUpItems?.let { items ->
                            if (items.isNotEmpty()) {
                                callback.onFollowUpItems(items)
                            }
                        }
                        
                        stateUpdate.error?.let { error ->
                            callback.onError(error)
                        }
                    }
                    
                    "assistant_response" -> {
                        // FIXED: Handle new assistant_response events from modular architecture
                        try {
                            val responseData = gson.fromJson(data, AssistantResponseData::class.java)
                            responseData.assistant_response?.let { message ->
                                if (message.isNotBlank()) {
                                    callback.onMessage(message)
                                }
                            }
                        } catch (e: Exception) {
                            // Fallback: treat as plain text if JSON parsing fails
                            Log.w(TAG, "Failed to parse assistant_response as JSON, treating as plain text: ${e.message}")
                            callback.onMessage(data)
                        }
                    }
                    
                    "message" -> {
                        callback.onMessage(data)
                    }
                    
                    "error" -> {
                        callback.onError(data)
                    }
                    
                    "attention_overlay" -> {
                        try {
                            val overlayData = gson.fromJson(data, AttentionOverlayData::class.java)
                            callback.onAttentionOverlay(overlayData)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing attention overlay data: ${e.message}")
                            callback.onError("Failed to parse attention overlay: ${e.message}")
                        }
                    }
                    
                    "complete" -> {
                        callback.onStreamComplete()
                    }
                    
                    else -> {
                        Log.d(TAG, "Unknown event type: $event")
                    }
                }
                
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "Error parsing JSON: $data", e)
                callback.onError("Failed to parse server response: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing event: $event", e)
                callback.onError("Error processing event: ${e.message}")
            }
        }
    }
    
    /**
     * Create a properly formatted chat request
     */
    fun createChatRequest(
        message: String,
        imageBase64: String? = null,
        sessionId: String? = null,
        context: Map<String, Any>? = null
    ): FSMChatRequest {
        return FSMChatRequest(
            sessionId = sessionId,
            message = message,
            imageB64 = imageBase64,
            context = context
        )
    }
    
    /**
     * Parse follow-up items from state update
     */
    fun parseFollowUpItems(stateUpdate: FSMStateUpdate): List<FollowUpItem> {
        return stateUpdate.followUpItems?.map { text ->
            FollowUpItem(text = text, isClicked = false)
        } ?: emptyList()
    }
    
    /**
     * Get current state display name
     */
    fun getStateDisplayName(currentNode: String?): String {
        return when (currentNode) {
            "initial" -> "Ready"
            "classifying" -> "Analyzing Plant..."
            "prescribing" -> "Generating Treatment..."
            "vendor_query" -> "Finding Vendors..."
            "show_vendors" -> "Vendor Information"
            "completed" -> "Diagnosis Complete"
            else -> currentNode?.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            } ?: "Processing..."
        }
    }
    
    /**
     * Parse insurance certificate from server response
     */
    private fun parseInsuranceCertificate(certificate: InsuranceCertificate): InsuranceCertificateDetails? {
        return try {
            // Extract PDF data from raw MCP response
            val pdfBase64 = certificate.rawMcpResponse?.content?.find { 
                it.type == "resource" && it.uri?.startsWith("data:application/pdf;base64,") == true 
            }?.uri?.substringAfter("data:application/pdf;base64,")
            
            InsuranceCertificateDetails(
                policyId = certificate.policyId ?: "N/A",
                farmerName = certificate.farmerName ?: "N/A",
                farmerId = certificate.farmerId ?: "N/A",
                crop = certificate.crop ?: "N/A",
                area = certificate.areaHectare ?: 0.0,
                state = certificate.state ?: "N/A",
                companyName = certificate.companyName ?: "N/A",
                premiumPaidByFarmer = certificate.premiumPaidByFarmer ?: 0.0,
                premiumPaidByGovt = certificate.premiumPaidByGovt ?: 0.0,
                totalSumInsured = certificate.totalSumInsured ?: 0.0,
                certificateDetails = certificate.certificateDetails ?: "",
                pdfBase64 = pdfBase64
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing insurance certificate: ${e.message}")
            null
        }
    }
    
    /**
     * Parse insurance premium details from server response
     */
    private fun parseInsuranceDetails(
        context: InsuranceContext,
        premiumDetails: InsurancePremiumDetails
    ): InsuranceDetails? {
        return try {
            // Parse the premium details string to extract individual amounts
            val premiumText = premiumDetails.premiumDetails ?: return null
            
            // Extract amounts using regex patterns
            val premiumPerHectareMatch = "Premium per hectare: ₹([\\d,]+\\.\\d+)".toRegex().find(premiumText)
            val totalPremiumMatch = "Total premium: ₹([\\d,]+\\.\\d+)".toRegex().find(premiumText)
            val subsidyMatch = "Government subsidy: ₹([\\d,]+\\.\\d+)".toRegex().find(premiumText)
            val contributionMatch = "Farmer contribution: ₹([\\d,]+\\.\\d+)".toRegex().find(premiumText)
            
            InsuranceDetails(
                crop = context.crop ?: premiumDetails.crop ?: "Unknown",
                state = context.state ?: premiumDetails.state ?: "Unknown",
                area = context.areaHectare ?: premiumDetails.areaHectare ?: 0.0,
                premiumPerHectare = premiumPerHectareMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0,
                totalPremium = totalPremiumMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0,
                governmentSubsidy = subsidyMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0,
                farmerContribution = contributionMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0,
                disease = context.disease
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing insurance details: ${e.message}")
            null
        }
    }
}
