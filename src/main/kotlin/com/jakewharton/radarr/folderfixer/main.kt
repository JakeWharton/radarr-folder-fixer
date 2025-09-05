@file:JvmName("Main")

package com.jakewharton.radarr.folderfixer

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.kevincianfarini.cardiologist.PulseBackpressureStrategy.Companion.SkipNext
import io.github.kevincianfarini.cardiologist.PulseSchedule
import io.github.kevincianfarini.cardiologist.schedulePulse
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import okhttp3.logging.LoggingEventListener
import okio.Path.Companion.toPath
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

public suspend fun main(vararg args: String) {
	val systemClock = Clock.System
	val systemTimeZone = TimeZone.currentSystemDefault()
	RadarrFolderFixerCommand(systemClock, systemTimeZone).main(args)
}

private class RadarrFolderFixerCommand(
	private val clock: Clock,
	private val timeZone: TimeZone,
) : SuspendingCliktCommand(name = "radarr-folder-fixer") {
	private val host by option(envvar = "RADARR_FOLDER_FIXER_HOST")
		.convert { it.toHttpUrl() }
		.required()
		.help("Radarr host")

	private val apiKey by option(envvar = "RADARR_FOLDER_FIXER_API_KEY")
		.required()
		.help("Radarr API key")

	private val tag by option(envvar = "RADARR_FOLDER_FIXER_TAG")
		.default("folder-mismatch")
		.help("Tag to add to movies with mismatched folders")

	private val ignoreTag by option(envvar = "RADARR_FOLDER_FIXER_IGNORE_TAG")
		.help("Tag indicating the tool should ignore folder mismatches")

	private val dryRun by option(envvar = "RADARR_FOLDER_FIXER_DRY_RUN")
		.flag()
		.help("Print actions instead of performing them")

	private val schedule by option("--cron", metavar = "expression")
		.help("Run command forever and perform sync on this schedule")
		.convert { PulseSchedule.parseCron(it) }

	private val healthCheckId by option("--hc-id", metavar = "id", envvar = "RADARR_FOLDER_FIXER_HC_ID")
		.help("ID of Healthchecks.io service to notify")

	private val healthCheckHost by option("--hc-host", metavar = "url", envvar = "RADARR_FOLDER_FIXER_HC_HOST")
		.convert { it.toHttpUrl() }
		.default("https://hc-ping.com".toHttpUrl())
		.help("Host of Healthchecks.io service to notify. Requires --hc-id")

	private val verbosity by option("--verbose", "-v")
		.counted(limit = 3)
		.help("Increase logging verbosity. -v = informational, -vv = debug, -vvv = trace")

	override suspend fun run() {
		val client = OkHttpClient.Builder()
			.addNetworkInterceptor(
				HttpLoggingInterceptor(::println).also {
					it.level = when (verbosity) {
						1 -> Level.BASIC
						2, 3 -> Level.BODY
						else -> Level.NONE
					}
				},
			)
			.apply {
				if (verbosity == 3) {
					eventListenerFactory(LoggingEventListener.Factory(::println))
				}
			}
			.build()

		val json = Json {
			ignoreUnknownKeys = true
		}

		val retrofit = Retrofit.Builder()
			.baseUrl(host)
			.client(client)
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
			.build()

		val service = retrofit.create<RadarrApi>()

		val healthCheckService = HealthCheckService(healthCheckHost, client)
		val healthCheck = healthCheckId?.let(healthCheckService::newCheck).takeUnless { dryRun }

		try {
			val schedule = schedule
			if (schedule != null) {
				println("Sync schedule: $schedule")
				val pulse = clock.schedulePulse(schedule, timeZone)
				pulse.beat(strategy = SkipNext) {
					runOnce(service, healthCheck)
				}
			} else {
				runOnce(service, healthCheck)
			}
		} finally {
			client.dispatcher.executorService.shutdown()
			client.connectionPool.evictAll()
		}
	}

	private suspend fun runOnce(
		service: RadarrApi,
		healthCheck: HealthCheck?,
	) {
		val started = healthCheck?.start()

		val tags = service.listTags(apiKey)
		suspend fun findOrCreateTag(name: String): Long {
			tags.find { it.label == tag }?.let { tag ->
				return tag.id
			}

			print("Creating tag $name…")
			val created = service.createTag(apiKey, Tag(0, tag))
			println(" ${created.id}")
			return created.id
		}

		val tagId = findOrCreateTag(tag)
		val ignoreTagId = ignoreTag?.let { findOrCreateTag(it) }

		val movies = service.listMovies(apiKey)
			.sortedBy { it.title }

		for (movie in movies) {
			val rootFolderPath = movie.rootFolderPath.toPath()
			val folderPath = movie.folderName.toPath()
			val currentFolderName = folderPath.relativeTo(rootFolderPath).toString()
			val expectedFolderName = service.getMovieFolder(apiKey, movie.id).folder
			val isMismatch = currentFolderName != expectedFolderName

			val hasTag = tagId in movie.tags
			val hasIgnoreTag = ignoreTagId?.let { it in movie.tags }

			if (isMismatch && movie.hasFile) {
				println(
					buildString {
						append(movie.title)
						append(" (id: ")
						append(movie.id)
						append(")\n  expected: ")
						append(expectedFolderName)
						append("\n   current: ")
						append(currentFolderName)
						if (hasIgnoreTag != null) {
							append("\n   ignored: ")
							append(hasIgnoreTag)
						}
						append("\n   has tag: ")
						append(hasTag)
					},
				)

				if (hasIgnoreTag == true) {
					if (hasTag) {
						if (dryRun) {
							println("DRY-RUN: Remove tag $tagId from ${movie.id}")
						} else {
							service.editMovie(
								apiKey,
								MovieEdit(
									movieIds = listOf(movie.id),
									tags = listOf(tagId),
									applyTags = "remove",
								),
							)
						}
					}
				} else if (!hasTag) {
					if (dryRun) {
						println("DRY-RUN: Add tag $tagId to ${movie.id}")
					}
					service.editMovie(
						apiKey,
						MovieEdit(
							movieIds = listOf(movie.id),
							tags = listOf(tagId),
							applyTags = "add",
						),
					)
				}
			} else if (hasTag) {
				if (dryRun) {
					println("DRY-RUN: Remove tag $tagId from ${movie.id}")
				} else {
					service.editMovie(
						apiKey,
						MovieEdit(
							movieIds = listOf(movie.id),
							tags = listOf(tagId),
							applyTags = "remove",
						),
					)
				}
			}
		}

		started?.complete()
	}
}
