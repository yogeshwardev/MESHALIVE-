package com.meshalive.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE msgId = :id")
    suspend fun get(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE msgId = :id AND status != 'Delivered'")
    suspend fun setStatus(id: String, status: String)

    @Query("UPDATE messages SET status = 'Delivered', hopCount = :hops, path = :path, latencyMs = :latency WHERE msgId = :id")
    suspend fun markDelivered(id: String, hops: Int, path: String, latency: Long)

    @Query("UPDATE messages SET status = 'Failed' WHERE outgoing = 1 AND status IN ('Relaying') AND timestamp < :before")
    suspend fun failStale(before: Long)

    @Query("UPDATE messages SET rescueStatus = :status, rescueNote = :note WHERE msgId = :id")
    suspend fun setRescue(id: String, status: String, note: String)

    @Query("SELECT * FROM messages WHERE type = 'ALERT' ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestAlert(): Flow<MessageEntity?>

    @Query("DELETE FROM messages")
    suspend fun clear()
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending_packets ORDER BY priority ASC, enqueuedAt ASC")
    suspend fun all(): List<PendingPacketEntity>

    @Query("SELECT COUNT(*) FROM pending_packets")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PendingPacketEntity)

    @Query("DELETE FROM pending_packets WHERE msgId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_packets WHERE enqueuedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM pending_packets")
    suspend fun clear()
}

@Dao
interface InboxDao {
    @Query("SELECT * FROM gateway_inbox ORDER BY urgency DESC, receivedAt DESC")
    fun observeAll(): Flow<List<InboxEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: InboxEntity): Long

    @Query("UPDATE gateway_inbox SET webhookStatus = :status, cloudPushed = :pushed WHERE msgId = :id")
    suspend fun setCloudStatus(id: String, status: String, pushed: Boolean)

    @Query("SELECT * FROM gateway_inbox WHERE cloudPushed = 0 ORDER BY receivedAt ASC")
    suspend fun notPushed(): List<InboxEntity>

    @Query("SELECT * FROM gateway_inbox WHERE msgId = :id")
    suspend fun get(id: String): InboxEntity?

    @Query("UPDATE gateway_inbox SET rescueStatus = :status WHERE msgId = :id")
    suspend fun setRescue(id: String, status: String)

    @Query("DELETE FROM gateway_inbox")
    suspend fun clear()
}

@Database(
    entities = [MessageEntity::class, PendingPacketEntity::class, InboxEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun pending(): PendingDao
    abstract fun inbox(): InboxDao

    companion object {
        fun create(context: Context): MeshDatabase =
            Room.databaseBuilder(context, MeshDatabase::class.java, "meshalive.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
