package com.sitecouncil.analysis

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.Base64
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sitecouncil.domain.AnalysisError
import com.sitecouncil.domain.Box
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lädt eine URL in einer unsichtbaren WebView, liest Text, HTML und
 * Elementpositionen per JavaScript aus und zeichnet die Seite in ein Bitmap.
 */
@Singleton
class PageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Serializable
    private data class JsElement(
        val tag: String = "",
        val type: String = "",
        val text: String = "",
        val x: Double = 0.0,
        val y: Double = 0.0,
        val w: Double = 0.0,
        val h: Double = 0.0,
        val fontSize: Double = 0.0,
        val color: String = "",
        val bg: String = "",
        val labeled: Boolean? = null,
    )

    @Serializable
    private data class JsDump(
        val innerWidth: Double = 0.0,
        val scrollHeight: Double = 0.0,
        val title: String = "",
        val lang: String = "",
        val text: String = "",
        val html: String = "",
        val elements: List<JsElement> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(url: String, desktop: Boolean): PageSnapshot = withContext(Dispatchers.Main) {
        val density = context.resources.displayMetrics.density
        val viewW = if (desktop) {
            min((DESKTOP_CSS_WIDTH * density).roundToInt(), MAX_DESKTOP_VIEW_PX)
        } else {
            (MOBILE_CSS_WIDTH * density).roundToInt()
        }
        val viewH = if (desktop) (viewW * 0.625f).roundToInt() else (viewW * 2f).roundToInt()
        val outW = if (desktop) DESKTOP_OUT_WIDTH else MOBILE_OUT_WIDTH

        val webView = WebView(context)
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = if (desktop) DESKTOP_UA else MOBILE_UA
            }
            webView.setBackgroundColor(Color.WHITE)
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(viewW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(viewH, View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, viewW, viewH)

            val started = SystemClock.elapsedRealtime()
            awaitPage(webView, url)
            val loadMs = SystemClock.elapsedRealtime() - started

            // Zeit für nachladende Skripte, dann einmal durchscrollen, damit Lazy Loading anspringt.
            delay(SETTLE_MS)
            evaluate(webView, SCROLL_JS)
            delay(SCROLL_MS)
            webView.scrollTo(0, 0)

            val raw = evaluate(webView, EXTRACT_JS)
            val dump = try {
                val inner = json.decodeFromString(String.serializer(), raw)
                json.decodeFromString(JsDump.serializer(), inner)
            } catch (e: Exception) {
                throw AnalysisError.PageUnreachable("Seiteninhalt konnte nicht ausgelesen werden")
            }

            val cssWidth = if (dump.innerWidth > 0) dump.innerWidth else viewW / density.toDouble()
            val outPerCss = outW / cssWidth
            val docOutH = (dump.scrollHeight * outPerCss).roundToInt().coerceAtLeast(1)
            val maxOutH = MAX_SEGMENTS * SEGMENT_HEIGHT
            val outH = min(docOutH, maxOutH)
            val capturedCssH = outH / outPerCss

            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val scale = outW / viewW.toFloat()
            canvas.scale(scale, scale)
            webView.draw(canvas)

            val (segments, path) = withContext(Dispatchers.IO) { encode(bitmap) }
            bitmap.recycle()

            val elements = dump.elements.mapNotNull { e ->
                if (e.y >= capturedCssH || e.w <= 0 || e.h <= 0) return@mapNotNull null
                PageElement(
                    tag = e.tag,
                    type = e.type,
                    text = e.text,
                    box = Box(e.x / cssWidth, e.y / capturedCssH, e.w / cssWidth, e.h / capturedCssH).clamped(),
                    fontSizePx = e.fontSize,
                    color = e.color,
                    background = e.bg,
                    labeled = e.labeled,
                )
            }

            PageSnapshot(
                url = webView.url ?: url,
                title = dump.title,
                lang = dump.lang,
                renderedHtml = dump.html,
                visibleText = dump.text,
                elements = elements,
                segments = segments,
                screenshotPath = path,
                screenshotWidth = outW,
                screenshotHeight = outH,
                truncated = docOutH > maxOutH,
                desktop = desktop,
                loadMs = loadMs,
            )
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private suspend fun awaitPage(webView: WebView, url: String) {
        val finished = CompletableDeferred<Unit>()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                finished.complete(Unit)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    finished.completeExceptionally(
                        AnalysisError.PageUnreachable(error.description?.toString() ?: "Ladefehler")
                    )
                }
            }
        }
        webView.loadUrl(url)
        val done = withTimeoutOrNull(LOAD_TIMEOUT_MS) { finished.await() }
        if (done == null && webView.progress < 50) {
            throw AnalysisError.PageUnreachable("Zeitüberschreitung beim Laden")
        }
    }

    private suspend fun evaluate(webView: WebView, script: String): String =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { value ->
                if (cont.isActive) cont.resume(value ?: "null")
            }
        }

    /** Zerlegt den Screenshot in Segmente für die API und speichert ihn komplett für die Heatmap. */
    private fun encode(bitmap: Bitmap): Pair<List<String>, String> {
        val segments = mutableListOf<String>()
        var y = 0
        while (y < bitmap.height) {
            val h = min(SEGMENT_HEIGHT, bitmap.height - y)
            val part = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, h)
            val bytes = ByteArrayOutputStream()
            part.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)
            if (part !== bitmap) part.recycle()
            segments += Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            y += h
        }
        val dir = File(context.filesDir, "screens").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return segments to file.absolutePath
    }

    companion object {
        const val MAX_SEGMENTS = 5
        const val SEGMENT_HEIGHT = 1568
        private const val MOBILE_CSS_WIDTH = 412
        private const val DESKTOP_CSS_WIDTH = 1280
        private const val MAX_DESKTOP_VIEW_PX = 2560
        private const val MOBILE_OUT_WIDTH = 618
        private const val DESKTOP_OUT_WIDTH = 1024
        private const val JPEG_QUALITY = 80
        private const val LOAD_TIMEOUT_MS = 25_000L
        private const val SETTLE_MS = 1_500L
        private const val SCROLL_MS = 1_200L

        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"

        private const val SCROLL_JS = """
(function(){
  var h=Math.max(document.documentElement.scrollHeight,document.body?document.body.scrollHeight:0);
  var y=0;
  var t=setInterval(function(){
    y+=600;window.scrollTo(0,y);
    if(y>=h||y>12000){clearInterval(t);window.scrollTo(0,0);}
  },40);
  return 1;
})()
"""

        private const val EXTRACT_JS = """
(function(){
  function vis(el){
    var r=el.getBoundingClientRect();
    if(r.width<2||r.height<2)return null;
    var s=getComputedStyle(el);
    if(s.visibility==='hidden'||s.display==='none'||parseFloat(s.opacity)===0)return null;
    return {r:r,s:s};
  }
  var out=[];
  var nodes=document.querySelectorAll('h1,h2,h3,h4,h5,h6,button,a[href],input,select,textarea,[role=button]');
  for(var i=0;i<nodes.length&&out.length<400;i++){
    var el=nodes[i];
    var tag=el.tagName.toLowerCase();
    var type=el.getAttribute('type')||'';
    if(tag==='input'&&type==='hidden')continue;
    var v=vis(el);
    if(!v)continue;
    var text=(el.innerText||el.value||el.getAttribute('aria-label')||el.getAttribute('placeholder')||el.getAttribute('title')||'');
    text=String(text).replace(/\s+/g,' ').trim().slice(0,80);
    var labeled=null;
    if(tag==='input'||tag==='select'||tag==='textarea'){
      labeled=!!((el.labels&&el.labels.length)||el.getAttribute('aria-label')||el.getAttribute('aria-labelledby')||el.getAttribute('title'));
    }
    out.push({tag:tag,type:type,text:text,
      x:v.r.left+window.scrollX,y:v.r.top+window.scrollY,w:v.r.width,h:v.r.height,
      fontSize:parseFloat(v.s.fontSize)||0,color:v.s.color,bg:v.s.backgroundColor,labeled:labeled});
  }
  var de=document.documentElement;var b=document.body;
  return JSON.stringify({
    innerWidth:window.innerWidth||de.clientWidth||0,
    scrollHeight:Math.max(de.scrollHeight,b?b.scrollHeight:0),
    title:document.title||'',
    lang:de.getAttribute('lang')||'',
    text:(b?b.innerText:'').slice(0,60000),
    html:de.outerHTML.slice(0,1500000),
    elements:out});
})()
"""
    }
}
