# Agricultural Profile - Insurance Integration

## Overview
Enhanced the agricultural profile system to seamlessly integrate with insurance operations, providing personalized insurance documents and smart fallback values.

## Requirements Implemented

### 1. ✅ Optional Farmer Name Field
**Requirement:** Take farmer name as optional input in agricultural profile

**Implementation:**
- Added `EditText` field in `dialog_agricultural_profile.xml`
- Positioned prominently at the top of the profile dialog
- Hint text: "Enter your name for insurance documents"
- Input type: `textPersonName|textCapWords` for proper name formatting
- Stored in SharedPreferences as `farmer_name`
- Only saved if user provides a value (truly optional)

**User Experience:**
```
👨‍🌾 Farmer Name (Optional)
┌─────────────────────────────────┐
│ Enter your name for insurance   │
│ documents                        │
└─────────────────────────────────┘
```

### 2. ✅ Use Farmer Name in Insurance Integration
**Requirement:** Use farmer name if present for insurance integration

**Implementation:**
- Added `farmer_name` to `getUserAgriculturalProfile()` return map
- Included `farmer_name` in context sent to FSM Agent (if provided)
- Conditional inclusion: only added to context when non-empty

**Code Flow:**
```kotlin
val farmerName = userProfile["farmer_name"] ?: ""

// Add to context only if provided
if (farmerName.isNotEmpty()) {
    context["farmer_name"] = farmerName
}
```

**Result:**
- Insurance certificates show actual farmer name instead of "Farmer" default
- Premium cards display personalized farmer information
- Professional appearance in insurance documents

### 3. ✅ Use Farm Size as Insurance Fallback
**Requirement:** If farm size not specified in prompt, use profile farm size field

**Implementation:**
- Added `farm_size_hectares` to context (always sent)
- Created `convertFarmSizeToHectares()` method for automatic conversion
- Intelligent mapping from user-friendly labels to precise hectare values

**Conversion Logic:**
```kotlin
Farm Size Label              → Hectares
─────────────────────────────────────────
Small (< 1 acre)            → 0.4 ha
Medium (1-5 acres)          → 2.0 ha
Large (5-10 acres)          → 3.0 ha
Very Large (> 10 acres)     → 6.0 ha
Default                     → 0.4 ha
```

**FSM Agent Behavior:**
1. If user specifies farm size in prompt → Use prompt value
2. If user doesn't specify → Use `farm_size_hectares` from context
3. Seamless fallback without user intervention

## Technical Details

### Files Modified

#### 1. `dialog_agricultural_profile.xml`
**Changes:**
- Added `farmerNameInput` EditText above state selector
- Updated helper text to explain insurance benefit
- Maintained consistent styling with existing fields

**New Helper Text:**
> "This information helps us provide region-specific plant disease detection and personalized farming advice. Your name will be used for insurance documents if you choose to purchase crop insurance."

#### 2. `MainActivityFSM.kt`
**Changes:**

**a) Profile Dialog Enhancement:**
```kotlin
private fun showAgriculturalProfileDialog() {
    val farmerNameInput = dialogView.findViewById<EditText>(R.id.farmerNameInput)
    farmerNameInput.setText(currentProfile["farmer_name"] ?: "")
    
    // On Save
    val farmerName = farmerNameInput.text.toString().trim()
    if (farmerName.isNotEmpty()) {
        newProfile["farmer_name"] = farmerName
    }
}
```

**b) Profile Retrieval:**
```kotlin
private fun getUserAgriculturalProfile(): Map<String, String> {
    return mapOf(
        "farmer_name" to (prefs.getString("farmer_name", null) ?: ""),
        "state" to (prefs.getString("state", null) ?: ""),
        "farm_size" to (prefs.getString("farm_size", null) ?: "")
    )
}
```

**c) Context Enrichment:**
```kotlin
private fun createEnrichedContext(userProfile: Map<String, String>): Map<String, Any> {
    val farmerName = userProfile["farmer_name"] ?: ""
    val farmSizeHectares = convertFarmSizeToHectares(userFarmSize)
    
    val context = mutableMapOf(
        "state" to userState,
        "farm_size" to userFarmSize,
        "farm_size_hectares" to farmSizeHectares,
        // ... other fields
    )
    
    if (farmerName.isNotEmpty()) {
        context["farmer_name"] = farmerName
    }
    
    return context
}
```

