package com.coderabyss.mobile.tasks

class TaskCancelReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        intent.getStringExtra("taskId")?.let { id -> runCatching { TaskManager(context).cancel(id) } }
    }
    companion object {
        fun intent(context: android.content.Context, id: String): android.app.PendingIntent = android.app.PendingIntent.getBroadcast(context, id.hashCode(),
            android.content.Intent(context, TaskCancelReceiver::class.java).putExtra("taskId", id), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
