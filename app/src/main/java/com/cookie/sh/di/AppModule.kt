package com.cookie.sh.di

import android.content.Context
import androidx.room.Room
import com.cookie.sh.core.shell.RuntimeShellExecutor
import com.cookie.sh.core.shell.ShellExecutor
import com.cookie.sh.data.local.CommandHistoryDao
import com.cookie.sh.data.local.CookieShDatabase
import com.cookie.sh.data.local.FavoritePropDao
import com.cookie.sh.data.local.SavedLogDao
import com.cookie.sh.data.repository.BootRepository
import com.cookie.sh.data.repository.DeviceRepository
import com.cookie.sh.data.repository.LogcatRepository
import com.cookie.sh.data.repository.NetworkToolsRepository
import com.cookie.sh.data.repository.PackageRepository
import com.cookie.sh.data.repository.PartitionRepository
import com.cookie.sh.data.repository.PowerRepository
import com.cookie.sh.data.repository.PropsRepository
import com.cookie.sh.data.repository.RuntimeBootRepository
import com.cookie.sh.data.repository.RuntimeDeviceRepository
import com.cookie.sh.data.repository.RuntimeLogcatRepository
import com.cookie.sh.data.repository.RuntimeNetworkToolsRepository
import com.cookie.sh.data.repository.RuntimePackageRepository
import com.cookie.sh.data.repository.RuntimePartitionRepository
import com.cookie.sh.data.repository.RuntimePowerRepository
import com.cookie.sh.data.repository.RuntimePropsRepository
import com.cookie.sh.data.repository.RuntimeShellRepository
import com.cookie.sh.data.repository.RuntimeSystemRepository
import com.cookie.sh.data.repository.ShellRepository
import com.cookie.sh.data.repository.SystemRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideShellExecutor(): ShellExecutor = RuntimeShellExecutor()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CookieShDatabase {
        return Room.databaseBuilder(
            context,
            CookieShDatabase::class.java,
            "cookiesh.db",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideCommandHistoryDao(database: CookieShDatabase): CommandHistoryDao = database.commandHistoryDao()

    @Provides
    fun provideFavoritePropDao(database: CookieShDatabase): FavoritePropDao = database.favoritePropDao()

    @Provides
    fun provideSavedLogDao(database: CookieShDatabase): SavedLogDao = database.savedLogDao()

    @Provides
    @Singleton
    fun provideDeviceRepository(impl: RuntimeDeviceRepository): DeviceRepository = impl

    @Provides
    @Singleton
    fun provideBootRepository(impl: RuntimeBootRepository): BootRepository = impl

    @Provides
    @Singleton
    fun providePropsRepository(impl: RuntimePropsRepository): PropsRepository = impl

    @Provides
    @Singleton
    fun provideShellRepository(impl: RuntimeShellRepository): ShellRepository = impl

    @Provides
    @Singleton
    fun provideLogcatRepository(impl: RuntimeLogcatRepository): LogcatRepository = impl

    @Provides
    @Singleton
    fun providePackageRepository(impl: RuntimePackageRepository): PackageRepository = impl

    @Provides
    @Singleton
    fun providePartitionRepository(impl: RuntimePartitionRepository): PartitionRepository = impl

    @Provides
    @Singleton
    fun provideNetworkToolsRepository(impl: RuntimeNetworkToolsRepository): NetworkToolsRepository = impl

    @Provides
    @Singleton
    fun provideSystemRepository(impl: RuntimeSystemRepository): SystemRepository = impl

    @Provides
    @Singleton
    fun providePowerRepository(impl: RuntimePowerRepository): PowerRepository = impl
}
