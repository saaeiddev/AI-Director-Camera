package com.saeid.aidirectorcamera.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long
}

@Dao
interface ShotDao {
    @Query("SELECT * FROM shots WHERE projectId = :projectId ORDER BY recordedAt DESC")
    fun observeShots(projectId: Long): Flow<List<ShotEntity>>

    @Insert
    suspend fun insert(shot: ShotEntity): Long
}
