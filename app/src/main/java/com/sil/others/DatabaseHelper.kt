package com.sil.others

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "mia.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        val query = "CREATE TABLE if not exists mytable (id INTEGER PRIMARY KEY, string_column TEXT, boolean_column BOOLEAN)"
        db?.execSQL(query)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Handle database upgrades here
    }

    companion object {
        fun insertData(helper: DatabaseHelper, stringValue: String, booleanValue: Boolean) {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("stringValue", stringValue)
                put("booleanValue", booleanValue)
            }
            db.insert("general", null, values)
            db.close()
        }
    }
}