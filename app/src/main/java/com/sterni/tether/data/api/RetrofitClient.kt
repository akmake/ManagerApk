package com.sterni.tether.data.api

import com.sterni.tether.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

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

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(deviceAuthInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
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
