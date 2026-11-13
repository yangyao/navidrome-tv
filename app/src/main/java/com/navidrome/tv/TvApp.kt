package com.navidrome.tv

import android.app.Application
import com.navidrome.tv.data.local.ConnectionSettings
import com.navidrome.tv.data.api.SubsonicClient
import com.navidrome.tv.data.local.SettingsStore
import com.navidrome.tv.data.repository.MusicRepository
import com.navidrome.tv.util.L10n

class TvApp : Application(), coil.ImageLoaderFactory {
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizeBytes(16 * 1024 * 1024).build() }
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("artwork"))
            .maxSizeBytes(64L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .build()

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: MusicRepository
        private set
    lateinit var client: SubsonicClient
        private set

    override fun onCreate() {
        super.onCreate()
        L10n.init(this)
        settings = SettingsStore(this)
        client = SubsonicClient { settings.connection() }
        repository = MusicRepository(client)
    }

    fun connection(): ConnectionSettings? = settings.connection()
}
