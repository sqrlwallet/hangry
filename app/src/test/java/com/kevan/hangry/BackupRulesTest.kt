package com.kevan.hangry

import com.kevan.hangry.data.backup.BackupException
import com.kevan.hangry.data.backup.BackupManifest
import com.kevan.hangry.data.backup.HangryBackupManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupRulesTest {

    @Test
    fun acceptsWhatHangryWrites() {
        listOf(
            "manifest.json",
            "database/hangry.db",
            "files/food_photos/food_1.jpg",
            "files/posture_photos/123/front.jpg",
            "files/datastore/dashboard_widgets.preferences_pb",
            "shared_prefs/hangry_fasting.xml",
            "shared_prefs/supplement_reminders.xml"
        ).forEach { assertTrue(it, HangryBackupManager.isAllowedEntry(it)) }
    }

    @Test
    fun rejectsPathTraversalAndForeignFiles() {
        listOf(
            "../../databases/hangry.db",
            "files/food_photos/../../shared_prefs/x.xml",
            "/data/data/com.kevan.hangry/databases/hangry.db",
            "database/other.db",
            "files/something_else/a.jpg",
            "files/food_photos/",
            "shared_prefs/hangry_secure_prefs.xml",
            "shared_prefs/androidx.work.util.preferences.xml",
            "shared_prefs/nested/hangry_x.xml",
            "lib/evil.so"
        ).forEach { assertFalse(it, HangryBackupManager.isAllowedEntry(it)) }
    }

    @Test
    fun neverBacksUpTheEncryptedApiKey() {
        assertFalse(HangryBackupManager.isOwnPrefsFile("hangry_secure_prefs.xml"))
        assertTrue(HangryBackupManager.isOwnPrefsFile("hangry_widget_prefs.xml"))
    }

    @Test
    fun manifestChecks() {
        val ok = BackupManifest(app = "com.kevan.hangry", format = 1, dbVersion = 31)
        HangryBackupManager.checkManifest(ok, currentDbVersion = 31)
        // Older backups are fine - Room migrates them on the next launch.
        HangryBackupManager.checkManifest(ok.copy(dbVersion = 20), currentDbVersion = 31)
        assertThrows(BackupException::class.java) { HangryBackupManager.checkManifest(ok.copy(dbVersion = 32), 31) }
        assertThrows(BackupException::class.java) { HangryBackupManager.checkManifest(ok.copy(format = 2), 31) }
        assertThrows(BackupException::class.java) { HangryBackupManager.checkManifest(ok.copy(app = "com.other"), 31) }
        assertThrows(BackupException::class.java) { HangryBackupManager.checkManifest(ok.copy(dbVersion = 1), 31) }
    }

    @Test
    fun cloudBackupExcludesEverything_deviceTransferKeepsDataButNotTheKey() {
        val res = listOf(File("src/main/res/xml"), File("app/src/main/res/xml")).first { it.exists() }
        val rules = File(res, "data_extraction_rules.xml").readText()
        val cloud = rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        listOf("root", "file", "database", "sharedpref", "external").forEach {
            assertTrue("cloud backup must exclude $it", cloud.contains("""<exclude domain="$it" path="." />"""))
        }
        assertFalse(cloud.contains("<include"))
        val transfer = rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        assertTrue(transfer.contains("hangry_secure_prefs.xml"))

        val legacy = File(res, "backup_rules.xml").readText()
        Regex("<include [^>]*>").findAll(legacy).forEach {
            assertTrue("legacy include must be device-transfer only: ${it.value}", it.value.contains("""requireFlags="deviceToDeviceTransfer""""))
        }
        assertTrue(legacy.contains("""<exclude domain="sharedpref" path="hangry_secure_prefs.xml" />"""))
    }
}
