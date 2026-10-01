package com.saeid.aidirectorcamera.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ProjectRepository(context: Context) {
    private val db = AppDatabase.get(context)
    val projects: Flow<List<ProjectEntity>> = db.projectDao().observeProjects()

    suspend fun createProject(name: String): Long = db.projectDao().insert(ProjectEntity(name = name.trim()))

    suspend fun saveShot(shot: ShotEntity): Long = db.shotDao().insert(shot)

    fun shots(projectId: Long): Flow<List<ShotEntity>> = db.shotDao().observeShots(projectId)
}
