package com.example.englishcoach;

import android.content.Context;
import android.util.Log;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import java.io.IOException;

public class VoskManager {
    private static final String TAG = "VoskManager";
    private Context context;
    private VoskCallback callback;
    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private boolean initialized = false;

    public interface VoskCallback {
        void onReady();
        void onPartialResult(String text);
        void onFinalResult(String text);
        void onError(String error);
    }

    public VoskManager(Context context) {
        this.context = context;
    }

    public void init(VoskCallback callback) {
        this.callback = callback;
        try {
            model = new Model(ModelDownloadManager.getVoskModelDir(context).getAbsolutePath());
            recognizer = new Recognizer(model, 16000.0f);
            initialized = true;
            Log.i(TAG, "Vosk model loaded");
            if (callback != null) callback.onReady();
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
            if (callback != null) callback.onError("Failed to load speech model: " + e.getMessage());
        }
    }

    public void startListening() {
        if (!initialized || model == null || recognizer == null) {
            if (callback != null) callback.onError("Speech model not ready");
            return;
        }

        try {
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(new RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                    if (callback != null) {
                        String text = "";
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(hypothesis);
                            text = json.optString("partial", "");
                        } catch (Exception e) {
                            text = hypothesis;
                        }
                        callback.onPartialResult(text);
                    }
                }

                @Override
                public void onResult(String hypothesis) { }

                @Override
                public void onFinalResult(String hypothesis) {
                    if (callback != null) {
                        String text = "";
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(hypothesis);
                            text = json.optString("text", "");
                        } catch (Exception e) {
                            text = hypothesis;
                        }
                        callback.onFinalResult(text);
                    }
                }

                @Override
                public void onError(Exception exception) {
                    Log.e(TAG, "Recognition error", exception);
                    if (callback != null) callback.onError(exception.getMessage());
                }

                @Override
                public void onTimeout() { }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error creating speech service", e);
            if (callback != null) callback.onError("Failed to start listening");
        }
    }

    public void stopListening() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        }
    }

    public void pause() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
        }
    }

    public void resume() {
        if (initialized && recognizer != null) {
            startListening();
        }
    }

    public void destroy() {
        stopListening();
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        initialized = false;
    }
}
