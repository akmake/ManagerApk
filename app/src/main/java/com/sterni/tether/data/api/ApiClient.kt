package com.sterni.tether.data.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the API services. These delegate to [RetrofitClient] (the single source of
 * truth for the OkHttp/Retrofit stack) so the Hilt-injected services share the same client —
 * including the device-auth interceptor, timeouts and BASE_URL — as the direct callers.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthApiService(): AuthApiService = RetrofitClient.authApi

    @Provides
    @Singleton
    fun provideAdminApiService(): AdminApiService = RetrofitClient.adminApi

    @Provides
    @Singleton
    fun provideNewsService(): NewsService = RetrofitClient.newsService

    @Provides
    @Singleton
    fun provideTetherApiService(): TetherApiService = RetrofitClient.tetherApi

    // Daily-study endpoints (study/day, tehillim-chapters)
    @Provides
    @Singleton
    fun provideApiService(): ApiService = RetrofitClient.apiService

    @Provides
    @Singleton
    fun provideArticleService(): ArticleService = RetrofitClient.articleService
}
