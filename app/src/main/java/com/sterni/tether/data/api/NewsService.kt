package com.sterni.tether.data.api

import com.sterni.tether.data.model.ArticleResponse
import com.sterni.tether.data.model.NewsFeedResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsService {

    @GET("news/feed")
    suspend fun getNewsFeed(): Response<NewsFeedResponse>

    @GET("news/article")
    suspend fun getArticle(
        @Query("url") url: String
    ): Response<ArticleResponse>
}
