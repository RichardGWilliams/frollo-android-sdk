package us.frollo.frollosdk.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import us.frollo.frollosdk.model.coredata.aggregation.providers.Provider
import us.frollo.frollosdk.model.coredata.aggregation.providers.ProviderRelation

@Dao
internal interface ProviderDao {

    @Query("SELECT * FROM provider")
    fun load(): LiveData<List<Provider>>

    @Query("SELECT * FROM provider WHERE provider_id = :providerId")
    fun load(providerId: Long): LiveData<Provider?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg models: Provider): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(model: Provider): Long

    @Query("SELECT provider_id FROM provider")
    fun getIds(): List<Long>

    @Query("SELECT provider_id FROM provider WHERE provider_id NOT IN (:apiIds) AND provider_status NOT IN ('DISABLED','UNSUPPORTED')")
    fun getStaleIds(apiIds: LongArray): List<Long>

    @Query("DELETE FROM provider WHERE provider_id IN (:providerIds)")
    fun deleteMany(providerIds: LongArray)

    @Query("DELETE FROM provider WHERE provider_id = :providerId")
    fun delete(providerId: Long)

    @Query("DELETE FROM provider")
    fun clear()

    // Relation methods

    @androidx.room.Transaction
    @Query("SELECT * FROM provider")
    fun loadWithRelation(): LiveData<List<ProviderRelation>>

    @androidx.room.Transaction
    @Query("SELECT * FROM provider WHERE provider_id = :providerId")
    fun loadWithRelation(providerId: Long): LiveData<ProviderRelation?>
}