package com.meshalive.app

import android.app.Application
import com.meshalive.app.data.MeshDatabase
import com.meshalive.app.data.SettingsRepository
import com.meshalive.app.data.StatsStore
import com.meshalive.app.mesh.DeviceStatus
import com.meshalive.app.mesh.MeshManager
import com.meshalive.app.mesh.Notifier

/** Simple manual dependency container. */
class MeshAliveApp : Application() {
    val db: MeshDatabase by lazy { MeshDatabase.create(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val device: DeviceStatus by lazy { DeviceStatus(this) }
    val notifier: Notifier by lazy { Notifier(this) }
    val stats: StatsStore by lazy { StatsStore(this) }
    val mesh: MeshManager by lazy { MeshManager(this, db, settings, device, notifier, stats) }
}
