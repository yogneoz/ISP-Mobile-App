package com.example.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.data.local.PulseHrDao
import com.example.data.local.PulseHrDatabase
import com.example.data.remote.AuthHeaderInterceptor
import com.example.data.remote.EnterpriseGatewayInterceptor
import com.example.data.remote.EnterprisePinningConfig
import com.example.data.remote.PulseHrApiService
import com.example.data.remote.TokenRefreshAuthenticator
import com.example.data.repository.AttendanceRepositoryImpl
import com.example.data.repository.AuthRepositoryImpl
import com.example.data.repository.DocumentRepositoryImpl
import com.example.data.repository.EmployeeRepositoryImpl
import com.example.data.repository.LeaveRepositoryImpl
import com.example.data.repository.PayrollRepositoryImpl
import com.example.data.repository.RouteTrackingRepositoryImpl
import com.example.data.repository.WorkplaceRepositoryImpl
import com.example.domain.repository.AttendanceRepository
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.DocumentRepository
import com.example.domain.repository.EmployeeRepository
import com.example.domain.repository.LeaveRepository
import com.example.domain.repository.PayrollRepository
import com.example.domain.repository.RouteTrackingRepository
import com.example.domain.repository.WorkplaceRepository
import com.example.scanner.DocumentTextAnalyzer
import com.example.scanner.MlKitDocumentTextAnalyzer
import com.example.security.BiometricAuthenticationManager
import com.example.security.BiometricPromptInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): PulseHrDatabase {
    return Room.databaseBuilder(context, PulseHrDatabase::class.java, "pulsehr_enterprise.db")
      .fallbackToDestructiveMigration(dropAllTables = true)
      .build()
  }

  @Provides
  fun providePulseHrDao(db: PulseHrDatabase): PulseHrDao = db.pulseHrDao()

  @Provides
  @Singleton
  fun provideMoshi(): Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  @Provides
  @Singleton
  fun provideOkHttpClient(
    authHeaderInterceptor: AuthHeaderInterceptor,
    tokenRefreshAuthenticator: TokenRefreshAuthenticator,
    enterpriseGatewayInterceptor: EnterpriseGatewayInterceptor,
  ): OkHttpClient {
    val logging =
      HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
      }

    return OkHttpClient.Builder()
      .certificatePinner(EnterprisePinningConfig.certificatePinner)
      .addInterceptor(authHeaderInterceptor)
      .addInterceptor(logging)
      .addInterceptor(enterpriseGatewayInterceptor)
      .authenticator(tokenRefreshAuthenticator)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .build()
  }

  @Provides
  @Singleton
  fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
    return Retrofit.Builder()
      .baseUrl(EnterprisePinningConfig.SANDBOX_BASE_URL)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }

  @Provides
  @Singleton
  fun providePulseHrApiService(retrofit: Retrofit): PulseHrApiService =
    retrofit.create(PulseHrApiService::class.java)

  @Provides
  @Singleton
  fun provideFusedLocationProviderClient(
    @ApplicationContext context: Context
  ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

  @Provides
  @Singleton
  fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
    WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindsModule {

  @Binds
  @Singleton
  abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

  @Binds
  @Singleton
  abstract fun bindEmployeeRepository(impl: EmployeeRepositoryImpl): EmployeeRepository

  @Binds
  @Singleton
  abstract fun bindAttendanceRepository(impl: AttendanceRepositoryImpl): AttendanceRepository

  @Binds
  @Singleton
  abstract fun bindLeaveRepository(impl: LeaveRepositoryImpl): LeaveRepository

  @Binds
  @Singleton
  abstract fun bindPayrollRepository(impl: PayrollRepositoryImpl): PayrollRepository

  @Binds
  @Singleton
  abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

  @Binds
  @Singleton
  abstract fun bindWorkplaceRepository(impl: WorkplaceRepositoryImpl): WorkplaceRepository

  @Binds
  @Singleton
  abstract fun bindRouteTrackingRepository(
    impl: RouteTrackingRepositoryImpl
  ): RouteTrackingRepository

  @Binds
  @Singleton
  abstract fun bindBiometricPromptInterface(
    impl: BiometricAuthenticationManager
  ): BiometricPromptInterface

  @Binds
  @Singleton
  abstract fun bindDocumentTextAnalyzer(
    impl: MlKitDocumentTextAnalyzer
  ): DocumentTextAnalyzer
}
