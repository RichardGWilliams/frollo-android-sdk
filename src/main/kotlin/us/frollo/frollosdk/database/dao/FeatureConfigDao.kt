package us.frollo.frollosdk.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.reactivex.Observable
import us.frollo.frollosdk.model.coredata.appconfiguration.FeatureConfig

@Dao
internal interface FeatureConfigDao {

    @Query("SELECT * FROM feature_config")
    fun load(): LiveData<List<FeatureConfig>>

    @RawQuery(observedEntities = [FeatureConfig::class])
    fun loadByQuery(queryStr: SupportSQLiteQuery): LiveData<List<FeatureConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(model: FeatureConfig): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg models: FeatureConfig): LongArray

    @Query("DELETE FROM feature_config WHERE `key` = :key")
    fun delete(key: String)

    @Query("DELETE FROM feature_config")
    fun clear()

    /**
     * RxJava Return Types
     */

    @Query("SELECT * FROM feature_config")
    fun loadRx(): Observable<List<FeatureConfig>>

    @RawQuery(observedEntities = [FeatureConfig::class])
    fun loadByQueryRx(queryStr: SupportSQLiteQuery): Observable<List<FeatureConfig>>
}
