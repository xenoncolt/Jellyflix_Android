package dev.xenoncolt.jellyflix.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import dev.xenoncolt.jellyflix.R

class WebViewFragment : Fragment() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_web_view, container, false)
        webView = view.findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true // Auto adjust the content to fit the screen
        webView.settings.useWideViewPort = true // Enable normal viewport
        webView.webViewClient = WebViewClient()
        val url = arguments?.getString("url")
        webView.loadUrl(url ?: "")
        return view
    }
}