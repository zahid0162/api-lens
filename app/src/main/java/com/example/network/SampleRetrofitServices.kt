package com.example.network

import com.apilens.attachApiLens
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class LoginPayload(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class OrderPayload(
    val sku: String,
    val quantity: Int
)

@JsonClass(generateAdapter = true)
data class ProfilePayload(
    val displayName: String,
    val role: String
)

@JsonClass(generateAdapter = true)
data class PreferencesPayload(
    val theme: String,
    val compactDensity: Boolean
)

/**
 * First Retrofit service demonstrating catalog & analytics endpoints.
 */
interface CatalogApiService {
    @GET("api/products")
    suspend fun getProducts(
        @Query("category") category: String = "hardware",
        @Query("api_key") apiKey: String = "demo_key_12345"
    ): Response<ResponseBody>

    @GET("api/analytics/slow")
    suspend fun getSlowAnalytics(): Response<ResponseBody>

    @GET("api/timeout")
    suspend fun triggerTimeout(): Response<ResponseBody>

    @GET
    suspend fun callExternalUrl(@Url fullUrl: String): Response<ResponseBody>
}

/**
 * Second Retrofit service demonstrating auth, orders, and mutation endpoints
 * on a separate Retrofit instance also monitored by API Lens.
 */
interface AccountOrdersApiService {
    @POST("api/login")
    suspend fun login(
        @Header("Authorization") authHeader: String = "Bearer pre_auth_token_999",
        @Body payload: LoginPayload
    ): Response<ResponseBody>

    @GET("api/customers")
    suspend fun getCustomers(
        @Header("Authorization") bearer: String = "Bearer expired_token_401"
    ): Response<ResponseBody>

    @POST("api/orders")
    suspend fun createOrder(@Body payload: OrderPayload): Response<ResponseBody>

    @PUT("api/profile")
    suspend fun updateProfile(@Body payload: ProfilePayload): Response<ResponseBody>

    @PATCH("api/preferences")
    suspend fun patchPreferences(@Body payload: PreferencesPayload): Response<ResponseBody>

    @DELETE("api/session")
    suspend fun logoutSession(
        @Header("Cookie") cookie: String = "session_id=abc123secret"
    ): Response<Unit>
}

object SampleNetworkClient {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .attachApiLens()
            .build()
    }

    fun createCatalogService(baseUrl: String): CatalogApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CatalogApiService::class.java)
    }

    fun createAccountOrdersService(baseUrl: String): AccountOrdersApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AccountOrdersApiService::class.java)
    }
}
