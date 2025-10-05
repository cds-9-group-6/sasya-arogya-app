# Context Passing Debug Guide - Farmer Name & Farm Size Issue

## Issue Report

**Date:** October 5, 2025  
**Reporter:** User  
**Severity:** High - Core feature not working

### Problem Statement
Despite setting farmer name to "Aditya" in the agricultural profile, the FSM agent backend shows `farmer_name='Farmer'` (the default fallback value) in insurance operations.

### Backend Evidence
```
2025-10-05 20:49:06,360 - fsm_agent.core.nodes.insurance_node - INFO - Extracted insurance context:
{'disease': None, 'crop': 'Potato', 'state': 'Karnataka', 'farmer_name': 'Farmer'}
                                                           ^^^^^^^^^^^^^^^^^^^^
```

**Expected:** `'farmer_name': 'Aditya'` (from profile)  
**Actual:** `'farmer_name': 'Farmer'` (default fallback)

### Similarly Missing
- `area_hectare` should be `2.0` (from profile "Medium" farm size)
- Likely also missing from backend context

## App-Side Investigation

### Current Implementation

#### 1. Profile Storage & Retrieval
**File:** `MainActivityFSM.kt`

```kotlin
private fun getUserAgriculturalProfile(): Map<String, String> {
    val prefs = getSharedPreferences("agricultural_profile", MODE_PRIVATE)
    return mapOf(
        "farmer_name" to (prefs.getString("farmer_name", null) ?: ""),
        "state" to (prefs.getString("state", null) ?: ""),
        "farm_size" to (prefs.getString("farm_size", null) ?: "")
    )
}
```

✅ **Status:** Correctly reads from SharedPreferences

#### 2. Context Creation
**File:** `MainActivityFSM.kt` - `createEnrichedContext()`

```kotlin
val farmerName = userProfile["farmer_name"] ?: ""
val farmSizeHectares = convertFarmSizeToHectares(userFarmSize)

val context = mutableMapOf(
    "state" to userState,
    "area_hectare" to farmSizeHectares,  // For insurance fallback
    // ... other fields
)

// Conditionally add farmer_name if provided
if (farmerName.isNotEmpty()) {
    context["farmer_name"] = farmerName
}
```

✅ **Status:** Correctly creates context with farmer_name and area_hectare

#### 3. Request Construction
**File:** `FSMStreamHandler.kt` - `createChatRequest()`

```kotlin
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
        context = context  // ✅ Context is included
    )
}
```

✅ **Status:** Context properly passed to request model

#### 4. API Call
**File:** `FSMApiService.kt`

```kotlin
@POST("sasya-chikitsa/chat-stream")
@Headers(
    "Accept: text/event-stream",
    "Content-Type: application/json",
    "Cache-Control: no-cache"
)
@Streaming
fun chatStream(@Body request: FSMChatRequest): Call<ResponseBody>
```

**Request Model:** `FSMChatRequest`
```kotlin
data class FSMChatRequest(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("message") val message: String,
    @SerializedName("image_b64") val imageB64: String? = null,
    @SerializedName("context") val context: Map<String, Any>? = null  // ✅ Serialized
)
```

✅ **Status:** Retrofit + Gson will serialize context to JSON

## Enhanced Logging (Added)

### Commit: `61aa3fa`

Added comprehensive logging in `sendToFSMAgent()`:

```kotlin
// 1. Profile retrieval
Log.d(TAG, "🌾 User agricultural profile: $userProfile")

// 2. Context creation
Log.d(TAG, "📤 Sending context to server: $context")

// 3. Request details
Log.d(TAG, "📨 Complete request to FSM agent:")
Log.d(TAG, "  └─ Message: $message")
Log.d(TAG, "  └─ Session ID: ${currentSessionState.sessionId}")
Log.d(TAG, "  └─ Has Image: ${imageBase64 != null}")
Log.d(TAG, "  └─ Context keys: ${context.keys}")
Log.d(TAG, "  └─ farmer_name in context: ${context["farmer_name"]}")
Log.d(TAG, "  └─ area_hectare in context: ${context["area_hectare"]}")
Log.d(TAG, "  └─ state in context: ${context["state"]}")

// 4. Farm size conversion
Log.d(TAG, "🚜 Sending area_hectare to FSM agent: $farmSizeHectares (for insurance fallback)")
```

### HTTP Payload Logging

**File:** `FSMRetrofitClient.kt`

```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY  // ✅ Full request/response logging
}
```

