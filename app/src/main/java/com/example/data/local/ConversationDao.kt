package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ConversationTurn
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_turns ORDER BY timestamp DESC")
    fun getAllTurns(): Flow<List<ConversationTurn>>

    @Query("SELECT * FROM conversation_turns ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTurns(limit: Int = 20): Flow<List<ConversationTurn>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: ConversationTurn): Long

    @Query("DELETE FROM conversation_turns")
    suspend fun clearAllTurns()

    @Query("SELECT COUNT(*) FROM conversation_turns")
    fun getTurnCount(): Flow<Int>
}
