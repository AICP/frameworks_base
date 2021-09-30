package com.android.systemui.screenrecord;

import com.android.systemui.screenrecord.IRecordingCallback;

interface IRemoteRecording {
    void startRecording(int audioSource, boolean showTaps, boolean showStopDot, boolean lowQuality, boolean longerDuration);
    void stopRecording();
    boolean isRecording();
    boolean isStarting();
    void addRecordingCallback(in IRecordingCallback callback);
    void removeRecordingCallback(in IRecordingCallback callback);
}
