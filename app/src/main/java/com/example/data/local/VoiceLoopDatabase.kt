package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ConversationTurn
import com.example.data.model.DiagnosticLog

@Database(
    entities = [ConversationTurn::class, DiagnosticLog::class],
    version = 1,
    exportSchema = false
)
abstract class VoiceLoopDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun diagnosticLogDao(): DiagnosticLogDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceLoopDatabase? = null

        fun getInstance(context: Context): VoiceLoopDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceLoopDatabase::class.java,
                    "voiceloop_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
