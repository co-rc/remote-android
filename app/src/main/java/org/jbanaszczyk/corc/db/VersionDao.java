package org.jbanaszczyk.corc.db;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface VersionDao {

    @Query("SELECT * FROM version WHERE id = 1 LIMIT 1")
    @Nullable
    VersionEntity getSingleton();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(VersionEntity version);
}
