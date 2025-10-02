package com.sasya.arogya.fsm

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor to handle CSRF token authentication
 * Automatically fetches and includes CSRF tokens in requests
 */
class CsrfTokenInterceptor : Interceptor {
    
    companion object {
        private const val TAG = "CsrfTokenInterceptor"
        private const val CSRF_HEADER_NAME = "X-CSRF-TOKEN"
        private const val CSRF_COOKIE_NAME = "XSRF-TOKEN"
        
        /**
         * Check if the host is a local development URL that should skip CSRF protection
         */
        private fun isLocalDevelopmentUrl(host: String): Boolean {
            return host == "localhost" ||
                   host == "127.0.0.1" ||
                   host == "10.0.2.2" ||  // Android emulator localhost
                   host.startsWith("192.168.") ||  // Local network
                   host.startsWith("10.") ||       // Private network ranges
                   host.startsWith("172.16.") ||   // Private network ranges
                   host.endsWith(".local")         // mDNS local domains
        }
        
        /**
         * Check if the host is a production cluster URL that should skip CSRF protection
         * (Some servers may be misconfigured to require CSRF for API endpoints)
         */
        private fun isProductionClusterUrl(host: String): Boolean {
            return host.contains("opentlc.com") ||
                   host.contains("cluster-") ||
                   host.contains("sandbox")
        }
    }
    
    private var csrfToken: String? = null
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        Log.d(TAG, "Intercepting request to: ${originalRequest.url}")
        
        // Skip CSRF for GET requests and health checks
        if (originalRequest.method == "GET" || 
            originalRequest.url.encodedPath.contains("health") ||
            originalRequest.url.encodedPath.contains("csrf-token")) {
            Log.d(TAG, "Skipping CSRF for GET/health/csrf-token request")
            return chain.proceed(originalRequest)
        }
        
        // Skip CSRF for local development URLs
        val host = originalRequest.url.host
        Log.d(TAG, "Checking host: $host")
        if (isLocalDevelopmentUrl(host)) {
            Log.d(TAG, "Skipping CSRF for local development URL: $host")
            return chain.proceed(originalRequest)
        }
        
        // Skip CSRF for production cluster URLs (temporarily to fix server issues)
        if (isProductionClusterUrl(host)) {
            Log.d(TAG, "Skipping CSRF for production cluster URL: $host")
            return chain.proceed(originalRequest)
        }
        
        // Try to get CSRF token if we don't have one
        if (csrfToken == null) {
            try {
                fetchCsrfToken(chain)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch CSRF token, proceeding without it: ${e.message}")
            }
        }
        
        // Add CSRF token to request if available
        val requestBuilder = originalRequest.newBuilder()
        
        csrfToken?.let { token ->
            requestBuilder.addHeader(CSRF_HEADER_NAME, token)
            Log.d(TAG, "Added CSRF token to request: ${originalRequest.url}")
        }
        
        // Also try common alternatives
        requestBuilder.addHeader("X-Requested-With", "XMLHttpRequest")
        
        val response = chain.proceed(requestBuilder.build())
        
        // If we get a 403/401, try to refresh CSRF token
        if (response.code == 403 || response.code == 401) {
            Log.w(TAG, "Got ${response.code}, attempting to refresh CSRF token")
            response.close()
            
            try {
                fetchCsrfToken(chain)
                val newRequestBuilder = originalRequest.newBuilder()
                csrfToken?.let { token ->
                    newRequestBuilder.addHeader(CSRF_HEADER_NAME, token)
                }
                newRequestBuilder.addHeader("X-Requested-With", "XMLHttpRequest")
                
                return chain.proceed(newRequestBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh CSRF token: ${e.message}")
            }
        }
        
        return response
    }
    
    private fun fetchCsrfToken(chain: Interceptor.Chain) {
        try {
            // Try to get CSRF token from /csrf-token endpoint
            val csrfRequest = chain.request().newBuilder()
                .url(chain.request().url.newBuilder()
                    .encodedPath("/csrf-token")
                    .build())
                .get()
                .build()
            
            val csrfResponse = chain.proceed(csrfRequest)
            if (csrfResponse.isSuccessful) {
                val responseBody = csrfResponse.body?.string()
                if (responseBody != null) {
                    // Try to parse JSON response for token
                    val tokenPattern = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"")
                    val match = tokenPattern.find(responseBody)
                    if (match != null) {
                        csrfToken = match.groupValues[1]
                        Log.d(TAG, "Successfully fetched CSRF token")
                    }
                }
            }
            csrfResponse.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching CSRF token: ${e.message}")
        }
    }
}
