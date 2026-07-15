package com.example.englishcoach;

import android.content.Context;
import android.util.Log;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;
import java.io.IOException;

public class VoskManager {
    private static final String TAG = "VoskManager";
    private Context context;
    private VoskCallback callback;
    private Model model;
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
        StorageService.unpack(context, "model-en-us", "model",
            (model) -> {
                this.model = model;
                initialized = true;
                Log.i(TAG, "Vosk model loaded");
                if (callback != null) callback.onReady();
            },
            (exception) -> {
                Log.e(TAG, "Error unpacking model", exception);
                if (callback != null) callback.onError("Failed to load speech model");
            });
    }

    public void startListening() {
        if (!initialized || model == null) {
            if (callback != null) callback.onError("Speech model not ready");
            return;
        }

        try {
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(new RecognitionListener() {
                @Override
                public void onPartialResult(String hypothesis) {
                    if (callback != null) callback.onPartialResult(hypothesis);
                }

                @Override
                public void onResult(String hypothesis) {
                    // This is called for partial results
                }

                @Override
                public void onFinalResult(String hypothesis) {
                    if (callback != null) callback.onFinalResult(hypothesis);
                }

                @Override
                public void onError(Exception exception) {
                    Log.e(TAG, "Recognition error", exception);
                    if (callback != null) callback.onError(exception.getMessage());
                }

                @Override
                public void onTimeout() {
                    // Silence detected
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error creating recognizer", e);
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
        }
    }

    public void resume() {
        if (speechService != null && initialized) {
            startListening();
        }
    }

    public void destroy() {
        stopListening();
        if (model != null) {
            model.close();
            model = null;
        }
        initialized = false;
    }
}
