package app.personal.workouttracker.quickstart

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.personal.workouttracker.MainActivity
import app.personal.workouttracker.shared.quickstart.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Installed production WebView DOM controls -> real Capacitor bridge -> paired watch. */
@RunWith(AndroidJUnit4::class)
class PairedQuickStartEntryUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var web: WebView
    private lateinit var output: File
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun js(expression: String): JsonElement {
        val reply = CompletableDeferred<String>()
        instrumentation.runOnMainSync {
            web.evaluateJavascript("JSON.stringify((() => {try {return {value:($expression)};} catch(error) {return {error:String(error.stack || error)};}})())") { reply.complete(it) }
        }
        val raw = withTimeoutOrNull(2_000) { reply.await() } ?: return JsonNull
        // Navigation can invalidate a pending evaluation while the page loads.
        if (raw == "null") return JsonNull
        val result = json.parseToJsonElement(json.parseToJsonElement(raw).jsonPrimitive.content).jsonObject
        assertNull("JavaScript failed: ${result["error"]}", result["error"])
        return result["value"] ?: JsonNull
    }
    private suspend fun asyncJs(body: String): JsonElement {
        // Reload replaces the JavaScript global object. Install read helpers for
        // each async operation, including failure cleanup after a reload.
        js("(() => { window.__entryDb = () => new Promise((resolve,reject)=>{const r=indexedDB.open('workoutAppDB');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);}); window.__entryRead = async () => {const db=await __entryDb();const stores=[...db.objectStoreNames];const values=await Promise.all(stores.map(name=>new Promise((resolve,reject)=>{const r=db.transaction(name).objectStore(name).getAll();r.onsuccess=()=>resolve([name,r.result]);r.onerror=()=>reject(r.error);})));db.close();return Object.fromEntries(values);}; return true; })()")
        js("(() => { window.__entryResult = null; (async () => { $body })().then(value => window.__entryResult = {value}, error => window.__entryResult = {error: String(error.stack || error)}); return true; })()")
        return withTimeout(20_000) {
            while (true) {
                val value = js("window.__entryResult")
                if (value != JsonNull) {
                    val record = value.jsonObject
                    assertNull(record["error"]?.toString(), record["error"])
                    return@withTimeout requireNotNull(record["value"])
                }
                delay(100)
            }
            @Suppress("UNREACHABLE_CODE") error("No JavaScript result")
        }
    }
    private suspend fun waitFor(expression: String) {
        withTimeout(25_000) { while (js(expression).jsonPrimitive.booleanOrNull != true) delay(150) }
    }
    private suspend fun click(text: String, root: String = "document") {
        waitFor("[...$root.querySelectorAll('button')].some(b => b.textContent.trim() === ${q(text)} && !b.disabled)")
        assertTrue(js("(() => { const b = [...$root.querySelectorAll('button')].find(b => b.textContent.trim() === ${q(text)} && !b.disabled); b.scrollIntoView({block:'center'}); b.click(); return true; })()").jsonPrimitive.boolean)
        delay(200)
    }
    private fun q(value: String) = Json.encodeToString(value)
    private suspend fun setInput(selector: String, value: String) {
        js("(() => { const e = document.querySelector(${q(selector)}); if (!e || e.disabled) throw Error('Missing editable input'); const prototype = e.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype; Object.getOwnPropertyDescriptor(prototype, 'value').set.call(e, ${q(value)}); e.dispatchEvent(new Event(e.tagName === 'SELECT' ? 'change' : 'input', {bubbles:true})); return true; })()")
        delay(150)
        assertEquals(value, js("document.querySelector(${q(selector)}).value").jsonPrimitive.content)
    }
    private suspend fun capture(name: String) {
        delay(500)
        File(output, "$name.json").writeText(js("({url:location.href,text:document.body.innerText,inputs:[...document.querySelectorAll('[role=dialog] input,[role=dialog] select')].map(e=>({label:e.getAttribute('aria-label'),value:e.value,disabled:e.disabled}))})").toString())
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private suspend fun sheetItems(): JsonArray = js("[...document.querySelectorAll('[role=dialog] strong')].map(s => { const card=s.parentElement.parentElement; const input=suffix=>card.querySelector('[aria-label$=\" '+suffix+'\"]'); const load=input('load').value; return {name:s.textContent.replace(/^\\d+\\.\\s*/,''),sets:Number(input('sets').value),prescription:input('target').value,restSeconds:Number(input('rest seconds').value),loadWeight:load ? Number(load) : null,loadUnit:load ? input('load unit').value : null}; })").jsonArray

    @Test fun libraryAndTodayThroughInstalledWebView() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("quickStartEntryUiPairedValidation") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val avd = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("getprop ro.boot.qemu.avd_name")
        ).bufferedReader().use { it.readText().trim() }
        assertEquals("Pasingot_Matrix_Phone", avd)
        val context = instrumentation.targetContext
        val peer = requireNotNull(args.getString("peerNodeId"))
        assertEquals(listOf(peer), Wearable.getNodeClient(context).connectedNodes.await().map { it.id })
        val store = QuickStartPhoneStore(DataStoreQuickStartPhonePersistence(context))
        val messages = Wearable.getMessageClient(context)
        val replies = Channel<String>(Channel.UNLIMITED)
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.sourceNodeId == peer && event.path == REPLY_PATH) replies.trySend(event.data.toString(Charsets.UTF_8))
        }
        messages.addListener(listener).await()
        suspend fun probe(command: String): String {
            messages.sendMessage(peer, PROBE_PATH, command.toByteArray()).await()
            return withTimeout(25_000) { replies.receive() }.also { assertFalse(it, it.startsWith("error:")) }
        }
        output = File(context.getExternalFilesDir(null), "entry-ui-acceptance/${System.currentTimeMillis()}").apply { mkdirs() }
        var scenario: ActivityScenario<MainActivity>? = null
        var originalDraft: JsonElement? = null
        var originalStores: JsonElement? = null
        val schedulePreferences = context.getSharedPreferences("schedule_cache", android.content.Context.MODE_PRIVATE)
        var originalNativeSchedule: String? = null
        var todayId: Int? = null
        var ownedRequestId: String? = null
        var primaryFailure: Throwable? = null
        val fixtureName = "Emulator entry ${UUID.randomUUID()}"
        try {
            scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
            scenario.onActivity { web = it.bridge.webView }
            waitFor("!!document.querySelector('.app-nav')")
            assertEquals("https://localhost", js("location.origin").jsonPrimitive.content)
            click("Library", "document.querySelector('.app-nav')")
            waitFor("document.querySelectorAll('.library-item').length >= 2")
            // Snapshot stores after ordinary startup reconciliation finishes.
            delay(2_000)
            js("(() => { window.__entryDb = () => new Promise((resolve,reject)=>{const r=indexedDB.open('workoutAppDB');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);}); window.__entryRead = async () => {const db=await __entryDb();const stores=[...db.objectStoreNames];const values=await Promise.all(stores.map(name=>new Promise((resolve,reject)=>{const r=db.transaction(name).objectStore(name).getAll();r.onsuccess=()=>resolve([name,r.result]);r.onerror=()=>reject(r.error);})));db.close();return Object.fromEntries(values);}; return true; })()")
            originalStores = asyncJs("return await __entryRead();")
            File(output, "before-indexeddb.json").writeText(originalStores.toString())
            originalNativeSchedule = schedulePreferences.getString("cached_schedule_json", null)
            File(output, "before-native-schedule.json").writeText(originalNativeSchedule ?: "null")
            originalDraft = originalStores.jsonObject["appState"]!!.jsonArray.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.content == "playlistDraft" } ?: JsonNull
            asyncJs("const db=await __entryDb();await new Promise((resolve,reject)=>{const tx=db.transaction('appState','readwrite');tx.objectStore('appState').put({key:'playlistDraft',schemaVersion:1,day:new Date().toLocaleDateString('en-US',{weekday:'long'}),time:'07:00',level:'beginner',items:[]});tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);});db.close();return true;")
            instrumentation.runOnMainSync { web.reload() }
            waitFor("!!document.querySelector('.app-nav')")
            click("Library", "document.querySelector('.app-nav')")
            waitFor("document.querySelectorAll('.library-item').length >= 2")
            val names = js("[...document.querySelectorAll('.library-item')].slice(0,2).map(e=>e.querySelector('p.text-sm').textContent)").jsonArray.map { it.jsonPrimitive.content }

            suspend fun verifyAndCancel(label: String, source: QuickStartSource, expected: JsonArray, rowId: Int? = null) {
                val previousIds = store.recordsForTransportRecovery().map { it.request.requestId }.toSet()
                capture("$label-confirm")
                click("Send ${expected.size} to watch", "document.querySelector('[role=dialog]')")
                val record = withTimeout(20_000) {
                    while (true) {
                        store.recordsForTransportRecovery().lastOrNull {
                            it.request.requestId !in previousIds && it.acknowledgement?.status == QuickStartStatus.READY
                        }?.let { return@withTimeout it }
                        delay(100)
                    }
                    @Suppress("UNREACHABLE_CODE") error("No Ready record")
                }
                ownedRequestId = record.request.requestId
                assertEquals(source, record.request.source)
                assertEquals(peer, record.request.targetNodeId)
                assertEquals(expected.size, record.request.exercises.size)
                expected.zip(record.request.exercises).forEach { (input, received) ->
                    val value = input.jsonObject
                    assertEquals(value["name"]!!.jsonPrimitive.content, received.exerciseName)
                    assertEquals(value["sets"]!!.jsonPrimitive.int, received.sets)
                    assertEquals(value["prescription"]!!.jsonPrimitive.content, received.prescription)
                    assertEquals(value["restSeconds"]!!.jsonPrimitive.int, received.restSeconds)
                    assertEquals(value["loadWeight"]?.takeIf { it != JsonNull }?.jsonPrimitive?.double, received.loadWeight)
                    assertEquals(value["loadUnit"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content, received.loadUnit)
                    assertEquals(rowId?.toLong(), received.sourceWorkoutRowId)
                    if (rowId != null) assertEquals(js("new Date().toLocaleDateString('en-CA')").jsonPrimitive.content, received.sourceDate)
                }
                val watchRequest = json.decodeFromString<QuickStartRequest>(probe("ready:${record.request.requestId}"))
                assertEquals(record.request, watchRequest)
                waitFor("document.querySelector('[role=dialog]').innerText.includes('Ready on watch')")
                capture("$label-ready")
                File(output, "$label-request.json").writeText(Json.encodeToString(watchRequest))
                click("Cancel request", "document.querySelector('[role=dialog]')")
                waitFor("document.querySelector('[role=dialog]').innerText.includes('Cancelled on watch')")
                assertEquals(QuickStartStatus.CANCELLED, store.current(record.request.requestId)?.acknowledgement?.status)
                assertEquals("cleared", probe("cleared:${record.request.requestId}"))
                ownedRequestId = null
                js("(() => {document.querySelector('[aria-label=\"Close Quick Start\"]').click();return true;})()")
                waitFor("!document.querySelector('[role=dialog]')")
            }

            click("Quick Start on watch", "document.querySelector('.library-item')")
            val single = sheetItems()
            assertEquals(listOf(names[0]), single.map { it.jsonObject["name"]!!.jsonPrimitive.content })
            verifyAndCancel("01-single", QuickStartSource.SINGLE, single)

            for (index in 0..1) {
                click("Add", "document.querySelectorAll('.library-item')[$index]")
            }
            click("Quick Start playlist on watch")
            val playlist = sheetItems()
            assertEquals(names, playlist.map { it.jsonObject["name"]!!.jsonPrimitive.content })
            // Distinct custom targets exercise editable confirmation and unit preservation.
            setInput("[role=dialog] [aria-label=${q(names[0] + " sets")}]", "4")
            setInput("[role=dialog] [aria-label=${q(names[0] + " target")}]", "30 sec")
            setInput("[role=dialog] [aria-label=${q(names[0] + " rest seconds")}]", "25")
            setInput("[role=dialog] [aria-label=${q(names[0] + " load")}]", "12.5")
            setInput("[role=dialog] [aria-label=${q(names[0] + " load unit")}]", "lb")
            verifyAndCancel("02-playlist", QuickStartSource.LIBRARY_PLAYLIST, sheetItems())

            for (index in 0..1) js("(() => {document.querySelectorAll('.library-item')[$index].querySelector('input[type=checkbox]').click();return true;})()")
            click("Quick Start selected (2)")
            js("(() => {document.querySelector('[role=dialog] button[aria-label='+${q(q("Move ${names[1]} up"))}+']').click();return true;})()")
            waitFor("document.querySelector('[role=dialog] strong').textContent.includes(${q(names[1])})")
            val selection = sheetItems()
            assertEquals(names.reversed(), selection.map { it.jsonObject["name"]!!.jsonPrimitive.content })
            verifyAndCancel("03-selection", QuickStartSource.LIBRARY_SELECTION, selection)

            todayId = asyncJs("const db=await __entryDb();const id=await new Promise((resolve,reject)=>{const tx=db.transaction('workouts','readwrite');const r=tx.objectStore('workouts').add({schemaVersion:1,day:new Date().toLocaleDateString('en-US',{weekday:'long'}),time:'23:57',exercise:${q(fixtureName)},exerciseSourceId:null,sets:5,reps:'45 sec',rest:17,loadWeight:7.5,loadUnit:'kg'});r.onsuccess=()=>{const id=r.result;tx.oncomplete=()=>resolve(id);};tx.onerror=()=>reject(tx.error);});db.close();return id;").jsonPrimitive.int
            instrumentation.runOnMainSync { web.reload() }
            waitFor("!!document.querySelector('.app-nav')")
            click("Today", "document.querySelector('.app-nav')")
            val todayLabel = "Quick Start $fixtureName on watch"
            waitFor("!!document.querySelector('[aria-label='+${q(q(todayLabel))}+']')")
            js("(() => {document.querySelector('[aria-label='+${q(q(todayLabel))}+']').click();return true;})()")
            val todayItems = sheetItems()
            assertEquals(1, todayItems.size)
            assertEquals(fixtureName, todayItems[0].jsonObject["name"]!!.jsonPrimitive.content)
            assertEquals(5, todayItems[0].jsonObject["sets"]!!.jsonPrimitive.int)
            assertEquals("45 sec", todayItems[0].jsonObject["prescription"]!!.jsonPrimitive.content)
            assertEquals(17, todayItems[0].jsonObject["restSeconds"]!!.jsonPrimitive.int)
            assertEquals(7.5, todayItems[0].jsonObject["loadWeight"]!!.jsonPrimitive.double, 0.0)
            assertEquals("kg", todayItems[0].jsonObject["loadUnit"]!!.jsonPrimitive.content)
            verifyAndCancel("04-today", QuickStartSource.TODAY_ROW, todayItems, todayId)
        } catch (failure: Throwable) {
            primaryFailure = failure
            File(output, "failure-stack.txt").writeText(failure.stackTraceToString())
            if (::web.isInitialized) capture("failure")
            throw failure
        } finally {
            try {
                ownedRequestId?.let { id ->
                    val local = Wearable.getNodeClient(context).localNode.await().id
                    WatchQuickStartClient(context).sendCancellation(store.prepareCancellation(id, local, System.currentTimeMillis()))
                    probe("cleared:$id")
                }
                if (originalDraft != null) {
                    asyncJs("const db=await __entryDb();await new Promise((resolve,reject)=>{const tx=db.transaction(['appState','workouts'],'readwrite');const draft=$originalDraft;if(draft===null)tx.objectStore('appState').delete('playlistDraft');else tx.objectStore('appState').put(draft);const id=${todayId ?: "null"};if(id!==null){const r=tx.objectStore('workouts').get(id);r.onsuccess=()=>{if(r.result?.exercise===${q(fixtureName)})tx.objectStore('workouts').delete(id);else tx.abort();};}tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(Error('Fixture ownership changed'));});db.close();return true;")
                    val restored = asyncJs("return await __entryRead();")
                    assertEquals("Preserve all existing IndexedDB records", originalStores, restored)
                    File(output, "after-indexeddb.json").writeText(restored.toString())
                    // Reloading Today hydrates its synthetic row into the native cache.
                    // Restore that cache through the same production bridge as bootstrap.
                    asyncJs("await window.Capacitor.Plugins.ScheduleSync.syncSchedule({rows:(await __entryRead()).workouts});return true;")
                    assertEquals(originalNativeSchedule, schedulePreferences.getString("cached_schedule_json", null))
                    File(output, "after-native-schedule.json").writeText(schedulePreferences.getString("cached_schedule_json", null) ?: "null")
                }
            } finally {
                try {
                    try { assertEquals("finished", probe("finish")) }
                    catch (finishFailure: Throwable) {
                        if (primaryFailure == null) throw finishFailure
                        primaryFailure.addSuppressed(finishFailure)
                    }
                }
                finally { scenario?.close(); messages.removeListener(listener).await(); replies.close() }
            }
        }
    }

    companion object {
        const val PROBE_PATH = "/validation/quick-start-entry/probe"
        const val REPLY_PATH = "/validation/quick-start-entry/reply"
    }
}
