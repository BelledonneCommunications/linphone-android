package org.linphone.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class CallHistoryDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE CallHistoryCache (
                userId TEXT PRIMARY KEY,
                version INTEGER,
                data TEXT
            )
        """
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS CallHistoryCache")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "CallHistory.db"
        private const val DATABASE_VERSION = 1
    }
}
