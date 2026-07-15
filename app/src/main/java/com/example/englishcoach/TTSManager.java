package com.example.englishcoach;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;

public class TTSManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "TTSManager";
    private Context context;
    private TTSListener listener;
    private TextToSpeech tts;
    private boolean initialized = false;

    public interface TTSListener {
        void onSpeakEnd();
    }

    public TTSManager(Context context, TTSListener listener) {
        this.context = context;
        this.listener = listener;
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    if (listener != null) listener.onSpeakEnd();
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "TTS error for utterance: " + utteranceId);
                    if (listener != null) listener.onSpeakEnd();
                }
            });
            initialized = true;
            Log.i(TAG, "TTS initialized");
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }

    public void speak(String text) {
        if (!initialized || tts == null) {
            Log.w(TAG, "TTS not initialized, skipping");
            if (listener != null) listener.onSpeakEnd();
            return;
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance");
    }

    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        initialized = false;
    }
}
