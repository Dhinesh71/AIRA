package com.aira.app

import android.content.Context
import androidx.room.Room
import com.aira.app.automation.AiraActionExecutor
import com.aira.app.automation.AutomationValidator
import com.aira.app.automation.TaskerBridge
import com.aira.app.data.local.AiraDatabase
import com.aira.app.data.remote.AiraApi
import com.aira.app.data.repository.AiraRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.AIRA_API_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val database: AiraDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AiraDatabase::class.java,
            "aira.db",
        ).fallbackToDestructiveMigration().build()
    }

    private val automationValidator: AutomationValidator by lazy {
        AutomationValidator()
    }

    private val taskerBridge: TaskerBridge by lazy {
        TaskerBridge(appContext)
    }

    val repository: AiraRepository by lazy {
        AiraRepository(
            api = retrofit.create(AiraApi::class.java),
            commandDao = database.commandHistoryDao(),
        )
    }

    val actionExecutor: AiraActionExecutor by lazy {
        AiraActionExecutor(
            context = appContext,
            validator = automationValidator,
            taskerBridge = taskerBridge,
        )
    }
}

