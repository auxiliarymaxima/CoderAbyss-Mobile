package com.coderabyss.mobile.platformui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.coderabyss.mobile.projects.ProjectRepository
import com.coderabyss.mobile.tasks.TaskManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    val projects = ProjectRepository(application)
    val tasks = TaskManager(application)
    fun observeProject(id: String) = flow { while (true) { emit(projects.read(id)); delay(500) } }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    fun observeProjects() = flow { while (true) { emit(projects.all()); delay(1000) } }.flowOn(kotlinx.coroutines.Dispatchers.IO)
}
