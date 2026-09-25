package com.coderabyss.mobile.models

import android.content.Context
import android.os.Build

/** Passive measurement of successful real work, including model loading; never a token estimate. */
object WorkflowPerformance {
    fun record(context: Context, model: String, characters: Int, elapsedMs: Long) {
        if(characters < 32 || elapsedMs < 1000) return
        context.getSharedPreferences("workflow_performance", 0).edit()
            .putString(model, "${Build.FINGERPRINT}|${System.currentTimeMillis()}|${characters * 1000.0 / elapsedMs}").apply()
    }
    fun speed(context: Context, model: String): Double? {
        val value = context.getSharedPreferences("workflow_performance", 0).getString(model, null)?.split('|')
        if(value?.size != 3 || value[0] != Build.FINGERPRINT || System.currentTimeMillis() - (value[1].toLongOrNull() ?: 0) > 30L * 86400000) return null
        return value[2].toDoubleOrNull()
    }
    fun label(context: Context, model: String): String {
        val measured = speed(context, model) ?: return "Device performance not yet measured"
        return "Last local run: %.1f characters/s including loading".format(measured)
    }
}
