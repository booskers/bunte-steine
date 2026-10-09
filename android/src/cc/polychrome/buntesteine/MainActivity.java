package cc.polychrome.buntesteine;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Locale;

/**
 * Bunte Steine: the game (assets/index.html) full screen, always upright. The status and navigation bars stay hidden
 * so the stones can be as large as possible; a swipe from the edge brings them back for a moment.
 */
public class MainActivity extends Activity {
    static final String PAGE = "file:///android_asset/index.html";

    WebView web;
    TextToSpeech tts;
    volatile boolean ttsReady;
    float l, t, r, b;   // the camera notch, in CSS pixels

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // no dark screen while someone thinks
        if (Build.VERSION.SDK_INT >= 28)
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(24, 33, 46));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // the game, settings and records are kept in localStorage
        s.setTextZoom(100);                        // the page sizes its own text to the stones
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) { return true; }   // nothing leaves the game
            @Override public void onPageFinished(WebView v, String url) { sendInsets(); }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);

        // the notch: the page keeps its stones and buttons clear of it
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            float d = getResources().getDisplayMetrics().density;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.displayCutout());
                l = i.left / d; t = i.top / d; r = i.right / d; b = i.bottom / d;
            } else if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                android.view.DisplayCutout c = insets.getDisplayCutout();
                l = c.getSafeInsetLeft() / d; t = c.getSafeInsetTop() / d; r = c.getSafeInsetRight() / d; b = c.getSafeInsetBottom() / d;
            }
            sendInsets();
            return insets;
        });

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            // the game speaks the phone's language, like the page: German, otherwise English
            boolean de = "de".equals(Locale.getDefault().getLanguage());
            int ok = tts.setLanguage(de ? Locale.GERMANY : Locale.US);
            if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(de ? Locale.GERMAN : Locale.ENGLISH);
            tts.setSpeechRate(0.9f);
            ttsReady = true;
        });

        hideBars();
        web.loadUrl(PAGE);

        // updates come quietly while the game is closed (see Updater)
        Updater.onChange = () -> runOnUiThread(() -> { if (web != null) web.evaluateJavascript("window.onUpdate&&onUpdate()", null); });
        Updater.schedule(this);
    }

    void sendInsets() {
        if (web != null) web.evaluateJavascript("window.setInsets&&setInsets(" + l + "," + t + "," + r + "," + b + ")", null);
    }

    @SuppressWarnings("deprecation")
    void hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    // back closes an open window in the game first; from the plain table it leaves (the game is saved, and comes back as it was)
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.appBack?appBack():false", v -> { if (!"true".equals(v)) moveTaskToBack(true); });
    }

    @Override
    protected void onPause() {
        web.evaluateJavascript("window.saveNow&&saveNow()", null);
        if (ttsReady) tts.stop();
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.visible = true;
        web.onResume();
        hideBars();
    }

    // the game was left: a good moment to look for an update (it is installed only while the game is closed)
    @Override
    protected void onStop() {
        Updater.visible = false;
        Updater.check(this, false, null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Updater.onChange = null;
        if (tts != null) tts.shutdown();
        web.destroy();
        super.onDestroy();
    }

    /** window.Android in the page */
    class Bridge {
        @JavascriptInterface public String updateInfo() { return Updater.info(MainActivity.this); }
        @JavascriptInterface public void checkUpdate() { Updater.check(MainActivity.this, true, null); }
        @JavascriptInterface public void installUpdate() { runOnUiThread(() -> Updater.install(MainActivity.this, true)); }
        @JavascriptInterface public void autoUpdates() { runOnUiThread(() -> Updater.becomeInstaller(MainActivity.this)); }
        @JavascriptInterface public void speak(String text) {
            if (ttsReady && text != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bs");
        }
        @JavascriptInterface public void stopSpeaking() { if (ttsReady) tts.stop(); }

        // the page's buzzes: "35" or "70,60,70" (on, off, on … in ms). Android's web view ignores navigator.vibrate,
        // so the phone's vibrator is driven from here
        @JavascriptInterface @SuppressWarnings("deprecation")
        public void vibrate(String pattern) {
            Vibrator v = Build.VERSION.SDK_INT >= 31
                ? ((VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator()
                : (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator() || pattern == null) return;
            String[] parts = pattern.split(",");
            long[] t = new long[parts.length + 1];   // a waveform starts with a pause: 0
            try {
                for (int i = 0; i < parts.length; i++) t[i + 1] = Math.max(0, Math.min(1000, Long.parseLong(parts[i].trim())));
            } catch (NumberFormatException e) { return; }
            if (parts.length == 1) v.vibrate(VibrationEffect.createOneShot(Math.max(1, t[1]), VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(VibrationEffect.createWaveform(t, -1));
        }
    }
}
