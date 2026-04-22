// data/api/ApiService.kt
package com.kinetic.fitness.data.api

import com.kinetic.fitness.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth.php?action=login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthData>>

    @POST("auth.php?action=register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthData>>

    @POST("auth.php?action=logout")
    suspend fun logout(): Response<ApiResponse<Nothing>>

    @GET("dashboard.php")
    suspend fun getDashboard(): Response<ApiResponse<DashboardData>>

    @GET("exercises.php")
    suspend fun getExercises(
        @Query("muscle") muscle: String? = null,
        @Query("equip") equip: String? = null,
        @Query("level") level: String? = null,
        @Query("q") query: String? = null
    ): Response<ApiResponse<List<Exercise>>>

    @GET("exercises.php")
    suspend fun getExerciseById(@Query("id") id: Int): Response<ApiResponse<Exercise>>

    @GET("sessions.php")
    suspend fun getSessions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<WorkoutSession>>>

    @GET("sessions.php")
    suspend fun getSessionDetail(@Query("id") id: Int): Response<ApiResponse<WorkoutSession>>

    @POST("sessions.php?action=start")
    suspend fun startSession(@Body request: StartSessionRequest): Response<ApiResponse<WorkoutSession>>

    @POST("sessions.php?action=finish")
    suspend fun finishSession(@Body request: FinishSessionRequest): Response<ApiResponse<Nothing>>

    @POST("sessions.php?action=add_set")
    suspend fun addSet(@Body request: AddSetRequest): Response<ApiResponse<AddSetResult>>

    @POST("sessions.php?action=complete_set")
    suspend fun completeSet(@Body request: CompleteSetRequest): Response<ApiResponse<CompleteSetResult>>
}
