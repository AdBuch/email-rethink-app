package ch.emailrethink.android

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * Zeigt Mail-HTML in mehreren unabhängigen Schichten abgesichert an:
 * 1. JavaScript ist abgeschaltet.
 * 2. Die WebView darf nichts aus dem Netz, aus Dateien oder Content-Providern laden.
 * 3. Jede Ressourcenanfrage wird zusätzlich mit einer leeren Antwort abgefangen.
 * 4. Eine Content-Security-Policy im Dokument verbietet alles ausser eingebettetem CSS und data:-Bildern.
 * 5. Links werden nicht geöffnet (v0.1).
 */
@SuppressLint("SetJavaScriptEnabled") // wird ausdrücklich auf false gesetzt
@Composable
fun SafeHtmlView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                settings.loadsImagesAutomatically = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                settings.setGeolocationEnabled(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.saveFormData = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val scheme = request.url.scheme.orEmpty().lowercase()
                        if (scheme == "data" || scheme == "about") return null
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
            }
        },
        update = { webView ->
            val document = SafeHtml.wrap(html)
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

object SafeHtml {
    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; style-src 'unsafe-inline'; img-src data:; form-action 'none'; base-uri 'none'"

    /** Stellt dem Mail-HTML eine restriktive Content-Security-Policy voran. */
    fun wrap(html: String): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"$CONTENT_SECURITY_POLICY\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "</head><body>$html</body></html>"
}
