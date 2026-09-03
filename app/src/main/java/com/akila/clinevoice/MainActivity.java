package com.akila.clinevoice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_AUDIO = 1001;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextView status;
    private TextView transcript;
    private Button talkButton;
    private Button sendButton;
    private boolean listening = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("Speech recognition is not available on this device.");
            talkButton.setEnabled(false);
            return;
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 48, 36, 36);

        TextView title = new TextView(this);
        title.setText("CLINE VOICE"); title.setTextSize(28); title.setPadding(0,0,0,18);
        root.addView(title);

        status = new TextView(this);
        status.setText("Hold the microphone button and speak."); status.setTextSize(16); status.setPadding(0,0,0,24);
        root.addView(status);

        transcript = new TextView(this);
        transcript.setText("Your command will appear here."); transcript.setTextSize(20); transcript.setPadding(18,24,18,24);
        root.addView(transcript, new LinearLayout.LayoutParams(-1, 0, 1));

        talkButton = new Button(this); talkButton.setText("🎤  HOLD TO TALK"); talkButton.setTextSize(18);
        root.addView(talkButton, new LinearLayout.LayoutParams(-1, -2));
        sendButton = new Button(this); sendButton.setText("SEND TO CLINE"); sendButton.setTextSize(18); sendButton.setEnabled(false);
        root.addView(sendButton, new LinearLayout.LayoutParams(-1, -2));

        talkButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { startListening(); return true; }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) { stopListening(); return true; }
            return true;
        });
        sendButton.setOnClickListener(v -> sendToCline());
        setContentView(root);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO); return;
        }
        if (recognizer == null || listening) return;
        transcript.setText(""); sendButton.setEnabled(false); status.setText("Listening… release the button when finished.");
        talkButton.setText("🔴  RELEASE TO STOP"); listening = true;
        recognizer.cancel(); recognizer.startListening(recognizerIntent);
    }

    private void stopListening() {
        if (!listening) return;
        listening = false; status.setText("Processing speech…"); talkButton.setText("🎤  HOLD TO TALK");
        try { recognizer.stopListening(); } catch (Exception ignored) {}
    }

    private void sendToCline() {
        String prompt = transcript.getText().toString().trim();
        if (prompt.isEmpty() || prompt.equals("Your command will appear here.")) {
            Toast.makeText(this, "Speak a command first.", Toast.LENGTH_SHORT).show(); return;
        }
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "~/cline-voice.sh");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{prompt});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
        intent.putExtra("com.termux.RUN_COMMAND_LABEL", "Cline Voice");
        intent.putExtra("com.termux.RUN_COMMAND_DESCRIPTION", "Run the spoken command through Cline");
        try {
            startService(intent); status.setText("Sent to Cline.");
        } catch (SecurityException e) {
            status.setText("Termux permission is not granted.");
            Toast.makeText(this, "Enable 'Run commands in Termux environment' for Cline Voice.", Toast.LENGTH_LONG).show();
        } catch (Exception e) { status.setText("Could not start Termux: " + e.getMessage()); }
    }

    @Override public void onReadyForSpeech(Bundle p) { status.setText("Listening… release the button when finished."); }
    @Override public void onBeginningOfSpeech() { status.setText("Hearing you…"); }
    @Override public void onRmsChanged(float v) {}
    @Override public void onBufferReceived(byte[] b) {}
    @Override public void onEndOfSpeech() { status.setText("Processing speech…"); }
    @Override public void onError(int error) {
        listening = false; talkButton.setText("🎤  HOLD TO TALK");
        if (error == SpeechRecognizer.ERROR_NO_MATCH) status.setText("I couldn't understand that. Try again.");
        else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) status.setText("Microphone permission is required.");
        else status.setText("Speech error: " + error);
    }
    @Override public void onResults(Bundle results) {
        listening = false; talkButton.setText("🎤  HOLD TO TALK");
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) { transcript.setText(matches.get(0)); sendButton.setEnabled(true); status.setText("Ready. Review the command, then send it to Cline."); }
        else status.setText("No speech result. Try again.");
    }
    @Override public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) transcript.setText(matches.get(0));
    }
    @Override public void onEvent(int eventType, Bundle params) {}
    @Override protected void onDestroy() { if (recognizer != null) { recognizer.destroy(); recognizer = null; } super.onDestroy(); }
}
