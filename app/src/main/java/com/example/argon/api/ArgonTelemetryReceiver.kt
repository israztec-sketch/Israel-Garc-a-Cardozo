package com.example.argon.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receptor independiente de consultas ARGÓN vía Broadcast / Intent.
 */
class ArgonTelemetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_QUERY_TELEMETRY) {
            val payload = ArgonTelemetryBus.snapshotJson()
            setResultData(payload)
            val reply = Intent(ACTION_TELEMETRY_RESPONSE).apply {
                putExtra(EXTRA_JSON_SNAPSHOT, payload)
                putExtra(EXTRA_NAV_STATUS, ArgonTelemetryBus.snapshot.value.navigationStatus)
            }
            context?.sendBroadcast(reply)
        }
    }

    companion object {
        const val ACTION_QUERY_TELEMETRY = "com.argon.truck.ACTION_QUERY_TELEMETRY"
        const val ACTION_TELEMETRY_RESPONSE = "com.argon.truck.ACTION_TELEMETRY_RESPONSE"
        const val EXTRA_JSON_SNAPSHOT = "extra_argon_json_snapshot"
        const val EXTRA_NAV_STATUS = "extra_argon_nav_status"
    }
}
