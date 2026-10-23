package dev.tether.core.data

import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Manual diagnostic: hits the real YouTube endpoints. Run explicitly with
 * `gradlew :core:data:testDebugUnitTest --tests "dev.tether.core.data.YouTubeLiveTest"`.
 * Ignored by default so normal unit-test runs stay offline and deterministic.
 */
@org.junit.Ignore("live network test; run manually")
class YouTubeLiveTest {

    @Test
    fun searchAndResolve() = runBlocking {
        val provider = YouTubeProvider()
        try {
            val result = provider.search("lofi hip hop", limit = 5)
            println("TRACKS=${result.tracks.size} RELEASES=${result.releases.size} NOTE=${result.note}")
            result.tracks.forEach { println("  - ${it.title} | ${it.artist} | art=${it.artworkUri}") }
            val first = result.tracks.firstOrNull()
            if (first != null) {
                val stream = provider.resolveStream(first)
                println("STREAM=${stream?.uri?.let { it.take(80) + "..." } ?: "null"} mime=${stream?.mimeType}")
            }
            val sections = provider.browse()
            println("SECTIONS=${sections.size} ${sections.joinToString { it.title + ":" + it.releases.size }}")
            // Direct kiosk diagnostics
            val service = org.schabi.newpipe.extractor.ServiceList.YouTube
            service.kioskList.availableKiosks.forEach { id ->
                runCatching {
                    val k = service.kioskList.getExtractorById(id, null)
                    k.fetchPage()
                    println("KIOSK $id -> ${k.initialPage.items.size} items")
                }.onFailure { println("KIOSK $id FAILED: ${it.javaClass.simpleName}: ${it.message?.take(200)}") }
            }
        } catch (t: Throwable) {
            println("FAILED: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
            throw t
        }
    }
}
