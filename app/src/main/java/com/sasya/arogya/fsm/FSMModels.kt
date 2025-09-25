package com.sasya.arogya.fsm

import com.google.gson.annotations.SerializedName

/**
 * Data models for FSM Agent communication
 * Based on the FSM agent's API structure
 */

// Request models
data class FSMChatRequest(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("message") val message: String,
    @SerializedName("image_b64") val imageB64: String? = null,
    @SerializedName("context") val context: Map<String, Any>? = null
)

// Response models
data class FSMChatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("messages") val messages: List<Any>,
    @SerializedName("state") val state: String? = null
)

// Streaming event models
data class FSMStateUpdate(
    @SerializedName("current_node") val currentNode: String? = null,
    @SerializedName("previous_node") val previousNode: String? = null,
    @SerializedName("next_action") val nextAction: String? = null,
    @SerializedName("requires_user_input") val requiresUserInput: Boolean? = null,
    @SerializedName("messages") val messages: List<FSMMessage>? = null,
    @SerializedName("assistant_response") val assistantResponse: String? = null,
    @SerializedName("follow_up_items") val followUpItems: List<String>? = null,
    @SerializedName("is_complete") val isComplete: Boolean? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("classification_result") val classificationResult: Map<String, Any>? = null,
    @SerializedName("prescription_data") val prescriptionDetails: Map<String, Any>? = null,
    @SerializedName("treatment_recommendations") val treatmentRecommendations: List<Map<String, Any>>? = null,
    @SerializedName("preventive_measures") val preventiveMeasures: List<String>? = null,
    @SerializedName("vendor_details") val vendorDetails: Map<String, Any>? = null
)

// Message models
data class FSMMessage(
    @SerializedName("role") val role: String, // "user" or "assistant"
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: String? = null
)

// Chat message for UI display
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val followUpItems: List<String>? = null,
    val state: String? = null,
    val attentionOverlayBase64: String? = null,
    val diseaseName: String? = null,
    val confidence: Double? = null,
    val structuredPrescription: StructuredPrescription? = null
)

// Follow-up item for UI
data class FollowUpItem(
    val text: String,
    val isClicked: Boolean = false
)

// FSM session state
data class FSMSessionState(
    var sessionId: String? = null,
    var currentNode: String = "initial",
    var previousNode: String? = null,
    var isComplete: Boolean = false,
    val messages: MutableList<ChatMessage> = mutableListOf()
)

// Server event wrapper
data class ServerEvent(
    val event: String,
    val data: String
)

// Assistant response data for dedicated assistant_response events
data class AssistantResponseData(
    @SerializedName("assistant_response") val assistant_response: String? = null
)

// Attention overlay data for visualization
data class AttentionOverlayData(
    @SerializedName("attention_overlay") val attentionOverlay: String? = null,
    @SerializedName("disease_name") val diseaseName: String? = null,
    @SerializedName("confidence") val confidence: Double? = null,
    @SerializedName("source_node") val sourceNode: String? = null
)

// ========== STRUCTURED PRESCRIPTION MODELS ==========

data class StructuredPrescription(
    @SerializedName("diagnosis") val diagnosis: Diagnosis? = null,
    @SerializedName("immediate_treatment") val immediateTreatment: ImmediateTreatment? = null,
    @SerializedName("weekly_treatment_plan") val weeklyTreatmentPlan: WeeklyTreatmentPlan? = null,
    @SerializedName("medicine_recommendations") val medicineRecommendations: MedicineRecommendations? = null,
    @SerializedName("prevention") val prevention: Prevention? = null,
    @SerializedName("additional_notes") val additionalNotes: AdditionalNotes? = null,
    @SerializedName("disease_name") val diseaseName: String? = null,
    @SerializedName("plant_type") val plantType: String? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("season") val season: String? = null
)

data class Diagnosis(
    @SerializedName("disease_name") val diseaseName: String? = null,
    @SerializedName("symptoms") val symptoms: List<String>? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("affected_parts") val affectedParts: List<String>? = null
)

data class ImmediateTreatment(
    @SerializedName("actions") val actions: List<String>? = null,
    @SerializedName("emergency_measures") val emergencyMeasures: List<String>? = null,
    @SerializedName("timeline") val timeline: String? = null
)

data class WeeklyTreatmentPlan(
    @SerializedName("week_1") val week1: WeeklyPlan? = null,
    @SerializedName("week_2") val week2: WeeklyPlan? = null,
    @SerializedName("week_3") val week3: WeeklyPlan? = null,
    @SerializedName("week_4") val week4: WeeklyPlan? = null
)

data class WeeklyPlan(
    @SerializedName("actions") val actions: List<String>? = null,
    @SerializedName("monitoring") val monitoring: String? = null,
    @SerializedName("expected_results") val expectedResults: String? = null
)

data class MedicineRecommendations(
    @SerializedName("primary_treatment") val primaryTreatment: Treatment? = null,
    @SerializedName("secondary_treatment") val secondaryTreatment: Treatment? = null,
    @SerializedName("organic_alternatives") val organicAlternatives: List<OrganicTreatment>? = null
)

data class Treatment(
    @SerializedName("medicine_name") val medicineName: String? = null,
    @SerializedName("active_ingredient") val activeIngredient: String? = null,
    @SerializedName("dosage") val dosage: String? = null,
    @SerializedName("application_method") val applicationMethod: String? = null,
    @SerializedName("frequency") val frequency: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("precautions") val precautions: List<String>? = null,
    @SerializedName("when_to_use") val whenToUse: String? = null
)

data class OrganicTreatment(
    @SerializedName("name") val name: String? = null,
    @SerializedName("preparation") val preparation: String? = null,
    @SerializedName("application") val application: String? = null
)

data class Prevention(
    @SerializedName("cultural_practices") val culturalPractices: List<String>? = null,
    @SerializedName("crop_management") val cropManagement: List<String>? = null,
    @SerializedName("environmental_controls") val environmentalControls: List<String>? = null,
    @SerializedName("monitoring_schedule") val monitoringSchedule: String? = null
)

data class AdditionalNotes(
    @SerializedName("weather_considerations") val weatherConsiderations: String? = null,
    @SerializedName("crop_stage_specific") val cropStageSpecific: String? = null,
    @SerializedName("regional_considerations") val regionalConsiderations: String? = null,
    @SerializedName("follow_up") val followUp: String? = null
)
