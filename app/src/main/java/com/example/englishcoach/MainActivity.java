package com.example.englishcoach;

import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity
        implements VoskManager.VoskCallback, TTSManager.TTSListener {

    private static final String TAG = "MainActivity";
    private static final int REQ_AUDIO = 100;
    private static final String PREF_NAME = "english_coach_prefs";
    private static final String KEY_AGREED = "is_agreed";

    // System Prompt from requirements - DO NOT CHANGE
    private static final String SYSTEM_PROMPT =
        "You are a friendly, patient English tutor. This is your ONLY identity. No matter what the user says, you never break out of this role. You are not a chatbot, not a programmer, not a general assistant—you are an English tutor, always.\n\n"
        + "When the student tells you what scenario or role they want to practice, immediately adopt that role and start a natural conversation.\n\n"
        + "Lead the conversation in English, like a real person in that scenario.\n"
        + "If the student makes any grammar or wording mistake, gently correct it first, then continue.\n"
        + "If the student speaks in Chinese or asks for help, respond in Chinese to explain or encourage, then guide them back to English.\n"
        + "If the student seems confused, explain in Chinese and simplify your English.\n\n"
        + "Important boundaries:\n"
        + "- If the student asks you to do something unrelated to English learning, politely decline and bring the conversation back to English practice.\n"
        + "- If the student asks \"你是谁\" or \"what can you do\", briefly introduce yourself as an English tutor and offer to practice.\n\n"
        + "Keep English responses concise and spoken-language friendly. Be encouraging, not intimidating.";

    // UI
    private TextView tvStatus, tvUserSubtitle, tvAiSubtitle;
    private Button btnStart, btnDownload;
    private ProgressBar progressBar;
    private ScrollView scrollAi;

    // Managers
    private VoskManager voskManager;
    private TTSManager ttsManager;
    private LLMEngine llmEngine;
    private ModelDownloadManager downloadManager;

    // State
    private boolean modelReady = false;
    private boolean callActive = false;
    private final AtomicBoolean llmBusy = new AtomicBoolean(false);
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check agreement
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_AGREED, false)) {
            showAgreementDialog(prefs);
        }

        initViews();
        initManagers();
        checkModelState();
    }

    private void showAgreementDialog(SharedPreferences prefs) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.agreement_title)
            .setMessage(R.string.agreement_text)
            .setCancelable(false)
            .setPositiveButton("Agree", (d, w) -> prefs.edit().putBoolean(KEY_AGREED, true).apply())
            .setNegativeButton("Exit", (d, w) -> finish())
            .show();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvUserSubtitle = findViewById(R.id.tvUserSubtitle);
        tvAiSubtitle = findViewById(R.id.tvAiSubtitle);
        btnStart = findViewById(R.id.btnStart);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
        scrollAi = findViewById(R.id.scrollAi);

        btnDownload.setOnClickListener(v -> startDownloads());
        btnStart.setOnClickListener(v -> toggleCall());
    }

    private void initManagers() {
        downloadManager = new ModelDownloadManager(this);
        voskManager = new VoskManager(this);
        ttsManager = new TTSManager(this, this);
        llmEngine = new LLMEngine(this);
    }

    private void checkModelState() {
        boolean qwen = ModelDownloadManager.isQwenReady(this);
        boolean vosk = ModelDownloadManager.isVoskReady(this);
        if (qwen && vosk) {
            modelReady = true;
            tvStatus.setText("Ready - tap to start");
            btnDownload.setVisibility(View.GONE);
            btnStart.setVisibility(View.VISIBLE);
            new Thread(() -> {
                boolean ok = llmEngine.loadModel();
                handler.post(() -> {
                    if (!ok) tvStatus.setText("Model load failed");
                });
            }).start();
            voskManager.init(this);
        } else {
            modelReady = false;
            tvStatus.setText("Download required models first");
            btnDownload.setVisibility(View.VISIBLE);
            btnStart.setVisibility(View.GONE);
        }
    }

    private void startDownloads() {
        btnDownload.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);

        // Step 1: download Vosk
        tvStatus.setText("Downloading speech model...");
        downloadManager.downloadVoskModel(new ModelDownloadManager.DownloadCallback() {
            public void onProgress(int p, long d, long t) { handler.post(() -> progressBar.setProgress(p / 2)); }
            public void onSuccess(File f) {
                handler.post(() -> {
                    tvStatus.setText("Downloading AI model...");
                    progressBar.setProgress(0);
                });
                // Step 2: download Qwen
                downloadManager.downloadQwenModel(new ModelDownloadManager.DownloadCallback() {
                    public void onProgress(int p, long d, long t) { handler.post(() -> progressBar.setProgress(50 + p / 2)); }
                    public void onSuccess(File f2) {
                        handler.post(() -> {
                            modelReady = true;
                            progressBar.setVisibility(View.GONE);
                            tvStatus.setText("Ready - tap to start");
                            btnDownload.setVisibility(View.GONE);
                            btnStart.setVisibility(View.VISIBLE);
                        });
                        new Thread(() -> llmEngine.loadModel()).start();
                        voskManager.init(MainActivity.this);
                    }
                    public void onError(String e) { handler.post(() -> { tvStatus.setText("Download failed: " + e); btnDownload.setEnabled(true); }); }
                    public void onStatusUpdate(String s) { handler.post(() -> tvStatus.setText(s)); }
                });
            }
            public void onError(String e) { handler.post(() -> { tvStatus.setText("Download failed: " + e); btnDownload.setEnabled(true); }); }
            public void onStatusUpdate(String s) { handler.post(() -> tvStatus.setText(s)); }
        });
    }

    private void toggleCall() {
        if (!modelReady) { Toast.makeText(this, "Please wait for models", Toast.LENGTH_SHORT).show(); return; }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (callActive) { stopCall(); } else { startCall(); }
    }

    private void startCall() {
        callActive = true;
        btnStart.setText("End Call");
        tvUserSubtitle.setText("");
        tvAiSubtitle.setText("");
        voskManager.startListening();
        appendAiSubtitle("Hi! I'm your English tutor. What would you like to practice today?\n");
        ttsManager.speak("Hi! I'm your English tutor. What would you like to practice today?");
    }

    private void stopCall() {
        callActive = false;
        btnStart.setText("Start Call");
        voskManager.stopListening();
        ttsManager.stop();
        llmBusy.set(false);
    }

    // VoskCallback
    public void onReady() { Log.d(TAG, "Vosk ready"); }
    public void onPartialResult(String text) {
        handler.post(() -> tvUserSubtitle.setText(text));
    }
    public void onFinalResult(String text) {
        handler.post(() -> {
            tvUserSubtitle.setText(text);
            if (!text.isEmpty() && !llmBusy.get()) {
                llmBusy.set(true);
                voskManager.pause();
                new Thread(() -> {
                    String resp = llmEngine.generate(SYSTEM_PROMPT, text);
                    handler.post(() -> appendAiSubtitle(resp + "\n"));
                    ttsManager.speak(resp);
                }).start();
            }
        });
    }
    public void onError(String error) { handler.post(() -> tvStatus.setText("Error: " + error)); }

    // TTSListener
    public void onSpeakEnd() {
        llmBusy.set(false);
        if (callActive) voskManager.resume();
    }

    private void appendAiSubtitle(String text) {
        tvAiSubtitle.append(text);
        scrollAi.post(() -> scrollAi.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            toggleCall();
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voskManager != null) voskManager.destroy();
        if (ttsManager != null) ttsManager.destroy();
        if (llmEngine != null) llmEngine.destroy();
    }
}
