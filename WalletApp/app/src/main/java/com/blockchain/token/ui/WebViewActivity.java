package com.blockchain.token.ui;

import android.app.ActionBar;
import android.content.Intent;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.blockchain.token.R;

/**
 * WebView
 * @author zm
 * 
 */
public class WebViewActivity extends FragmentActivity {

	private WebView mWebView;
	private ProgressBar proBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_webview);

		proBar = findViewById(R.id.proBar);
		mWebView = findViewById(R.id.webView);
		mWebView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_bright));
		mWebView.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int newProgress) {
				if (newProgress < 100) {
					proBar.setVisibility(View.VISIBLE);
					proBar.setProgress(newProgress);
				} else {
					proBar.setProgress(100);
					proBar.postDelayed(new Runnable() {
						@Override
						public void run() {
							proBar.setVisibility(View.GONE);
						}
					}, 500);
				}
				super.onProgressChanged(view, newProgress);
			}
		});
		mWebView.setWebViewClient(new MyWebViewClient());
		WebSettings webSettings = mWebView.getSettings();
		//自适应屏幕
		webSettings.setSupportZoom(false);
		webSettings.setJavaScriptEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
		webSettings.setBuiltInZoomControls(true);
		webSettings.setUseWideViewPort(true);//这个很关键
		webSettings.setLoadWithOverviewMode(true);
		webSettings.setLayoutAlgorithm(LayoutAlgorithm.NARROW_COLUMNS);
		webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);//不使用缓存，只从网络获取数据
		webSettings.setDomStorageEnabled(true);
		webSettings.setDatabaseEnabled(true);
		webSettings.setAppCacheEnabled(true);
		webSettings.setAllowFileAccess(true);
		Intent intent = getIntent();
		String url = intent.getStringExtra("url");
		mWebView.loadUrl(url);
	}

	class MyWebViewClient extends WebViewClient {

		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler,
				SslError error) {
			handler.proceed();// 接受所有网站的证书
		}
	}

	@Override
	public void onBackPressed() {
		finish();
	}
}