This will log the actual JSON sent over HTTP:
```json
POST /sasya-chikitsa/chat-stream HTTP/1.1
Content-Type: application/json

{
  "session_id": "uuid-here",
  "message": "Get insurance for potato",
  "image_b64": null,
  "context": {
    "platform": "android",
    "state": "Karnataka",
    "farmer_name": "Aditya",      // ← Should see this
    "area_hectare": 2.0,           // ← Should see this
    "farm_size": "Medium (1-5 acres)",
    // ... other fields
  }
}
```

## Testing Procedure

### Step 1: Setup Profile
1. Open Sasya Arogya app
2. Click profile button (top-right)
3. Enter values:
   - **Farmer Name:** Aditya
   - **State:** Karnataka
   - **Farm Size:** Medium (1-5 acres)
4. Save profile

### Step 2: Trigger Insurance Operation
1. Send message: "Get insurance for potato"
2. Wait for response

### Step 3: Check Android Logcat

**Filter:** `tag:MainActivityFSM`

**Expected Logs:**

```
🌾 User agricultural profile: {farmer_name=Aditya, state=Karnataka, farm_size=Medium (1-5 acres)}
🏛️ Using state: Karnataka, farm size: Medium (1-5 acres) (2.0 ha), season: autumn
🚜 Sending area_hectare to FSM agent: 2.0 (for insurance fallback)
👨‍🌾 Farmer name: Aditya
📤 Sending context to server: {platform=android, state=Karnataka, farmer_name=Aditya, area_hectare=2.0, ...}
📨 Complete request to FSM agent:
  └─ Message: Get insurance for potato
  └─ Session ID: <uuid>
  └─ Has Image: false
  └─ Context keys: [platform, state, farmer_name, area_hectare, ...]
  └─ farmer_name in context: Aditya
  └─ area_hectare in context: 2.0
  └─ state in context: Karnataka
```

### Step 4: Check OkHttp Logs

**Filter:** `tag:OkHttp`

**Expected HTTP Body:**
```json
{
  "session_id": "...",
  "message": "Get insurance for potato",
  "context": {
    "farmer_name": "Aditya",
    "area_hectare": 2.0,
    "state": "Karnataka",
    ...
  }
}
```

### Step 5: Check Backend Logs

**Expected (if fix works):**
```
2025-10-05 XX:XX:XX - insurance_node - INFO - Extracted insurance context:
{'disease': None, 'crop': 'Potato', 'state': 'Karnataka', 'farmer_name': 'Aditya', 'area_hectare': 2.0}
```

**Actual (current problem):**
```
2025-10-05 20:49:06 - insurance_node - INFO - Extracted insurance context:
{'disease': None, 'crop': 'Potato', 'state': 'Karnataka', 'farmer_name': 'Farmer'}
```

## Diagnosis Decision Tree

```
┌─────────────────────────────────────────────────┐
│ Is farmer_name in Android logs correct?         │
│ (Check: 🌾 User agricultural profile log)       │
└───────┬─────────────────────────┬───────────────┘
        │                         │
       NO                        YES
        │                         │
        ↓                         ↓
┌───────────────────┐    ┌──────────────────────────────┐
│ App Storage Issue │    │ Is farmer_name in context?   │
│                   │    │ (Check: 📤 Sending context)  │
│ Fix:              │    └────┬────────────────────┬────┘
│ - Check profile   │         │                    │
│   saving logic    │        NO                   YES
│ - Verify          │         │                    │
│   SharedPrefs     │         ↓                    ↓
└───────────────────┘    ┌───────────┐    ┌────────────────┐
                         │ Context   │    │ Is it in HTTP  │
                         │ Creation  │    │ payload?       │
                         │ Bug       │    │ (Check: OkHttp)│
                         │           │    └───┬────────┬───┘
                         │ Fix:      │        │        │
                         │ - Check   │       NO       YES
                         │   if      │        │        │
                         │   condition│       ↓        ↓
                         └───────────┘   ┌────────┐ ┌────────┐
                                         │ Gson   │ │BACKEND │
                                         │Serial. │ │ ISSUE  │
                                         │ Issue  │ │        │
                                         └────────┘ └────────┘
```

## Most Likely Root Causes

### Hypothesis 1: Backend Session Context Handling ⚠️ **MOST LIKELY**

**Issue:** FSM agent backend may only read context from the **first message** in a session.

**Behavior:**
```
Message 1 (New Session):
  App sends: {message: "Hi", context: {...}}
  Backend: Stores context in session state ✅

Message 2 (Existing Session):
  App sends: {message: "Get insurance", session_id: "123", context: {farmer_name: "Aditya"}}
  Backend: Uses cached context from Message 1, ignores new context ❌
```

**Evidence Supporting This:**
- App logs would show correct context being sent
- HTTP logs would show correct JSON payload
- Backend still shows default values
- Problem only occurs in existing sessions (second+ message)

