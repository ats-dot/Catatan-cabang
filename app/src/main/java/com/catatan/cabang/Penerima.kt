package com.catatan.cabang

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Menerima kiriman dari APK 1 (akun baru) dan APK 3 (update server).
class Penerima : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncEngine.init(context)
        try {
            when (intent.action) {
                Store.ACTION_AKUN -> {
                    val email = intent.getStringExtra("email") ?: ""
                    val id = intent.getStringExtra("id") ?: ""
                    val raw = intent.getStringExtra("servers")
                        ?: intent.getIntArrayExtra("servers")?.joinToString(",")
                        ?: ""
                    val servers = raw.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
                    Store.upsertFromAts(context, email, id, servers)
                }
                Store.ACTION_UPDATE -> {
                    val id = intent.getStringExtra("id") ?: ""
                    val server = intent.getStringExtra("server") ?: ""
                    val level = intent.getStringExtra("level") ?: ""
                    val note = intent.getStringExtra("keterangan") ?: ""
                    Store.upsertFromClaim(context, id, server, level, note)
                }
            }
        } catch (e: Exception) {
        }
    }
}