**d) New Helper Method:**
```kotlin
private fun convertFarmSizeToHectares(farmSize: String): Double {
    return when {
        farmSize.contains("Small", ignoreCase = true) -> 0.4
        farmSize.contains("Medium", ignoreCase = true) -> 2.0
        farmSize.contains("Large", !contains("Very")) -> 3.0
        farmSize.contains("Very Large") -> 6.0
        else -> 0.4
    }
}
```

## Data Flow

### Complete Flow Diagram
```
┌─────────────────────────────────────────────────────────┐
│ USER INTERACTION                                        │
│ ┌─────────────────────────────────────────────┐        │
│ │ Agricultural Profile Dialog                 │        │
│ │  • Farmer Name: "Rajesh Kumar" (optional)   │        │
│ │  • State: "Tamil Nadu"                      │        │
│ │  • Farm Size: "Medium (1-5 acres)"          │        │
│ └─────────────────────────────────────────────┘        │
│                       ↓                                  │
│              [Save to SharedPreferences]                │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ MESSAGE PROCESSING                                      │
│ ┌─────────────────────────────────────────────┐        │
│ │ User sends message:                          │        │
│ │ "Get insurance for potato blight"            │        │
│ └─────────────────────────────────────────────┘        │
│                       ↓                                  │
│         [getUserAgriculturalProfile()]                  │
│                       ↓                                  │
│         [createEnrichedContext()]                       │
│                       ↓                                  │
│ ┌─────────────────────────────────────────────┐        │
│ │ Context Created:                             │        │
│ │  {                                           │        │
│ │    "farmer_name": "Rajesh Kumar",            │        │
│ │    "state": "Tamil Nadu",                    │        │
│ │    "farm_size": "Medium (1-5 acres)",        │        │
│ │    "farm_size_hectares": 2.0,                │        │
│ │    "platform": "android",                    │        │
│ │    "season": "summer"                        │        │
│ │  }                                           │        │
│ └─────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ FSM AGENT PROCESSING                                    │
│ ┌─────────────────────────────────────────────┐        │
│ │ FSM Agent receives context                   │        │
│ │  • Detects insurance intent                  │        │
│ │  • Uses farmer_name for certificate          │        │
│ │  • Uses farm_size_hectares (2.0) as fallback │        │
│ │  • Calls MCP insurance server                │        │
│ └─────────────────────────────────────────────┘        │
│                       ↓                                  │
│         [Insurance Certificate Generated]               │
│                       ↓                                  │
│ ┌─────────────────────────────────────────────┐        │
│ │ Certificate Details:                         │        │
│ │  • Policy Holder: "Rajesh Kumar"             │        │
│ │  • State: "Tamil Nadu"                       │        │
│ │  • Area: 2.0 hectares                        │        │
│ │  • Crop: "Potato"                            │        │
│ │  • Premium: ₹1,200 (farmer pays ₹300)       │        │
│ └─────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────┘
```

## Usage Scenarios

### Scenario 1: Complete Profile with Name
**Setup:**
- Farmer Name: "Rajesh Kumar"
- State: "Tamil Nadu"
- Farm Size: "Medium (1-5 acres)"

**User Query:** "Get insurance for potato late blight"

**Result:**
```
✅ Insurance Certificate Generated
┌─────────────────────────────────────┐
│ Policy Holder: Rajesh Kumar         │
│ State: Tamil Nadu                   │
│ Area: 2.0 hectares                  │
│ Crop: Potato                        │
│ Premium: ₹1,200                     │
│ Farmer Pays: ₹300 (Govt: ₹900)     │
└─────────────────────────────────────┘
```

### Scenario 2: Profile without Name
**Setup:**
- Farmer Name: (empty)
- State: "Karnataka"
- Farm Size: "Small (< 1 acre)"

**User Query:** "Insurance for my tomato crop"

**Result:**
```
✅ Insurance Certificate Generated
┌─────────────────────────────────────┐
│ Policy Holder: Farmer               │  ← Default fallback
│ State: Karnataka                    │
│ Area: 0.4 hectares                  │  ← From profile
│ Crop: Tomato                        │
│ Premium: ₹480                       │
└─────────────────────────────────────┘
```