**Backend Fix Needed:**
```python
# In FSM agent chat_stream endpoint
def process_message(request: ChatRequest, session: Session):
    # Current (wrong):
    context = session.context or request.context
    
    # Should be:
    context = request.context or session.context  # Per-request takes precedence
    
    # Or merge:
    context = {**session.context, **request.context}  # Update session context
```

### Hypothesis 2: Backend Context Field Extraction

**Issue:** Backend expects context fields at root level, not nested in `context` object.

**What Backend Might Expect:**
```json
{
  "message": "Get insurance",
  "farmer_name": "Aditya",    // ← At root level
  "area_hectare": 2.0,        // ← At root level
  "state": "Karnataka"
}
```

**What App Currently Sends:**
```json
{
  "message": "Get insurance",
  "context": {
    "farmer_name": "Aditya",  // ← Nested in context
    "area_hectare": 2.0,
    "state": "Karnataka"
  }
}
```

**Backend Fix Needed:**
```python
# Extract from context object
farmer_name = request.context.get('farmer_name', 'Farmer')
area_hectare = request.context.get('area_hectare', 0.4)
state = request.context.get('state', 'Tamil Nadu')
```

**Or App Fix (flatten context):**
```kotlin
// Instead of nested context
val request = FSMChatRequest(
    message = message,
    context = mapOf("farmer_name" to "Aditya", ...)
)

// Flatten to root level (if backend expects this)
val request = FSMChatRequest(
    message = message,
    // Add fields directly to request model?
)
```

### Hypothesis 3: Gson Serialization Issue (Least Likely)

**Issue:** `Map<String, Any>` not serializing correctly.

**Test:**
```kotlin
val context = mapOf<String, Any>(
    "farmer_name" to "Aditya",
    "area_hectare" to 2.0
)
val json = Gson().toJson(context)
Log.d("TEST", json)
// Should print: {"farmer_name":"Aditya","area_hectare":2.0}
```

If this works, Gson is fine. If not, need custom serializer.

## Immediate Next Steps

1. ✅ **Deploy enhanced logging build** (commit `61aa3fa`)
2. 🔄 **Run test scenario** (profile setup → insurance query)
3. 📊 **Collect logs:**
   - Android logcat (MainActivityFSM, OkHttp)
   - Backend logs (FSM agent)
4. 📝 **Analyze results:**
   - If app logs show correct values → **Backend issue**
   - If app logs show wrong values → **App issue**
5. 🔧 **Apply appropriate fix** based on diagnosis

## Backend Investigation Checklist

If app logs confirm context is sent correctly, check backend for:

- [ ] Does `chat_stream` endpoint receive `context` field?
- [ ] Is context stored in session on first message?
- [ ] Is context updated on subsequent messages?
- [ ] Does insurance_node extract fields from request.context?
- [ ] Or does it only look at session.context?
- [ ] Is there a field name mismatch (area vs area_hectare)?
- [ ] Are there any middleware/interceptors modifying the request?
- [ ] Is context being logged before insurance_node processes it?

## Potential Backend Fixes

### Fix Option 1: Always Update Session Context
```python
# In chat handler
if request.context:
    session.context.update(request.context)  # Merge new context
```

### Fix Option 2: Prioritize Request Context
```python
# In insurance node
context = {
    **session.context,      # Session defaults
    **request.context       # Per-request overrides
}
farmer_name = context.get('farmer_name', 'Farmer')
area_hectare = context.get('area_hectare', 0.4)
```

### Fix Option 3: Pass Context Explicitly
```python
# When calling insurance operations
insurance_service.get_premium(
    crop=crop,
    state=request.context.get('state'),         # From request
    farmer_name=request.context.get('farmer_name'),
    area=request.context.get('area_hectare')
)
```

## Success Criteria

After fix is applied, we should see:

✅ Android logs show: `farmer_name in context: Aditya`  
✅ HTTP logs show: `"farmer_name": "Aditya"` in JSON body  
✅ Backend logs show: `'farmer_name': 'Aditya'` in extracted context  
✅ Insurance certificate displays: "Policy Holder: Aditya"  
✅ Premium calculated for 2.0 hectares (from profile)

## Conclusion

Based on the app-side code review, the Android application is correctly:
1. ✅ Reading farmer_name from SharedPreferences
2. ✅ Including it in context map
3. ✅ Passing context to FSMChatRequest
4. ✅ Serializing via Gson
5. ✅ Sending via Retrofit

The issue is most likely on the **backend FSM agent** side, specifically in how it handles context from subsequent messages within an existing session.

The enhanced logging will provide definitive confirmation and guide the appropriate fix.

