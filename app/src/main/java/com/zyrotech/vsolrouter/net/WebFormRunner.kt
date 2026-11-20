package com.zyrotech.vsolrouter.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

data class WebFormResult(val ok: Boolean, val message: String)

/**
 * Some router pages (Wi-Fi basic settings, ONE-WiFi) fill their forms with a lot of
 * JavaScript before submitting (rates, band, channel, checkbox states). Rebuilding all of that
 * by hand is fragile, so these pages are driven in an off-screen WebView instead: the router's
 * own JS initialises the form, we change only the fields we need, run the page's own
 * saveChanges() validation, and submit.
 */
class WebFormRunner(context: Context) {
    private val appContext = context.applicationContext

    /**
     * Load [path], then set [fields] on form [formName] and submit it.
     * [radios] selects the radio button with the given value for each name.
     * When [strictValidation] is true, an exception in the page's saveChanges() aborts the submit.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun submit(
        baseUrl: String,
        sessionCookie: String?,
        path: String,
        formName: String,
        fields: Map<String, String> = emptyMap(),
        radios: Map<String, String> = emptyMap(),
        strictValidation: Boolean = true,
        checks: Map<String, Boolean> = emptyMap(),
        timeoutMs: Long = 30_000,
    ): WebFormResult = withContext(Dispatchers.Main) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        if (sessionCookie != null) cm.setCookie(baseUrl, "sessionid=$sessionCookie; path=/")
        cm.flush()

        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val alerts = mutableListOf<String>()
            val wv = WebView(appContext)
            var stage = 0 // 0 = loading the page, 1 = submitted, waiting for the result page
            var done = false

            fun finish(r: WebFormResult) {
                if (done) return
                done = true
                handler.removeCallbacksAndMessages(null)
                try { wv.stopLoading(); wv.destroy() } catch (_: Exception) {}
                if (cont.isActive) cont.resume(r)
            }

            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = VsolClient.USER_AGENT
            wv.webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    message?.let { alerts += it }
                    result?.confirm()
                    return true
                }
                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    message?.let { alerts += it }
                    result?.confirm()
                    return true
                }
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (done) return
                    if (stage == 0) {
                        if (url?.contains("login", ignoreCase = true) == true) {
                            finish(WebFormResult(false, "Session expired, please log in again"))
                            return
                        }
                        stage = 1
                        view.evaluateJavascript(buildScript(formName, fields, radios, strictValidation, checks)) { raw ->
                            val r = raw?.trim('"').orEmpty()
                            if (r != "submitted") {
                                stage = 2
                                val why = when {
                                    r == "invalid" -> alerts.joinToString(" ").ifBlank { "The router page rejected the values" }
                                    r == "noform" -> "Settings form not found on the router page"
                                    else -> "Router page error: $r ${alerts.joinToString(" ")}"
                                }
                                finish(WebFormResult(false, why.trim()))
                            } else {
                                // If no result page arrives, report what we know.
                                handler.postDelayed({
                                    finish(WebFormResult(false, alerts.joinToString(" ").ifBlank { "No reply from the router" }))
                                }, 15_000)
                            }
                        }
                    } else if (stage == 1) {
                        stage = 2
                        view.evaluateJavascript("(document.body?document.body.innerText:'').substring(0,400)") { raw ->
                            val text = raw?.trim('"').orEmpty()
                            if (Regex("""ERROR""").containsMatchIn(text)) {
                                finish(WebFormResult(false, text.substringAfter("ERROR").take(200).trim(':', ' ')))
                            } else {
                                finish(WebFormResult(true, "OK"))
                            }
                        }
                    }
                }
            }
            handler.postDelayed({ finish(WebFormResult(false, "Timed out talking to the router")) }, timeoutMs)
            cont.invokeOnCancellation { handler.post { finish(WebFormResult(false, "Cancelled")) } }
            wv.loadUrl(baseUrl + path)
        }
    }

    private fun buildScript(
        formName: String,
        fields: Map<String, String>,
        radios: Map<String, String>,
        strict: Boolean,
        checks: Map<String, Boolean>,
    ): String {
        val c = JSONObject(checks as Map<*, *>).toString()
        val f = JSONObject(fields as Map<*, *>).toString()
        val r = JSONObject(radios as Map<*, *>).toString()
        val q = JSONObject.quote(formName)
        return """
            (function(){try{
              var f=document.forms[$q]; if(!f) return 'noform';
              var R=$r; for (var n in R){ var els=document.getElementsByName(n);
                for (var i=0;i<els.length;i++){ if(String(els[i].value)==R[n]){ els[i].checked=true;
                  try{ if(els[i].onclick) els[i].onclick(); }catch(e){} } } }
              var C=$c; for (var n in C){ var el=f.elements[n]; if(!el) el=document.getElementsByName(n)[0];
                if(el && el.checked!=C[n]){ el.checked=C[n]; try{ if(el.onclick) el.onclick(); }catch(e){} } }
              var F=$f; for (var k in F){ var el=f.elements[k]; if(!el) el=document.getElementsByName(k)[0];
                if(el){ el.value=F[k]; } }
              var ok=true;
              if (typeof saveChanges=='function'){ try{ ok=saveChanges(); }catch(e){ if($strict) return 'jserror:'+e.message; } }
              if (ok===false) return 'invalid';
              if (!f.elements['save'] || f.elements['save'].type!='hidden'){
                var h=document.createElement('input'); h.type='hidden'; h.name='save'; h.value='Apply Changes'; f.appendChild(h); }
              f.submit(); return 'submitted';
            }catch(e){ return 'jserror:'+e.message; }})()
        """.trimIndent()
    }
}
