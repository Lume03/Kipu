package com.kipu.app.feature.portability

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.core.okio.OkioSerializer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okio.Path.Companion.toPath
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem

class DeletionRequestPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    // Android's FileStorage uses renameTo, which cannot replace an existing
    // destination on Windows. Okio's NIO backend keeps this real disk/reopen test
    // portable; Android production keeps its standard PreferenceDataStoreFactory.
    private fun storage(file: File) = OkioStorage(FileSystem.SYSTEM, object : OkioSerializer<Preferences> {
        override val defaultValue = PreferencesFileSerializer.defaultValue
        override suspend fun readFrom(source: BufferedSource) = PreferencesFileSerializer.readFrom(source.inputStream())
        override suspend fun writeTo(t: Preferences, sink: BufferedSink) = PreferencesFileSerializer.writeTo(t, sink.outputStream())
    }, producePath = { file.absolutePath.toPath() })
    @Test fun unfinishedRequestSurvivesReopenAndCompletedSecretIsErased() = runTest {
        val file = File(temporary.root,"deletion.preferences_pb")
        val firstJob = SupervisorJob()
        val first = DataStoreDeletionRequests(PreferenceDataStoreFactory.create(storage = storage(file), scope = CoroutineScope(Dispatchers.IO + firstJob)))
        val request = DeletionRequest("owner","request-id","secret",DeletionRemote.ERROR)
        first.write(request)
        firstJob.cancel(); firstJob.join()
        val nextJob = SupervisorJob()
        try {
            val reopened = DataStoreDeletionRequests(PreferenceDataStoreFactory.create(storage = storage(file), scope = CoroutineScope(Dispatchers.IO + nextJob)))
            assertEquals(request,reopened.read("owner"))
            assertNull(reopened.read("other"))
            assertEquals(listOf(request),reopened.unfinished())
            reopened.write(request.copy(remote = DeletionRemote.COMPLETED, local = DeletionLocal.CLEARED, secret = ""))
            assertEquals("",reopened.read("owner")?.secret)
            assertTrue(reopened.unfinished().isEmpty())
        } finally { nextJob.cancel(); nextJob.join() }
    }
}
