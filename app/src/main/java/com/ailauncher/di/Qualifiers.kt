package com.ailauncher.di

import javax.inject.Qualifier

/** Process-lifetime coroutine scope (SupervisorJob + Dispatchers.Default). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
