package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var pendingPermissionRequest: PermissionRequest? = null

    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingPermissionRequest?.let { request ->
                    request.grant(request.resources)
                }
            } else {
                pendingPermissionRequest?.deny()
            }
            pendingPermissionRequest = null
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
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }

            webViewClient = object : WebViewClient() {
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
            }

            loadUrl("file:///android_asset/index.html")
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
}

