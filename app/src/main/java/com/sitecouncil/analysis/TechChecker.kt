package com.sitecouncil.analysis

import com.sitecouncil.domain.TechCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Führt die Netzwerkabrufe aus und übergibt die Daten an [TechRules]. */
@Singleton
class TechChecker @Inject constructor(
    http: OkHttpClient,
) {
    private val client: OkHttpClient = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private data class Fetched(
        val code: Int,
        val body: String,
        val bytes: Int,
        val contentType: String,
        val location: String?,
        val xRobots: String?,
        val ms: Long,
    )

    suspend fun run(url: String, renderedTextLength: Int, renderMs: Long): List<TechCheck> =
        withContext(Dispatchers.IO) {
            val checks = mutableListOf<TechCheck>()

            // Weiterleitungen von Hand verfolgen, damit die Kette sichtbar wird.
            val hops = mutableListOf<TechRules.Hop>()
            var current: HttpUrl? = url.toHttpUrlOrNull()
            var page: Fetched? = null
            var guard = 0
            while (current != null && guard < 10) {
                coroutineContext.ensureActive()
                val fetched = fetch(current)
                hops += TechRules.Hop(fetched?.code ?: 0, current.toString())
                page = fetched
                val location = fetched?.location
                current = if (fetched != null && fetched.code in 300..399 && location != null) {
                    current.resolve(location)
                } else {
                    null
                }
                guard++
            }

            val finalUrl = hops.lastOrNull()?.url ?: url
            val finalHttpUrl = finalUrl.toHttpUrlOrNull()
            val code = page?.code ?: 0
            val rawHtml = page?.body.orEmpty()

            checks += TechRules.status(code)
            checks += TechRules.redirects(hops)
            checks += TechRules.https(finalUrl)

            var robotsTxt: String? = null
            var sitemapFound = false
            var llmsFound = false
            if (finalHttpUrl != null) {
                coroutineContext.ensureActive()
                val robots = fetch(finalHttpUrl.resolve("/robots.txt"))
                if (robots != null && robots.code == 200 && !looksLikeHtml(robots)) robotsTxt = robots.body
                coroutineContext.ensureActive()
                val sitemap = fetch(finalHttpUrl.resolve("/sitemap.xml"))
                sitemapFound = sitemap != null && sitemap.code == 200 &&
                    (sitemap.body.contains("<urlset") || sitemap.body.contains("<sitemapindex"))
                coroutineContext.ensureActive()
                val llms = fetch(finalHttpUrl.resolve("/llms.txt"))
                llmsFound = llms != null && llms.code == 200 && llms.body.isNotBlank() && !looksLikeHtml(llms)
            }
            val path = finalHttpUrl?.encodedPath ?: "/"
            checks += TechRules.robots(robotsTxt, path)
            checks += TechRules.sitemap(sitemapFound, robotsTxt?.let { RobotsParser.sitemaps(it) }.orEmpty())
            checks += TechRules.llmsTxt(llmsFound)

            if (rawHtml.isNotBlank()) {
                val doc = Jsoup.parse(rawHtml, finalUrl)
                checks += TechRules.title(doc)
                checks += TechRules.description(doc)
                checks += TechRules.canonical(doc, finalUrl)
                checks += TechRules.hreflang(doc)
                checks += TechRules.openGraph(doc)
                checks += TechRules.robotsMeta(doc, page?.xRobots)
                checks += TechRules.headings(doc)
                checks += TechRules.jsonLd(doc)
                checks += TechRules.imageAlt(doc)
                checks += TechRules.linkTexts(doc)
                checks += TechRules.renderDiff(doc.body().text().length, renderedTextLength)
            }
            checks += TechRules.loadTime(page?.ms ?: 0, renderMs)
            checks += TechRules.pageSize(page?.bytes ?: 0)
            checks
        }

    private fun looksLikeHtml(f: Fetched): Boolean =
        f.contentType.contains("html", ignoreCase = true) ||
            f.body.trimStart().startsWith("<!doctype", ignoreCase = true) ||
            f.body.trimStart().startsWith("<html", ignoreCase = true)

    private fun fetch(url: HttpUrl?): Fetched? {
        if (url == null) return null
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,text/plain,application/xml;q=0.9,*/*;q=0.8")
            .build()
        return try {
            val started = System.nanoTime()
            client.newCall(request).execute().use { response ->
                val ms = (System.nanoTime() - started) / 1_000_000
                val source = response.body?.source()
                val bytes = if (source != null) {
                    source.request(MAX_BODY_BYTES)
                    val size = minOf(source.buffer.size, MAX_BODY_BYTES)
                    source.buffer.readByteArray(size)
                } else {
                    ByteArray(0)
                }
                val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                Fetched(
                    code = response.code,
                    body = String(bytes, charset),
                    bytes = bytes.size,
                    contentType = response.header("Content-Type").orEmpty(),
                    location = response.header("Location"),
                    xRobots = response.header("X-Robots-Tag"),
                    ms = ms,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_BODY_BYTES = 3L * 1024 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (compatible; SiteCouncil/0.1; Website-Analyse)"
    }
}
