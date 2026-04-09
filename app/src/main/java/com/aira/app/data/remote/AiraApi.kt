package com.aira.app.data.remote

import com.aira.app.data.model.InterpretRequest
import com.aira.app.data.model.InterpretResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AiraApi {
    @POST("interpret")
    suspend fun interpret(
        @Body request: InterpretRequest,
    ): InterpretResponse
}