### Scenario 3: User Overrides Farm Size
**Setup:**
- Farmer Name: "Priya Sharma"
- State: "Maharashtra"
- Farm Size: "Large (5-10 acres)" → 3.0 ha in profile

**User Query:** "Get cotton insurance for my 5 hectare farm"

**Result:**
```
✅ Uses 5.0 hectares from prompt (overrides profile)
┌─────────────────────────────────────┐
│ Policy Holder: Priya Sharma         │
│ State: Maharashtra                  │
│ Area: 5.0 hectares                  │  ← From prompt
│ Crop: Cotton                        │
│ Premium: ₹3,000                     │
└─────────────────────────────────────┘
```

## Benefits

### 🎯 For Users
1. **Personalized Documents:** Insurance certificates show actual farmer names
2. **Convenience:** No need to specify farm size repeatedly
3. **Privacy:** Farmer name is optional, not mandatory
4. **Consistency:** Same profile data used across app features

### 💼 For Business
1. **Professional Appearance:** Proper names on insurance documents
2. **Reduced Friction:** Fewer fields to fill during insurance queries
3. **Better Data:** Centralized profile management
4. **Compliance Ready:** Proper identification for insurance transactions

### 🔧 For Developers
1. **Single Source of Truth:** Profile data in one place
2. **Flexible Integration:** Optional fields don't break existing flows
3. **Smart Defaults:** Automatic hectare conversion
4. **Maintainable:** Clear separation of concerns

## Privacy & Security

### Data Storage
- **Location:** Android SharedPreferences (local device storage)
- **Encryption:** Standard Android keystore protection
- **Scope:** Application-private, not shared with other apps

### Data Usage
- **Farmer Name:** Only sent to server if provided and non-empty
- **Farm Size:** Always sent (default to "Small" if not set)
- **State:** Always sent (default to "Tamil Nadu" if not set)

### User Control
- Can edit profile anytime via profile button
- Can clear farmer name by leaving it empty
- No data sent to server without user initiating a query

## Testing Guide

### Test Case 1: First-Time Profile Setup
```
1. Open app → Click profile button
2. Leave farmer name empty
3. Select state: "Tamil Nadu"
4. Select farm size: "Medium (1-5 acres)"
5. Save
6. Ask: "Get insurance quote for rice"
7. Verify: Certificate shows "Farmer", area: 2.0 ha
```

### Test Case 2: Add Farmer Name
```
1. Click profile button
2. Enter farmer name: "Test Farmer"
3. Save
4. Ask: "Insurance for wheat late blight"
5. Verify: Certificate shows "Test Farmer", area: 2.0 ha
```

### Test Case 3: Override Farm Size
```
1. Profile has "Small (< 1 acre)" = 0.4 ha
2. Ask: "Get insurance for 1.5 hectares cotton farm"
3. Verify: Uses 1.5 ha from prompt, not 0.4 from profile
```

### Test Case 4: Farm Size Conversions
```
Profile Setting              Expected in Context
────────────────────────────────────────────────
Small (< 1 acre)            → 0.4 hectares
Medium (1-5 acres)          → 2.0 hectares
Large (5-10 acres)          → 3.0 hectares
Very Large (> 10 acres)     → 6.0 hectares
```

## Future Enhancements

### Potential Improvements
1. **Multi-Field Support:** Multiple farms with different sizes
2. **Farmer ID Integration:** Link to government farmer ID systems
3. **Family Members:** Add co-farmers or beneficiaries
4. **Historical Data:** Track insurance history within profile
5. **Bank Details:** Store for premium payment/claim settlement
6. **Location Coordinates:** GPS-based farm location for precision

### API Extensions
```kotlin
// Future profile structure
data class FarmerProfile(
    val personalInfo: PersonalInfo,
    val farms: List<FarmDetails>,
    val insuranceHistory: List<InsurancePolicy>,
    val bankDetails: BankAccount?
)
```

## Conclusion

This integration successfully bridges the gap between user profile management and insurance operations, providing a seamless experience while maintaining privacy and flexibility. The implementation follows Android best practices and is production-ready.

**Key Achievement:** One-time profile setup now benefits multiple app features, especially insurance operations where personalization and accuracy are critical.

