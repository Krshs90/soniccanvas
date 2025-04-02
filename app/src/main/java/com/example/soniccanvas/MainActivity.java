
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

    private static final String TAG = "SonicCanvas";
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
        visualizerView.setSensitivityMultiplier(5.0f); // Increase sensitivity

        // Request audio permissions
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
                Log.d(TAG, "Visualizer type changed to: " + currentVisualizerType);
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

        // Request MODIFY_AUDIO_SETTINGS permission explicitly
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS},
                    MODIFY_AUDIO_PERMISSION);
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
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Release device audio visualizer if it exists
            releaseDeviceAudioVisualizer();

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                Toast.makeText(this, "AudioRecord failed to initialize.", Toast.LENGTH_LONG).show();
                return;
            }
            Log.d(TAG, "Microphone recording setup successfully");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error initializing AudioRecord: " + e.getMessage());
            Toast.makeText(this, "Error initializing audio recording. Please check your device.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupDeviceAudioCapture() {
        try {
            // Release microphone if it exists
            releaseMicrophoneRecording();

            // Check permission for MODIFY_AUDIO_SETTINGS
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio settings permission required for device audio capture", Toast.LENGTH_LONG).show();
                isUsingMicrophone = true;
                audioSourceSwitch.setChecked(false);
                setupMicrophoneRecording();
                return;
            }

            // Create a Visualizer to capture device audio output
            int captureSize = Visualizer.getCaptureSizeRange()[1]; // Use maximum capture size
            Log.d(TAG, "Creating visualizer with capture size: " + captureSize);

            try {
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
                                    audioData[i] = (short) ((waveform[i] & 0xFF) * 128); // Amplify signal for better visualization
                                }

                                float magnitude = calculateMagnitude(audioData, audioData.length);
                                visualizerView.updateVisualizer(magnitude * 2.0f, audioData, audioData.length); // Increase sensitivity
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
                Log.d(TAG, "Device audio capture setup successfully");

            } catch (Exception e) {
                Log.e(TAG, "Error creating Visualizer: " + e.getMessage(), e);
                throw e;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error setting up device audio capture: " + e.getMessage(), e);
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
                audioRecord = null;
                Log.d(TAG, "Microphone recording released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioRecord: " + e.getMessage());
            }
        }
    }

    private void releaseDeviceAudioVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
                visualizer = null;
                Log.d(TAG, "Device audio visualizer released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing Visualizer: " + e.getMessage());
            }
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
            Log.d(TAG, "Microphone recording started");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error starting AudioRecord: " + e.getMessage());
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
            Log.d(TAG, "Device audio capture started");
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error enabling Visualizer: " + e.getMessage());
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
                audioRecord = null;
                Log.d(TAG, "Microphone recording stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recording: " + e.getMessage());
            }
        }

        if (recordingThread != null) {
            try {
                recordingThread.join();
                recordingThread = null;
                Log.d(TAG, "Recording thread stopped");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Error stopping recording thread: " + e.getMessage());
            }
        }
    }

    private void stopDeviceAudioCapture() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                Log.d(TAG, "Device audio capture stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error disabling Visualizer: " + e.getMessage());
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
                Log.e(TAG, "Error reading audio data: " + e.getMessage());
                stopRecording();
                return;
            }

            if (readResult > 0) {
                float magnitude = calculateMagnitude(buffer, readResult);
                int finalReadResult = readResult;
                // Increase sensitivity by multiplying magnitude
                handler.post(() -> visualizerView.updateVisualizer(magnitude * 2.5f, buffer, finalReadResult));
            } else if (readResult < 0) {
                Log.e(TAG, "Error reading audio data, readResult: " + readResult);
                stopRecording();
                return;
            }
            try {
                Thread.sleep(VISUALIZATION_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Thread sleep interrupted: " + e.getMessage());
                return;
            }
        }
    }

    private float calculateMagnitude(short[] buffer, int readResult) {
        float sum = 0;
        for (int i = 0; i < readResult; i++) {
            sum += Math.abs(buffer[i]);
        }
        return sum / readResult;
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
                    Log.d(TAG, "RECORD_AUDIO permission granted");
                } else {
                    Toast.makeText(this, "Audio recording permission required for visualization.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "RECORD_AUDIO permission denied");
                    finish();
                }
                break;

            case MODIFY_AUDIO_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "MODIFY_AUDIO_SETTINGS permission granted");
                } else {
                    // If permission denied, disable device audio option
                    Toast.makeText(this, "Permission needed for device audio capture. Only microphone will be available.", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "MODIFY_AUDIO_SETTINGS permission denied");
                    isUsingMicrophone = true;
                    audioSourceSwitch.setChecked(false);
                    audioSourceSwitch.setEnabled(false);
                }
                break;
        }
    }
}
