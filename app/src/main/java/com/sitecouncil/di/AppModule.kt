package com.sitecouncil.di

import android.content.Context
import androidx.room.Room
import com.sitecouncil.data.AnalysisDao
import com.sitecouncil.data.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Bewusst ohne Logging-Interceptor: Header mit dem API-Key landen so nie im Log.
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "sitecouncil.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDao(db: AppDatabase): AnalysisDao = db.analysisDao()
}
