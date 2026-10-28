package dev.openmacro.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MacroEntity::class,
        TriggerEntity::class,
        ActionEntity::class,
        ConstraintEntity::class,
        GlobalVariableEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(SpecConverters::class)
abstract class OpenMacroDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun globalVariableDao(): GlobalVariableDao

    companion object {
        /** Adds per-macro local variables and the global variable table (keeps saved macros). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE macros ADD COLUMN localVariables TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS global_variables " +
                        "(name TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, value TEXT NOT NULL)",
                )
            }
        }

        fun build(context: Context): OpenMacroDatabase =
            Room.databaseBuilder(context.applicationContext, OpenMacroDatabase::class.java, "openmacro.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
