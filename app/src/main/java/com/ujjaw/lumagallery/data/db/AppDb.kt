package com.ujjaw.lumagallery.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val uri: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "hidden_folders")
data class HiddenFolderEntity(
    @PrimaryKey val bucketId: Long,
    val name: String
)

@Entity(tableName = "hidden_items")
data class HiddenItemEntity(
    @PrimaryKey val uri: String
)

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val centroid: Long,
    val count: Int,
    val coverUri: String?
)

@Entity(tableName = "content_index")
data class ContentEntity(
    @PrimaryKey val mediaUri: String,
    val text: String,
    val labels: String
)

@Entity(tableName = "faces", indices = [Index("mediaUri"), Index("personId")])
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val personId: Long,
    val hash: Long,
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun all(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun allOnce(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(e: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun remove(uri: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE uri = :uri")
    suspend fun count(uri: String): Int
}

@Dao
interface HiddenDao {
    @Query("SELECT * FROM hidden_folders")
    fun all(): Flow<List<HiddenFolderEntity>>

    @Query("SELECT * FROM hidden_folders")
    suspend fun allOnce(): List<HiddenFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(e: HiddenFolderEntity)

    @Query("DELETE FROM hidden_folders WHERE bucketId = :bucketId")
    suspend fun unhide(bucketId: Long)
}

@Dao
interface HiddenItemDao {
    @Query("SELECT * FROM hidden_items")
    fun all(): Flow<List<HiddenItemEntity>>

    @Query("SELECT * FROM hidden_items")
    suspend fun allOnce(): List<HiddenItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hide(e: HiddenItemEntity)

    @Query("DELETE FROM hidden_items WHERE uri = :uri")
    suspend fun unhide(uri: String)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY count DESC")
    fun all(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons")
    suspend fun allOnce(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun byId(id: Long): PersonEntity?

    @Insert
    suspend fun insert(p: PersonEntity): Long

    @Update
    suspend fun update(p: PersonEntity)

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM persons")
    suspend fun deleteAll()
}

@Dao
interface ContentDao {
    @Query("SELECT * FROM content_index")
    fun all(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM content_index")
    suspend fun allOnce(): List<ContentEntity>

    @Query("SELECT COUNT(*) FROM content_index")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: ContentEntity)

    @Query("DELETE FROM content_index")
    suspend fun clear()
}

@Dao
interface FaceDao {
    @Query("SELECT * FROM faces WHERE mediaUri = :uri")
    suspend fun byMedia(uri: String): List<FaceEntity>

    @Query("SELECT * FROM faces WHERE personId = :personId")
    fun forPerson(personId: Long): Flow<List<FaceEntity>>

    @Query("SELECT * FROM faces WHERE personId = :personId")
    suspend fun forPersonOnce(personId: Long): List<FaceEntity>

    @Insert
    suspend fun insertAll(faces: List<FaceEntity>)

    @Query("SELECT * FROM faces")
    suspend fun allOnce(): List<FaceEntity>

    @Query("UPDATE faces SET personId = :to WHERE id = :faceId")
    suspend fun reassign(faceId: Long, to: Long)

    @Query("DELETE FROM faces WHERE id = :faceId")
    suspend fun deleteFace(faceId: Long)

    @Query("UPDATE faces SET personId = :to WHERE personId = :from")
    suspend fun moveFaces(from: Long, to: Long)

    @Query("DELETE FROM faces WHERE personId = :personId")
    suspend fun deleteForPerson(personId: Long)

    @Query("DELETE FROM faces")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM faces")
    suspend fun total(): Int
}

@Database(
    entities = [FavoriteEntity::class, HiddenFolderEntity::class, HiddenItemEntity::class, PersonEntity::class, FaceEntity::class, ContentEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun hiddenDao(): HiddenDao
    abstract fun hiddenItemDao(): HiddenItemDao
    abstract fun personDao(): PersonDao
    abstract fun faceDao(): FaceDao
    abstract fun contentDao(): ContentDao
}

object AppDb {
    lateinit var db: AppDatabase
        private set

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `content_index` (" +
                    "`mediaUri` TEXT NOT NULL, `text` TEXT NOT NULL, `labels` TEXT NOT NULL, " +
                    "PRIMARY KEY(`mediaUri`))"
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `hidden_items` (" +
                    "`uri` TEXT NOT NULL, PRIMARY KEY(`uri`))"
            )
        }
    }

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "luma.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
