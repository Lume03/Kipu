package com.kipu.app.feature.categories.data

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.kipu.app.core.database.MIGRATION_37_38
import com.kipu.app.feature.categories.domain.model.InitialCategoryCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CategoryCatalogMigrationTest {
    @Test fun `upgrade repairs only known branches and preserves associations flags and presentation`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(
            RuntimeEnvironment.getApplication()).name(null).callback(object : SupportSQLiteOpenHelper.Callback(37) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE categories(id TEXT PRIMARY KEY,user_id TEXT,parent_id TEXT,origin TEXT,category_type TEXT,is_active INTEGER)")
                db.execSQL("CREATE TABLE financial_movements(id TEXT PRIMARY KEY,category_id TEXT)")
                db.execSQL("CREATE TABLE category_presentations(category_id TEXT,name TEXT,icon TEXT,color TEXT)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.use {
            val db = it.writableDatabase
            InitialCategoryCatalog.allEntries.forEach { entry ->
                db.execSQL("INSERT INTO categories VALUES(?,NULL,?,'SYSTEM','GENERAL',0)", arrayOf(entry.id, entry.parentId))
            }
            val root = InitialCategoryCatalog.entries.first().id
            db.execSQL("INSERT INTO categories VALUES('custom','owner',NULL,'CUSTOM','GENERAL',1)")
            db.execSQL("INSERT INTO categories VALUES('income','owner',NULL,'CUSTOM','INCOME',1)")
            db.execSQL("INSERT INTO categories VALUES('custom-child','owner',?,'CUSTOM','GENERAL',1)", arrayOf(root))
            db.execSQL("INSERT INTO financial_movements VALUES('historic',?)", arrayOf(root))
            db.execSQL("INSERT INTO category_presentations VALUES(?,'Mi comida','star','#123456')", arrayOf(root))
            MIGRATION_37_38.migrate(db)
            fun scalar(query: String) = db.query(query).use { c -> c.moveToFirst(); c.getString(0) }
            assertEquals("52", scalar("SELECT count(*) FROM categories WHERE category_type='EXPENSE'"))
            assertEquals("GENERAL", scalar("SELECT category_type FROM categories WHERE id='custom'"))
            assertEquals("INCOME", scalar("SELECT category_type FROM categories WHERE id='income'"))
            assertEquals("51", scalar("SELECT count(*) FROM categories WHERE is_active=0"))
            assertEquals(root, scalar("SELECT category_id FROM financial_movements WHERE id='historic'"))
            assertEquals("Mi comida|star|#123456", scalar("SELECT name||'|'||icon||'|'||color FROM category_presentations"))
        }
    }
}
