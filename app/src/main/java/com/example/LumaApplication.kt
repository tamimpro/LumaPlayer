package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.example.data.local.LumaDatabase
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.LibraryRepository

class LumaApplication : Application(), ImageLoaderFactory {

    lateinit var database: LumaDatabase
        private set

    lateinit var libraryRepository: LibraryRepository
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = LumaDatabase.getInstance(this)
        libraryRepository = LibraryRepository(this, database.lumaDao())
        preferencesRepository = PreferencesRepository(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
