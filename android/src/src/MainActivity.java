package systems.obsidian.focus;

import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.view.Window;
import android.view.WindowManager;
import android.os.SystemClock;
import android.util.Log;
import android.os.Handler;
import android.os.Process;

import java.util.Timer;
import java.util.TimerTask;

import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.util.Log;
import android.webkit.ValueCallback;
import android.net.Uri;

public class MainActivity extends Activity {
    private JSaddleShim jsaddle;
    private ValueCallback<Uri[]> fileUploadCallback;

    static {
        System.loadLibrary("@APPNAME@");
    }

    private static final String TAG = "JSADDLE";
    private static final int REQUEST_CODE_FILE_PICKER = 51426;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        CookieManager.setAcceptFileSchemeCookies(true);
        super.onCreate(savedInstanceState);
        // Remove title and notification bars, obv.
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);
        // find the web view
        final WebView wv = (WebView) findViewById (R.id.webview);
        // enable javascript and debugging
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccessFromFileURLs(true); //Maybe you don't need this rule
        ws.setAllowUniversalAccessFromFileURLs(true);
        wv.setWebContentsDebuggingEnabled(true);
        // init an object mediating the interaction with JSaddle
        final Handler hnd = new Handler();
        jsaddle = new JSaddleShim(wv, hnd);
        // create and set a web view client aware of the JSaddle
        wv.setWebViewClient(new JSaddleWebViewClient(jsaddle));
        // create and set a web chrome client for console message handling
        wv.setWebChromeClient(new JSaddleWebChromeClient());
        // register jsaddle javascript interface
        wv.addJavascriptInterface(jsaddle, "jsaddle");
        // tell C about the shim so that it can spin up Haskell and connect the two
        Log.d(TAG, "###jsaddle");
        jsaddle.init();
        Log.d(TAG, "###loadhtml");
        wv.loadUrl("file:///android_asset/index.html");
    }
    @Override
    public void onPause() {
        Log.d(TAG, "!!!PAUSE");
        super.onPause();
    }
    @Override
    public void onStop() {
        Log.d(TAG, "!!!STOP");
        CookieManager.getInstance().flush();
        super.onStop();
    }
    @Override
    public void onDestroy() {
        Log.d(TAG, "!!!DESTROY");
        // jsaddle.deinit(); crashes because we're not deinit'ing native threads correctly
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid()); //TODO: Properly handle the process surviving between invocations which means that the Haskell RTS needs to not be initialized twice.
    }

    // File uploads don't work out of the box.
    // You have to start an 'Intent' from 'onShowFileChooser', and handle the result here.
    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_FILE_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                if (intent != null) {
                    if (fileUploadCallback != null) {
                        Uri[] dataUris = null;
                        
                        try {
                            if (intent.getDataString() != null) {
                                dataUris = new Uri[] { Uri.parse(intent.getDataString()) };
                            }
                            else {
				if (intent.getClipData() != null) {
				    final int numSelectedFiles = intent.getClipData().getItemCount();
                                        
				    dataUris = new Uri[numSelectedFiles];
                                        
				    for (int i = 0; i < numSelectedFiles; i++) {
					dataUris[i] = intent.getClipData().getItemAt(i).getUri();
				    }
				}
                            }
                        }
                        catch (Exception ignored) { }
                        
                        fileUploadCallback.onReceiveValue(dataUris);
                        fileUploadCallback = null;
                    }
                }
            }
            else if (fileUploadCallback != null) {
		fileUploadCallback.onReceiveValue(null);
		fileUploadCallback = null;
            }
        }
    }

    private class JSaddleWebChromeClient extends WebChromeClient {
	@Override
	public boolean onConsoleMessage(ConsoleMessage cm) {
	    Log.d("JSADDLEJS", String.format("%s @ %d: %s", cm.message(), cm.lineNumber(), cm.sourceId()));
	    return true;
	}

	// file upload callback (Android 5.0 (API level 21) -- current)
	@Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
	    final boolean allowMultiple = fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;

	    if (fileUploadCallback != null) {
		fileUploadCallback.onReceiveValue(null);
	    }
	    fileUploadCallback = filePathCallback;

	    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
	    i.addCategory(Intent.CATEGORY_OPENABLE);
	
	    if (allowMultiple) {
		i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
	    }

	    i.setType("*/*");

	    startActivityForResult(Intent.createChooser(i, "Choose a File"), REQUEST_CODE_FILE_PICKER);

	    return true;
	}
    }
}
