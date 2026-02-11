package dev.kuylar.sakura.di.modules

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kuylar.sakura.markdown.MarkdownHandler

@Module
@InstallIn(SingletonComponent::class)
object MarkdownModule {
	@Provides
	fun provideMarkdownHandler() = MarkdownHandler()
}