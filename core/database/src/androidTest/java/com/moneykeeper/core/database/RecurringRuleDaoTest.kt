package com.moneykeeper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moneykeeper.core.database.dao.RecurringRuleDao
import com.moneykeeper.core.database.entity.RecurringRuleEntity
import com.moneykeeper.core.domain.model.Frequency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RecurringRuleDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecurringRuleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recurringRuleDao()
    }

    @After
    fun tearDown() = db.close()

    private fun rule(
        frequency: Frequency = Frequency.MONTHLY,
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        endDate: LocalDate? = null,
        lastGeneratedDate: LocalDate? = null,
        interval: Int = 1,
    ) = RecurringRuleEntity(
        frequency = frequency, interval = interval,
        startDate = startDate, endDate = endDate,
        lastGeneratedDate = lastGeneratedDate,
    )

    @Test
    fun upsert_and_getById_returnsRule() = runTest {
        val id = dao.upsert(rule())
        val result = dao.getById(id)
        assertNotNull(result)
        assertEquals(Frequency.MONTHLY, result!!.frequency)
        assertEquals(LocalDate.of(2026, 1, 1), result.startDate)
    }

    @Test
    fun getAllActive_excludesRulesWithPastEndDate() = runTest {
        dao.upsert(rule(endDate = LocalDate.of(2026, 4, 19))) // expired yesterday
        dao.upsert(rule(endDate = LocalDate.of(2026, 4, 20))) // ends today → included
        dao.upsert(rule(endDate = null))                       // open-ended → included

        val active = dao.getAllActive("2026-04-20")
        assertEquals(2, active.size)
        assertTrue(active.none { it.endDate == LocalDate.of(2026, 4, 19) })
    }

    @Test
    fun getAllActive_includesOpenEndedRules() = runTest {
        dao.upsert(rule(endDate = null))
        val active = dao.getAllActive("2026-04-20")
        assertEquals(1, active.size)
        assertNull(active[0].endDate)
    }

    @Test
    fun getAllActive_includesRulesEndingExactlyToday() = runTest {
        dao.upsert(rule(endDate = LocalDate.of(2026, 4, 20)))
        val active = dao.getAllActive("2026-04-20")
        assertEquals(1, active.size)
    }

    @Test
    fun getAllActive_excludesAllExpiredWhenAllHavePastEndDate() = runTest {
        dao.upsert(rule(endDate = LocalDate.of(2026, 1, 1)))
        dao.upsert(rule(endDate = LocalDate.of(2026, 2, 1)))
        val active = dao.getAllActive("2026-04-20")
        assertTrue(active.isEmpty())
    }

    @Test
    fun updateLastGeneratedDate_persistsNewDate() = runTest {
        val id = dao.upsert(rule(lastGeneratedDate = null))
        dao.updateLastGeneratedDate(id, "2026-04-20")
        val updated = dao.getById(id)
        assertEquals(LocalDate.of(2026, 4, 20), updated!!.lastGeneratedDate)
    }

    @Test
    fun updateLastGeneratedDate_overwritesPreviousDate() = runTest {
        val id = dao.upsert(rule(lastGeneratedDate = LocalDate.of(2026, 3, 31)))
        dao.updateLastGeneratedDate(id, "2026-04-20")
        val updated = dao.getById(id)
        assertEquals(LocalDate.of(2026, 4, 20), updated!!.lastGeneratedDate)
    }

    @Test
    fun delete_removesRule() = runTest {
        val id = dao.upsert(rule())
        val entity = dao.getById(id)!!
        dao.delete(entity)
        assertNull(dao.getById(id))
    }

    @Test
    fun observeAll_reflectsInsertedRules() = runTest {
        dao.upsert(rule(frequency = Frequency.DAILY))
        dao.upsert(rule(frequency = Frequency.WEEKLY))
        dao.upsert(rule(frequency = Frequency.MONTHLY))

        val all = dao.observeAll().first()
        assertEquals(3, all.size)
    }

    @Test
    fun interval_persistedCorrectly() = runTest {
        val id = dao.upsert(rule(frequency = Frequency.MONTHLY, interval = 3))
        assertEquals(3, dao.getById(id)!!.interval)
    }
}
