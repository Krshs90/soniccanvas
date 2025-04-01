package com.example.soniccanvas;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int RECORD_AUDIO_PERMISSION = 0;
    private static final int MODIFY_AUDIO_PERMISSION = 1;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final long VISUALIZATION_INTERVAL = 16;

    // Audio capture options
    private AudioRecord audioRecord;
    private Visualizer visualizer; // For device audio
    private boolean isUsingMicrophone = true;

    private boolean isRecording = false;
    private Thread recordingThread;
    private VisualizerView visualizerView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Button startStopButton;
    private Chip waveformChip;
    private Chip barsChip;
    private Chip circularChip;
    private TextView permissionText;
    private ChipGroup visualizerTypesGroup;
    private SwitchMaterial audioSourceSwitch;
    private int currentVisualizerType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        visualizerView = findViewById(R.id.visualizerView);
        startStopButton = findViewById(R.id.startStopButton);
        waveformChip = findViewById(R.id.waveformButton);
        barsChip = findViewById(R.id.barsButton);
        circularChip = findViewById(R.id.circularButton);
        permissionText = findViewById(R.id.permissionText);
        visualizerTypesGroup = findViewById(R.id.visualizerTypesGroup);
        audioSourceSwitch = findViewById(R.id.audioSourceSwitch);

        visualizerView.setVisualizerType(currentVisualizerType);
        visualizerView.setSensitivityMultiplier(2.5f); // Default sensitivity

        // Request both audio permissions
        requestAudioPermissions();

        // Set up audio source switch listener
        audioSourceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isUsingMicrophone = !isChecked;
            if (isRecording) {
                stopRecording();
                startRecording();
            }

            String message = isUsingMicrophone ? "Using microphone for input" : "Using device audio for input";
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });

        // Set up click listeners
        startStopButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
                startStopButton.setText(R.string.start_visualizer);
            } else {
                startRecording();
                startStopButton.setText(R.string.stop_visualizer);
            }
        });

        // Set up chip group listener
        visualizerTypesGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.size() > 0) {
                int chipId = checkedIds.get(0);
                if (chipId == R.id.waveformButton) {
                    currentVisualizerType = 0;
                } else if (chipId == R.id.barsButton) {
                    currentVisualizerType = 1;
                } else if (chipId == R.id.circularButton) {
                    currentVisualizerType = 2;
                }
                visualizerView.setVisualizerType(currentVisualizerType);
            }
        });

        // Set initial selection
        waveformChip.setChecked(true);
    }

    private void requestAudioPermissions() {
        // Request RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION);
            permissionText.setVisibility(View.VISIBLE);
        } else {
            permissionText.setVisibility(View.GONE);
        }

        // For Android 10+ we need this permission for device audio capture
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS},
                        MODIFY_AUDIO_PERMISSION);
            }
        }
    }

    private void setupAudioRecording() {
        if (isUsingMicrophone) {
            setupMicrophoneRecording();
        } else {
            setupDeviceAudioCapture();
        }
    }

    private void setupMicrophoneRecording() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            // Release device audio visualizer if it exists
            releaseDeviceAudioVisualizer();

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, // Use MIC instead of VOICE_COMMUNICATION for better sensitivity
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecord", "AudioRecord not initialized");
                Toast.makeText(this, "AudioRecord failed to initialize.", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (IllegalArgumentException e) {
            Log.e("AudioRecord", "Error initializing AudioRecord: " + e.getMessage());
            Toast.makeText(this, "Error initializing audio recording. Please check your device.", Toast.LENGTH_LONG).show();
            return;
        }
    }

    private void setupDeviceAudioCapture() {
        try {
            // Release microphone if it exists
            releaseMicrophoneRecording();

            // Make sure we have permission for modifying audio settings
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Audio settings permission required for device audio capture.", Toast.LENGTH_LONG).show();
                    isUsingMicrophone = true;
                    audioSourceSwitch.setChecked(false);
                    setupMicrophoneRecording();
                    return;
                }
            }

            // Create a Visualizer to capture device audio output
            int captureSize = Visualizer.getCaptureSizeRange()[1]; // Use maximum capture size
            Log.d("Visualizer", "Creating visualizer with capture size: " + captureSize);

            visualizer = new Visualizer(0); // 0 = output mix (device audio)
            visualizer.setEnabled(false);
            visualizer.setCaptureSize(captureSize);

            // Set up data capture listener
            visualizer.setDataCaptureListener(
                    new Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                            // Convert byte[] to short[] for compatibility with existing code
                            short[] audioData = new short[waveform.length];
                            for (int i = 0; i < waveform.length; i++) {
                                audioData[i] = (short) ((waveform[i] & 0xFF) * 128);
                            }

                            float magnitude = calculateMagnitude(audioData, audioData.length);
                            visualizerView.updateVisualizer(magnitude, audioData, audioData.length);
                        }

                        @Override
                        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                            // We're not using FFT data in this implementation
                        }
                    },
                    Visualizer.getMaxCaptureRate(), // Maximum capture rate for smooth visualization
                    true, // Capture waveform
                    false // Don't capture FFT
            );

        } catch (Exception e) {
            Log.e("Visualizer", "Error setting up device audio capture: " + e.getMessage(), e);
            Toast.makeText(this, "Error setting up device audio capture. Falling back to microphone.", Toast.LENGTH_LONG).show();
            isUsingMicrophone = true;
            audioSourceSwitch.setChecked(false);
            setupMicrophoneRecording();
        }
    }

    private void releaseMicrophoneRecording() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e("AudioRecord", "Error releasing AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }
    }

    private void releaseDeviceAudioVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception e) {
                Log.e("Visualizer", "Error releasing Visualizer: " + e.getMessage());
            }
            visualizer = null;
        }
    }

    private void startRecording() {
        setupAudioRecording();

        if (isUsingMicrophone) {
            startMicrophoneRecording();
        } else {
            startDeviceAudioCapture();
        }
    }

    private void startMicrophoneRecording() {
        if (audioRecord == null) {
            setupMicrophoneRecording();
            if (audioRecord == null) {
                return;
            }
        }

        try {
            audioRecord.startRecording();
            isRecording = true;
            recordingThread = new Thread(this::processAudioData);
            recordingThread.start();
        } catch (IllegalStateException e) {
            Log.e("AudioRecord", "Error starting AudioRecord: " + e.getMessage());
            Toast.makeText(this, "Error starting audio recording.", Toast.LENGTH_LONG).show();
        }
    }

    private void startDeviceAudioCapture() {
        if (visualizer == null) {
            setupDeviceAudioCapture();
            if (visualizer == null) {
                return;
            }
        }

        try {
            visualizer.setEnabled(true);
            isRecording = true;
        } catch (IllegalStateException e) {
            Log.e("Visualizer", "Error enabling Visualizer: " + e.getMessage());
            Toast.makeText(this, "Error capturing device audio.", Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        isRecording = false;

        if (isUsingMicrophone) {
            stopMicrophoneRecording();
        } else {
            stopDeviceAudioCapture();
        }

        handler.post(visualizerView::clear);
    }

    private void stopMicrophoneRecording() {
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e("AudioRecord", "Error stopping recording: " + e.getMessage());
            }
            audioRecord = null;
        }

        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e("AudioRecord", "Error stopping recording thread: " + e.getMessage());
            }
            recordingThread = null;
        }
    }

    private void stopDeviceAudioCapture() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
            } catch (Exception e) {
                Log.e("Visualizer", "Error disabling Visualizer: " + e.getMessage());
            }
        }
    }

    private void processAudioData() {
        short[] buffer = new short[BUFFER_SIZE / 4];
        while (isRecording && audioRecord != null) {
            int readResult = 0;
            try {
                readResult = audioRecord.read(buffer, 0, buffer.length);
            } catch (IllegalStateException e) {
                Log.e("AudioRecord", "Error reading audio data: " + e.getMessage());
                stopRecording();
                return;
            }

            if (readResult > 0) {
                float magnitude = calculateMagnitude(buffer, readResult);
                int finalReadResult = readResult;
                handler.post(() -> visualizerView.updateVisualizer(magnitude, buffer, finalReadResult));
            } else if (readResult < 0) {
                Log.e("AudioRecord", "Error reading audio data, readResult: " + readResult);
                stopRecording();
                return;
            }
            try {
                Thread.sleep(VISUALIZATION_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e("AudioRecord", "Thread sleep interrupted: " + e.getMessage());
                return;
            }
        }
    }

    private float calculateMagnitude(short[] buffer, int readResult) {
        float sum = 0;
        for (int i = 0; i < readResult; i++) {
            sum += Math.abs(buffer[i]);
        }
        return (sum / readResult);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMicrophoneRecording();
        releaseDeviceAudioVisualizer();

        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            recordingThread = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case RECORD_AUDIO_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionText.setVisibility(View.GONE);
                } else {
                    Toast.makeText(this, "Audio recording permission required for visualization.", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;

            case MODIFY_AUDIO_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // If permission denied, disable device audio option
                    Toast.makeText(this, "Permission needed for device audio capture. Only microphone will be available.", Toast.LENGTH_LONG).show();
                    isUsingMicrophone = true;
                    audioSourceSwitch.setChecked(false);
                    audioSourceSwitch.setEnabled(false);
                }
                break;
        }
    }
}
