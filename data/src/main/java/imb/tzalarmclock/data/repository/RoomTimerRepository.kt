package imb.tzalarmclock.data.repository

import imb.tzalarmclock.data.db.TimerDao
import imb.tzalarmclock.data.db.toDomain
import imb.tzalarmclock.data.db.toEntity
import imb.tzalarmclock.domain.model.Timer
import imb.tzalarmclock.domain.repository.TimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [TimerRepository] backed by Room. */
class RoomTimerRepository(
    private val dao: TimerDao,
) : TimerRepository {

    override fun observeTimers(): Flow<List<Timer>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeTimer(id: Long): Flow<Timer?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getTimer(id: Long): Timer? = dao.getById(id)?.toDomain()

    override suspend fun getRunningTimers(): List<Timer> = dao.getRunning().map { it.toDomain() }

    override suspend fun save(timer: Timer): Long {
        val entity = timer.toEntity()
        return if (timer.id == Timer.NO_ID) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            timer.id
        }
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
