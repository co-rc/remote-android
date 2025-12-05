package org.jbanaszczyk.corc.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "version")
public class VersionEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private Integer id = 1; // single-row table

    @ColumnInfo(name = "db_version")
    private int dbVersion;

    public VersionEntity(int dbVersion) {
        this.dbVersion = dbVersion;
    }

    @NonNull
    public Integer getId() {
        return id;
    }

    public int getDbVersion() {
        return dbVersion;
    }

    public void setDbVersion(int dbVersion) {
        this.dbVersion = dbVersion;
    }

    // Setter required by Room for private field; keeps singleton semantics
    public void setId(@NonNull Integer ignored) {
        this.id = 1;
    }
}
