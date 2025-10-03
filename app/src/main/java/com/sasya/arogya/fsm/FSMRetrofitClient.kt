package com.sasya.arogya.fsm

import android.content.Context
import com.sasya.arogya.config.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * FSM Agent Retrofit Client for API communication
 */
object FSMRetrofitClient {
    
    private var context: Context? = null
    private var cachedInstance: FSMApiService? = null
    
    /**
     * Initialize the client with Android context
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        // Clear cached instance when context changes
        cachedInstance = null
    }
    
    /**
     * Get FSM API service instance
     */
    val apiService: FSMApiService
        get() {
            return cachedInstance ?: createInstance().also { cachedInstance = it }
        }
    
    /**
     * Create new FSM API service instance
     */
    private fun createInstance(): FSMApiService {
        val currentContext = context 
            ?: throw IllegalStateException("FSMRetrofitClient not initialized. Call initialize(context) first.")
        
        // Get base URL from existing server config
        val baseUrl = ServerConfig.getServerUrl(currentContext)
        
        // Create logging interceptor for debugging
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Create CSRF token interceptor
        val csrfInterceptor = CsrfTokenInterceptor()
        
        // Configure OkHttp client with intelligent timeouts
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(csrfInterceptor) // Add CSRF interceptor first
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS) // Connection timeout
            .readTimeout(120, TimeUnit.SECONDS) // Read timeout for streaming (2 minutes)
            .writeTimeout(30, TimeUnit.SECONDS) // Write timeout
            .callTimeout(300, TimeUnit.SECONDS) // Overall call timeout (5 minutes max)
            .retryOnConnectionFailure(true)
            .build()
        
        // Create Retrofit instance
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(FSMApiService::class.java)
    }
    
    /**
     * Refresh the API service instance (e.g., when server config changes)
     */
    fun refreshInstance() {
        cachedInstance = null
    }
    
    /**
     * Test connection to FSM agent
     */
    suspend fun testConnection(): Boolean {
        return try {
            val response = apiService.healthCheck().execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get default context for requests
     */
    fun getDefaultContext(): Map<String, Any> {
        return mapOf(
            "platform" to "android",
            "app_version" to "1.0",
            "timestamp" to System.currentTimeMillis()
        )
    }
}
