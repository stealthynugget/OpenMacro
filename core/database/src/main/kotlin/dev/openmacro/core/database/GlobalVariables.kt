package dev.openmacro.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import dev.openmacro.core.model.VarType
import dev.openmacro.core.model.Variable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "global_variables")
data class GlobalVariableEntity(
    @PrimaryKey val name: String,
    val type: VarType,
    val value: String,
)

@Dao
interface GlobalVariableDao {
    @Query("SELECT * FROM global_variables ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<GlobalVariableEntity>>

    @Query("SELECT * FROM global_variables")
    suspend fun getAll(): List<GlobalVariableEntity>

    @Query("SELECT * FROM global_variables WHERE name = :name")
    suspend fun get(name: String): GlobalVariableEntity?

    @Upsert
    suspend fun upsert(variable: GlobalVariableEntity)

    @Query("DELETE FROM global_variables WHERE name = :name")
    suspend fun delete(name: String)
}

fun GlobalVariableEntity.toDomain(): Variable = Variable(name, type, value)

fun Variable.toGlobalEntity(): GlobalVariableEntity = GlobalVariableEntity(name, type, value)

/** Domain access to the user's global variables. */
class GlobalVariableRepository(private val dao: GlobalVariableDao) {
    fun observeAll(): Flow<List<Variable>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    suspend fun getAll(): List<Variable> = dao.getAll().map { it.toDomain() }
    suspend fun get(name: String): Variable? = dao.get(name)?.toDomain()
    suspend fun put(variable: Variable) = dao.upsert(variable.toGlobalEntity())
    suspend fun delete(name: String) = dao.delete(name)
}
