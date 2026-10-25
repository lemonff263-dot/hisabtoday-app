package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingPermissionRequest: PermissionRequest? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingPermissionRequest?.let { request ->
                    request.grant(request.resources)
                }
            } else {
                pendingPermissionRequest?.deny()
                Toast.makeText(this, "ক্যামেরা পারমিশন ছাড়া স্ক্যানার কাজ করবে না", Toast.LENGTH_SHORT).show()
            }
            pendingPermissionRequest = null
        }

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (filePathCallback != null) {
                val dataUri = result.data?.data
                val clipData = result.data?.clipData
                val results: Array<Uri>? = when {
                    clipData != null -> {
                        Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    }
                    dataUri != null -> arrayOf(dataUri)
                    else -> null
                }
                filePathCallback?.onReceiveValue(results)
                filePathCallback = null
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure edge-to-edge content is controlled and insets are delivered cleanly
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Dark green status bar -> white status bar icons (time, battery, wifi)
        windowInsetsController.isAppearanceLightStatusBars = false
        // White bottom navigation bar -> dark navigation icons (back, home, recents)
        windowInsetsController.isAppearanceLightNavigationBars = true

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.parseColor("#065F46")
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.parseColor("#FFFFFF")
        window.decorView.setBackgroundColor(Color.parseColor("#065F46"))

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#065F46"))
        }

        val topInsetView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            )
            setBackgroundColor(Color.parseColor("#065F46"))
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                // Secure webview settings: no raw file URL access
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }

            addJavascriptInterface(AppBridge(this@MainActivity, this), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url ?: return null
                    val path = url.path ?: ""
                    if (path.endsWith(".woff2")) {
                        try {
                            val assetPath = path.removePrefix("/assets/")
                            val inputStream = assets.open(assetPath)
                            val headers = mapOf(
                                "Access-Control-Allow-Origin" to "*",
                                "Content-Type" to "font/woff2"
                            )
                            return WebResourceResponse("font/woff2", "binary", 200, "OK", headers, inputStream)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error serving font woff2: $path", e)
                        }
                    } else if (path.endsWith(".ttf")) {
                        try {
                            val assetPath = path.removePrefix("/assets/")
                            val inputStream = assets.open(assetPath)
                            val headers = mapOf(
                                "Access-Control-Allow-Origin" to "*",
                                "Content-Type" to "font/ttf"
                            )
                            return WebResourceResponse("font/ttf", "binary", 200, "OK", headers, inputStream)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error serving font ttf: $path", e)
                        }
                    }
                    return assetLoader.shouldInterceptRequest(url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("smsto:") ||
                        url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.startsWith("geo:")
                    ) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "অ্যাপটি খুলতে ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                            return true
                        }
                    }
                    return false
                }

                override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                    // Prevent process crash from bringing down the entire activity
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val requestedResources = request.resources
                    for (resource in requestedResources) {
                        if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                request.grant(request.resources)
                            } else {
                                pendingPermissionRequest = request
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            return
                        }
                    }
                    request.grant(requestedResources)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }

                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (e: Exception) {
                        this@MainActivity.filePathCallback = null
                        return false
                    }
                    return true
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("হিসাব বার্তা")
                        .setMessage(message ?: "")
                        .setPositiveButton("ঠিক আছে") { _, _ -> result?.confirm() }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("অনুমতি নিশ্চিত করুন")
                        .setMessage(message ?: "")
                        .setPositiveButton("হ্যাঁ") { _, _ -> result?.confirm() }
                        .setNegativeButton("না") { _, _ -> result?.cancel() }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }

                override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                    val input = EditText(this@MainActivity).apply {
                        setText(defaultValue ?: "")
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(message ?: "ইনপুট দিন")
                        .setView(input)
                        .setPositiveButton("ঠিক আছে") { _, _ -> result?.confirm(input.text.toString()) }
                        .setNegativeButton("বাতিল") { _, _ -> result?.cancel() }
                        .setOnCancelListener { result?.cancel() }
                        .show()
                    return true
                }
            }

            // Load via safe modern WebViewAssetLoader origin
            loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }

        val bottomInsetView = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            )
            setBackgroundColor(Color.parseColor("#FFFFFF"))
        }

        rootLayout.addView(topInsetView)
        rootLayout.addView(webView)
        rootLayout.addView(bottomInsetView)

        setContentView(rootLayout)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            val imeInsets = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val lpTop = topInsetView.layoutParams as LinearLayout.LayoutParams
            if (lpTop.height != statusBarInsets.top) {
                lpTop.height = statusBarInsets.top
                topInsetView.layoutParams = lpTop
            }

            val desiredBottom = if (imeInsets.bottom > 0) 0 else navBarInsets.bottom
            val lpBottom = bottomInsetView.layoutParams as LinearLayout.LayoutParams
            if (lpBottom.height != desiredBottom) {
                lpBottom.height = desiredBottom
                bottomInsetView.layoutParams = lpBottom
            }

            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            rootLayout.setPadding(systemBars.left, 0, systemBars.right, 0)

            insets
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    class AppBridge(private val activity: MainActivity, private val webView: WebView) {

        @JavascriptInterface
        fun downloadFile(base64Data: String, fileName: String, mimeType: String) {
            activity.runOnUiThread {
                try {
                    val bytes = try {
                        Base64.decode(base64Data, Base64.DEFAULT)
                    } catch (e: Exception) {
                        base64Data.toByteArray(Charsets.UTF_8)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = activity.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { out ->
                                out.write(bytes)
                            }
                            Toast.makeText(activity, "Downloads ফোল্ডারে সেভ হয়েছে: $fileName", Toast.LENGTH_LONG).show()
                        } else {
                            saveFallbackFile(bytes, fileName)
                        }
                    } else {
                        saveFallbackFile(bytes, fileName)
                    }
                } catch (e: Exception) {
                    Toast.makeText(activity, "ডাউনলোড ব্যর্থ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun saveFallbackFile(bytes: ByteArray, fileName: String) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            Toast.makeText(activity, "Downloads ফোল্ডারে সেভ হয়েছে: $fileName", Toast.LENGTH_LONG).show()
        }

        @JavascriptInterface
        fun shareFile(base64Data: String, fileName: String, mimeType: String) {
            activity.runOnUiThread {
                try {
                    val bytes = try {
                        Base64.decode(base64Data, Base64.DEFAULT)
                    } catch (e: Exception) {
                        base64Data.toByteArray(Charsets.UTF_8)
                    }
                    val shareDir = File(activity.cacheDir, "shared_files").apply { mkdirs() }
                    val file = File(shareDir, fileName)
                    FileOutputStream(file).use { it.write(bytes) }

                    val uri = FileProvider.getUriForFile(
                        activity,
                        "${activity.packageName}.fileprovider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(Intent.createChooser(intent, "ফাইল শেয়ার করুন"))
                } catch (e: Exception) {
                    Toast.makeText(activity, "শেয়ার ব্যর্থ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun shareText(text: String, title: String) {
            activity.runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    activity.startActivity(Intent.createChooser(intent, title.ifEmpty { "শেয়ার করুন" }))
                } catch (e: Exception) {
                    Toast.makeText(activity, "শেয়ার করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun sendSms(phoneNumber: String, message: String) {
            activity.runOnUiThread {
                try {
                    val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
                    val uri = if (cleanPhone.isNotEmpty()) {
                        Uri.parse("smsto:$cleanPhone")
                    } else {
                        Uri.parse("smsto:")
                    }
                    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                        putExtra("sms_body", message)
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(activity, "এসএমএস অ্যাপ খুলতে ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun printDocument(htmlContent: String, jobTitle: String) {
            activity.runOnUiThread {
                try {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager == null) {
                        Toast.makeText(activity, "প্রিন্ট সেবা পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    val printWebView = WebView(activity)
                    printWebView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val printAdapter = printWebView.createPrintDocumentAdapter(jobTitle.ifEmpty { "হিসাব রশিদ" })
                            printManager.print(
                                jobTitle.ifEmpty { "হিসাব রশিদ" },
                                printAdapter,
                                PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .build()
                            )
                        }
                    }
                    printWebView.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/", htmlContent, "text/html", "utf-8", null)
                } catch (e: Exception) {
                    Toast.makeText(activity, "প্রিন্ট করতে সমস্যা হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun openBarcodeScanner() {
            activity.runOnUiThread {
                webView.evaluateJavascript("if (window.__openAppScanner) window.__openAppScanner('pos');", null)
            }
        }
    }
}
