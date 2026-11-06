package com.adshield

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {
    lateinit var web: WebView
    @Volatile private var pageHost: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        lifecycleScope.launch(Dispatchers.IO) { Engine.rebuild(this@MainActivity) }
        web = buildWeb()
        web.loadUrl("https://duckduckgo.com")
        setContent { AdShieldTheme { App(this) } }
    }

    override fun onPause() { super.onPause(); web.onPause(); Store.saveStats() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }

    fun go(input: String) {
        val s = input.trim()
        if (s.isEmpty()) return
        val u = when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            !s.contains(' ') && s.contains('.') -> "https://$s"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(s)
        }
        web.loadUrl(u)
    }

    fun rebuildRules() { lifecycleScope.launch(Dispatchers.IO) { Engine.rebuild(this@MainActivity) } }

    fun updateLists() {
        if (Store.updating.value) return
        Store.updating.value = true
        Store.message.value = null
        lifecycleScope.launch(Dispatchers.IO) {
            Engine.update(this@MainActivity)
            Store.updating.value = false
        }
    }

    fun clearBrowsingData() {
        web.clearCache(true)
        web.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        Store.message.value = "Cache and cookies cleared"
    }

    fun applyCookiePolicy() {
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, !(Store.filterOn.value && Store.blockCookies.value))
    }

    private fun script(): String {
        val css = "ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id*='div-gpt-ad'],[class*='ad-slot']," +
            "[class*='ad-banner'],[id*='ad-banner'],[class*='advert'],[id*='advert'],[data-ad-slot]," +
            "[aria-label='Advertisement'],iframe[src*='doubleclick'],iframe[src*='googlesyndication']," +
            "iframe[src*='adservice']{display:none!important}"
        val df = Store.distractionFree.value
        return """
(function(){
 if(window.__adshield)return; window.__adshield=1;
 try{var s=document.createElement('style'); s.textContent="$css"; (document.head||document.documentElement).appendChild(s);}catch(e){}
 if($df){
  var re=/(^|[-_ ])(ad|ads|advert|promo|popup|newsletter|subscribe|cookie|consent|floating|float|sticky-video)([-_ ]|${'$'})/i;
  var sweep=function(){
   var els=document.querySelectorAll('div,aside,section,iframe');
   for(var i=0;i<els.length;i++){
    var e=els[i]; if(e.__as)continue;
    var cs=getComputedStyle(e);
    if(cs.position==='fixed'||cs.position==='sticky'){
     var c=(typeof e.className==='string')?e.className:'';
     if(re.test((e.id||'')+' '+c)){e.style.setProperty('display','none','important'); e.__as=1;}
    }
   }
  };
  sweep(); var t;
  new MutationObserver(function(){clearTimeout(t); t=setTimeout(sweep,600);}).observe(document.documentElement,{childList:true,subtree:true});
 }
})();
"""
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWeb(): WebView {
        val w = WebView(this)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            safeBrowsingEnabled = true
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val r = request ?: return null
                if (!Store.filterOn.value) return null
                val ph = pageHost
                if (Engine.isWhitelisted(ph)) return null
                val kind = Engine.check(r.url, ph) ?: return null
                Store.hit(kind)
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val r = request ?: return false
                val u = r.url
                val scheme = u.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") {
                    if ((scheme == "mailto" || scheme == "tel") && r.hasGesture()) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                    }
                    return true
                }
                if (Store.filterOn.value && !Engine.isWhitelisted(pageHost) && r.isForMainFrame) {
                    if (Engine.check(u, null) != null) { Store.hit(2); return true }
                    val h = u.host?.lowercase()
                    if (Store.distractionFree.value && !r.hasGesture() && !r.isRedirect && pageHost != null && h != pageHost) {
                        Store.hit(2); return true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageHost = url?.let { Uri.parse(it).host?.lowercase() }
                Store.currentUrl.value = url ?: ""
                applyCookiePolicy()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (Store.filterOn.value && !Engine.isWhitelisted(pageHost)) view?.evaluateJavascript(script(), null)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                if (url != null) Store.currentUrl.value = url
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) { Store.progress.value = newProgress }
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                Store.hit(2) // pop-up / pop-under window blocked
                return false
            }
        }
        return w
    }
}
