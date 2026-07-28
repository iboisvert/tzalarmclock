package imb.tzalarmclock.data.repository

import imb.tzalarmclock.data.db.AlarmDao
import imb.tzalarmclock.data.db.toDomain
import imb.tzalarmclock.data.db.toEntity
import imb.tzalarmclock.domain.model.Alarm
import imb.tzalarmclock.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [AlarmRepository] backed by Room. */
class RoomAlarmRepository(
    private val dao: AlarmDao,
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeAlarm(id: Long): Flow<Alarm?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getAlarm(id: Long): Alarm? = dao.getById(id)?.toDomain()

    override suspend fun getEnabledAlarms(): List<Alarm> =
        dao.getEnabled().map { it.toDomain() }

    override suspend fun save(alarm: Alarm): Long {
        val entity = alarm.toEntity()
        return if (alarm.id == Alarm.NO_ID) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            alarm.id
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
