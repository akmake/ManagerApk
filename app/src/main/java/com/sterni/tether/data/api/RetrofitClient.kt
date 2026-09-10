package com.sterni.tether.data.api

import com.sterni.tether.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the app's Retrofit/OkHttp stack.
 *
 * Both the direct callers (`RetrofitClient.tetherApi`, `RetrofitClient.create(...)`) and the Hilt
 * graph (see [NetworkModule], whose providers delegate here) share this one client — so the
 * device-auth interceptor, timeouts and BASE_URL are defined in exactly one place.
 */
object RetrofitClient {
    // Supplies the per-device secret token (from TetherPolicyManager).
    // Set once in TetherApp.onCreate(). Read at request time (not captured) so it always reflects the latest token.
    @Volatile private var deviceTokenProvider: () -> String? = { null }
    fun setDeviceTokenProvider(provider: () -> String?) { deviceTokenProvider = provider }

    // Attaches X-Device-Token to device-facing endpoints only, and advertises support for
    // per-command acknowledgement (X-Cmd-Ack) so the server delivers commands at-least-once.
    private val deviceAuthInterceptor = Interceptor { chain ->
        val req = chain.request()
        if (!req.url.encodedPath.contains("/devices/")) return@Interceptor chain.proceed(req)
        val builder = req.newBuilder().header("X-Cmd-Ack", "1")
        val token = deviceTokenProvider()
        if (!token.isNullOrEmpty()) builder.header("X-Device-Token", token)
        chain.proceed(builder.build())
    }

    // Invoked when the server rejects an admin token (401). Set once in TetherApp.onCreate().
    // Admin tokens never expire on their own, so a 401 means the session was genuinely revoked —
    // an explicit logout elsewhere, or the account being deactivated — and the stale token should
    // be dropped so the next launch shows the login screen instead of a dashboard that 401s.
    @Volatile private var onAdminUnauthorized: () -> Unit = {}
    fun setAdminUnauthorizedHandler(handler: () -> Unit) { onAdminUnauthorized = handler }

    private val adminAuthInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        // Only admin-panel calls carry an admin token; the login call itself 401s on a wrong
        // password and must not be treated as a revoked session.
        val path = request.url.encodedPath
        if (response.code == 401 &&
            path.contains("/tether/") &&
            !path.contains("/devices/") &&
            !path.endsWith("/auth/login")
        ) {
            onAdminUnauthorized()
        }
        response
    }

    // Full request/response logging in debug builds only; silent in release.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(deviceAuthInterceptor)
            .addInterceptor(adminAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            // Timeouts are a safe superset of the two previous configs (device sync + study/PDF fetch).
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val newsService: NewsService by lazy { retrofit.create(NewsService::class.java) }
    val tetherApi: TetherApiService by lazy { retrofit.create(TetherApiService::class.java) }
    val authApi: AuthApiService by lazy { retrofit.create(AuthApiService::class.java) }
    val adminApi: AdminApiService by lazy { retrofit.create(AdminApiService::class.java) }
    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val articleService: ArticleService by lazy { retrofit.create(ArticleService::class.java) }
    val zmanimService: ZmanimService by lazy { retrofit.create(ZmanimService::class.java) }

    fun <T> create(service: Class<T>): T = retrofit.create(service)
}
