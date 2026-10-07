package app.personal.workouttracker.wear.quickstart

import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only native inventory; OK is not proof of connectivity or app acceptance. */
@RunWith(AndroidJUnit4::class)
class PairingReadinessTest {
    @Test fun recordNativeReadiness() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("pairingReadinessValidation") == "true")
        assertTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val name = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
            .executeShellCommand("getprop ro.boot.qemu.avd_name")).bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Timed_UI", name)
        val context = instrumentation.targetContext
        assertEquals("app.personal.workouttracker", context.packageName)
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = requireNotNull(info.signingInfo).apkContentsSigners.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }
        var local: String? = null
        var peers = emptyList<com.google.android.gms.wearable.Node>()
        val errors = mutableListOf<String>()
        try { local = withTimeout(15_000) { Wearable.getNodeClient(context).localNode.await().id } }
        catch (error: Exception) { errors += "local: ${error.javaClass.simpleName}: ${error.message}".take(500) }
        try { peers = withTimeout(15_000) { Wearable.getNodeClient(context).connectedNodes.await() } }
        catch (error: Exception) { errors += "peers: ${error.javaClass.simpleName}: ${error.message}".take(500) }
        val record = buildJsonObject {
            put("schemaVersion", 1); put("role", "wear"); put("avdName", name)
            put("apiLevel", Build.VERSION.SDK_INT); put("packageName", context.packageName)
            put("observedAtMillis", System.currentTimeMillis())
            put("signatureSha256", JsonArray(signatures.map(::JsonPrimitive)))
            put("localNodeId", local?.let(::JsonPrimitive) ?: JsonNull)
            put("apiAvailable", errors.isEmpty())
            put("connectedPeers", JsonArray(peers.map { node -> buildJsonObject {
                put("id", node.id); put("displayName", node.displayName); put("nearby", node.isNearby)
            } }))
            put("errors", JsonArray(errors.map(::JsonPrimitive)))
        }
        val folder = File(context.getExternalFilesDir(null), "pairing-readiness").apply { mkdirs() }
        File(folder, "latest.json").writeText(record.toString())
        println(record)
    }
}
