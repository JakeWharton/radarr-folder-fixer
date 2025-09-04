package com.jakewharton.radarr.folderfixer

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

internal interface RadarrApi {
	@GET("api/v3/movie")
	@Headers("Accept: application/json")
	suspend fun listMovies(
		@Header("X-Api-Key") apiKey: String,
	): List<Movie>

	@GET("api/v3/movie/{id}/folder")
	@Headers("Accept: application/json")
	suspend fun getMovieFolder(
		@Header("X-Api-Key") apiKey: String,
		@Path("id") id: Long,
	): MovieFolder

	@PUT("api/v3/movie/editor")
	@Headers("Accept: application/json")
	suspend fun editMovie(
		@Header("X-Api-Key") apiKey: String,
		@Body body: MovieEdit,
	)

	@GET("api/v3/tag")
	@Headers("Accept: application/json")
	suspend fun listTags(
		@Header("X-Api-Key") apiKey: String,
	): List<Tag>

	@POST("api/v3/tag")
	@Headers("Accept: application/json")
	suspend fun createTag(
		@Header("X-Api-Key") apiKey: String,
		@Body body: Tag,
	): Tag
}

@Serializable
internal data class MovieEdit(
	val movieIds: List<Long>,
	val tags: List<Long>,
	val applyTags: String,
)

@Serializable
internal data class MovieFolder(
	val folder: String,
)

@Serializable
internal data class Movie(
	val id: Long,
	val title: String,
	val folderName: String,
	val rootFolderPath: String,
	val tags: List<Long>,
	val hasFile: Boolean,
)

@Serializable
internal data class Tag(
	val id: Long,
	val label: String,
)
