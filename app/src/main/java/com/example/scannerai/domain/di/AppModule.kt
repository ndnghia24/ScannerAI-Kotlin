package com.example.scannerai.domain.di

import com.example.scannerai.data.ml.classification.TextAnalyzer
import com.example.scannerai.domain.ml.ObjectDetector
import com.example.scannerai.domain.use_cases.AnalyzeImage
import com.example.scannerai.domain.use_cases.HitTest
import com.example.scannerai.domain.use_cases.*
import com.example.scannerai.domain.utils.GetDestinationDesc
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideObjectDetector(): ObjectDetector {
        return TextAnalyzer()
    }

    @Provides
    @Singleton
    fun provideImageAnalyzer(objectDetector: ObjectDetector): AnalyzeImage {
        return AnalyzeImage(objectDetector)
    }

    @Provides
    @Singleton
    fun provideHitTest(): HitTest {
        return HitTest()
    }

    @Provides
    @Singleton
    fun provideDestinationDest(): GetDestinationDesc {
        return GetDestinationDesc()
    }
}