/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.tv.interactive;

import android.graphics.Rect;
import android.media.tv.AdBuffer;
import android.media.tv.AdResponse;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvRecordingInfo;
import android.media.tv.interactive.AppLinkInfo;
import android.media.tv.interactive.ITvInteractiveAppClient;
import android.media.tv.interactive.ITvInteractiveAppManagerCallback;
import android.media.tv.interactive.TvInteractiveAppServiceInfo;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.view.Surface;

/**
 * Interface to the TV interactive app service.
 * @hide
 */
interface ITvInteractiveAppManager {
    List<TvInteractiveAppServiceInfo> getTvInteractiveAppServiceList(int userId);
    List<AppLinkInfo> getAppLinkInfoList(int userId);
    void registerAppLinkInfo(String tiasId, in AppLinkInfo info, int userId);
    void unregisterAppLinkInfo(String tiasId, in AppLinkInfo info, int userId);
    void sendAppLinkCommand(String tiasId, in Bundle command, int userId);
    void startInteractiveApp(in IBinder sessionToken, int userId);
    void stopInteractiveApp(in IBinder sessionToken, int userId);
    void resetInteractiveApp(in IBinder sessionToken, int userId);
    void createBiInteractiveApp(
            in IBinder sessionToken, in Uri biIAppUri, in Bundle params, int userId);
    void destroyBiInteractiveApp(in IBinder sessionToken, in String biIAppId, int userId);
    void setTeletextAppEnabled(in IBinder sessionToken, boolean enable, int userId);
    void sendCurrentVideoBounds(in IBinder sessionToken, in Rect bounds, int userId);
    void sendCurrentChannelUri(in IBinder sessionToken, in Uri channelUri, int userId);
    void sendCurrentChannelLcn(in IBinder sessionToken, int lcn, int userId);
    void sendStreamVolume(in IBinder sessionToken, float volume, int userId);
    void sendTrackInfoList(in IBinder sessionToken, in List<TvTrackInfo> tracks, int userId);
    void sendCurrentTvInputId(in IBinder sessionToken, in String inputId, int userId);
    void sendTimeShiftMode(in IBinder sessionToken, int mode, int userId);
    void sendAvailableSpeeds(in IBinder sessionToken, in float[] speeds, int userId);
    void sendSigningResult(in IBinder sessionToken, in String signingId, in byte[] result,
            int userId);
    void sendCertificate(in IBinder sessionToken, in String host, int port,
            in Bundle certBundle, int userId);
    void sendTvRecordingInfo(in IBinder sessionToken, in TvRecordingInfo recordingInfo, int userId);
    void sendTvRecordingInfoList(in IBinder sessionToken,
            in List<TvRecordingInfo> recordingInfoList, int userId);
    void notifyError(in IBinder sessionToken, in String errMsg, in Bundle params, int userId);
    void notifyTimeShiftPlaybackParams(
            in IBinder sessionToken, in PlaybackParams params, int userId);
    void notifyTimeShiftStatusChanged(
            in IBinder sessionToken, in String inputId, int status, int userId);
    void notifyTimeShiftStartPositionChanged(
            in IBinder sessionToken, in String inputId, long timeMs, int userId);
    void notifyTimeShiftCurrentPositionChanged(
            in IBinder sessionToken, in String inputId, long timeMs, int userId);
    void notifyRecordingConnectionFailed(
            in IBinder sessionToken, in String recordingId, in String inputId, int userId);
    void notifyRecordingDisconnected(
            in IBinder sessionToken, in String recordingId, in String inputId, int userId);
    void notifyRecordingTuned(
            in IBinder sessionToken, in String recordingId, in Uri channelUri, int userId);
    void notifyRecordingError(
            in IBinder sessionToken, in String recordingId, int err, int userId);
    void notifyRecordingScheduled(
            in IBinder sessionToken, in String recordingId, in String requestId, int userId);
    void createSession(in ITvInteractiveAppClient client, in String iAppServiceId, int type,
            int seq, int userId);
    void releaseSession(in IBinder sessionToken, int userId);
    void notifyTuned(in IBinder sessionToken, in Uri channelUri, int userId);
    void notifyTrackSelected(in IBinder sessionToken, int type, in String trackId, int userId);
    void notifyTracksChanged(in IBinder sessionToken, in List<TvTrackInfo> tracks, int userId);
    void notifyVideoAvailable(in IBinder sessionToken, int userId);
    void notifyVideoUnavailable(in IBinder sessionToken, int reason, int userId);
    void notifyVideoFreezeUpdated(in IBinder sessionToken, boolean isFrozen, int userId);
    void notifyContentAllowed(in IBinder sessionToken, int userId);
    void notifyContentBlocked(in IBinder sessionToken, in String rating, int userId);
    void notifySignalStrength(in IBinder sessionToken, int stength, int userId);
    void notifyRecordingStarted(in IBinder sessionToken, in String recordingId, String requestId,
            int userId);
    void notifyRecordingStopped(in IBinder sessionToken, in String recordingId, int userId);
    void notifyTvMessage(in IBinder sessionToken, in int type, in Bundle data, int userId);
    void setSurface(in IBinder sessionToken, in Surface surface, int userId);
    void dispatchSurfaceChanged(in IBinder sessionToken, int format, int width, int height,
            int userId);
    void notifyBroadcastInfoResponse(in IBinder sessionToken, in BroadcastInfoResponse response,
            int UserId);
    void notifyAdResponse(in IBinder sessionToken, in AdResponse response, int UserId);
    void notifyAdBufferConsumed(in IBinder sessionToken, in AdBuffer buffer, int userId);
    void sendSelectedTrackInfo(in IBinder sessionToken, in List<TvTrackInfo> tracks,
            int userId);

    void createMediaView(in IBinder sessionToken, in IBinder windowToken, in Rect frame,
            int userId);
    void relayoutMediaView(in IBinder sessionToken, in Rect frame, int userId);
    void removeMediaView(in IBinder sessionToken, int userId);

    void registerCallback(in ITvInteractiveAppManagerCallback callback, int userId);
    void unregisterCallback(in ITvInteractiveAppManagerCallback callback, int userId);
}
