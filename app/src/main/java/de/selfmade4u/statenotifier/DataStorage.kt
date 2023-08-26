package de.selfmade4u.statenotifier

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// service identifier like rolling proximity identifier, so based on time and hash of public key?

@Entity(tableName = "advertised_service")
data class AdvertisedService(
    @PrimaryKey @ColumnInfo(name = "private_key") val privateKey: String,
    @ColumnInfo(name = "name") val name: String,
)

@Dao
interface AdvertisedServiceDao {
    @Query("SELECT * FROM advertised_service")
    fun getAll(): Flow<List<AdvertisedService>>

    @Query("SELECT * FROM advertised_service WHERE private_key IN (:userIds)")
    suspend fun loadAllByIds(userIds: List<String>): List<AdvertisedService>

    @Query("SELECT * FROM advertised_service WHERE name LIKE :serviceName LIMIT 1")
    suspend fun findByName(serviceName: String): AdvertisedService

    @Insert
    suspend fun insertAll(vararg users: AdvertisedService)

    @Delete
    suspend fun delete(user: AdvertisedService)
}

@Entity(
    primaryKeys = ["advertised_service", "public_key"],
    tableName = "subscribed_user", foreignKeys = [ForeignKey(
        entity = AdvertisedService::class,
        parentColumns = arrayOf("private_key"),
        childColumns = arrayOf("advertised_service")
    )]
)
data class SubscribedUser(
    @ColumnInfo(name = "advertised_service") val advertisedService: String,
    @ColumnInfo(name = "public_key") val publicKey: String,
    @ColumnInfo(name = "name") val name: String
)

@Dao
interface SubscribedUserDao {
    @Query("SELECT * FROM subscribed_user")
    fun getAll(): Flow<List<SubscribedUser>>

    @Query("SELECT * FROM subscribed_user WHERE advertised_service = :advertisedService AND public_key IN (:userPublicKeys)")
    suspend fun loadAllByIds(
        advertisedService: String,
        userPublicKeys: List<String>
    ): List<SubscribedUser>

    @Query("SELECT * FROM subscribed_user WHERE advertised_service = :advertisedService AND name LIKE :name LIMIT 1")
    suspend fun findByName(advertisedService: String, name: String): SubscribedUser

    @Insert
    suspend fun insertAll(vararg users: SubscribedUser)

    @Delete
    suspend fun delete(user: SubscribedUser)
}

@Database(entities = [AdvertisedService::class, SubscribedUser::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun advertisedServiceDao(): AdvertisedServiceDao
    abstract fun subscribedUserDao(): SubscribedUserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
