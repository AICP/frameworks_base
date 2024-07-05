/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.autofill;

import static com.android.server.autofill.Session.REQUEST_ID_KEY;
import static com.android.server.autofill.Session.SESSION_ID_KEY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentSender;
import android.os.Bundle;
import android.service.autofill.ConvertCredentialResponse;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.util.Slog;
import android.view.autofill.IAutoFillManagerClient;
import android.view.inputmethod.InlineSuggestionsRequest;

/**
 * Requests autofill response from a Remote Autofill Service. This autofill service can be
 * either a Credential Autofill Service or the user-opted autofill service.
 *
 * <p> With the credman integration, Autofill Framework handles two types of autofill flows -
 * regular autofill flow and the credman integrated autofill flow. With the credman integrated
 * autofill, the data source for the autofill is handled by the credential autofill proxy
 * service, which is hidden from users. By the time a session gets created, the framework
 * decides on one of the two flows by setting the remote fill service to be either the
 * user-elected autofill service or the hidden credential autofill service by looking at the
 * user-focused view's credential attribute. If the user needs both flows concurrently because
 * the screen has both regular autofill fields and credential fields, then secondary provider
 * handler will be used to fetch supplementary fill response. Depending on which remote fill
 * service the session was initially created with, the secondary provider handler will contain
 * the remaining autofill service. </p>
 *
 * @hide
 */
final class SecondaryProviderHandler implements RemoteFillService.FillServiceCallbacks {
    private static final String TAG = "SecondaryProviderHandler";

    private final RemoteFillService mRemoteFillService;
    private final SecondaryProviderCallback mCallback;
    private int mLastFlag;

    SecondaryProviderHandler(
            @NonNull Context context, int userId, boolean bindInstantServiceAllowed,
            SecondaryProviderCallback callback, ComponentName componentName) {
        mRemoteFillService = new RemoteFillService(context, componentName, userId, this,
                bindInstantServiceAllowed);
        mCallback = callback;
        Slog.v(TAG, "Creating a secondary provider handler with component name, " + componentName);
    }
    @Override
    public void onServiceDied(RemoteFillService service) {
        mRemoteFillService.destroy();
    }

    @Override
    public void onFillRequestSuccess(int requestId, @Nullable FillResponse response,
                                     @NonNull String servicePackageName, int requestFlags) {
        Slog.v(TAG, "Received a fill response: " + response);
        mCallback.onSecondaryFillResponse(response, mLastFlag);
    }

    @Override
    public void onFillRequestFailure(int requestId, @Nullable CharSequence message) {

    }

    @Override
    public void onFillRequestTimeout(int requestId) {

    }

    @Override
    public void onSaveRequestSuccess(@NonNull String servicePackageName,
                                     @Nullable IntentSender intentSender) {

    }

    @Override
    public void onSaveRequestFailure(@Nullable CharSequence message,
                                     @NonNull String servicePackageName) {

    }

    @Override
    public void  onConvertCredentialRequestSuccess(@NonNull ConvertCredentialResponse
            convertCredentialResponse) {

    }

    /**
     * Requests a new fill response.
     */
    public void onFillRequest(FillRequest pendingFillRequest,
            InlineSuggestionsRequest pendingInlineSuggestionsRequest, int flag, int id,
            IAutoFillManagerClient client) {
        Slog.v(TAG, "Requesting fill response to secondary provider.");
        mLastFlag = flag;
        if (mRemoteFillService != null && mRemoteFillService.isCredentialAutofillService()) {
            Slog.v(TAG, "About to call CredAutofill service as secondary provider");
            FillRequest request = addSessionIdAndRequestIdToClientState(pendingFillRequest,
                    pendingInlineSuggestionsRequest, id);
            mRemoteFillService.onFillCredentialRequest(request, client);
        } else {
            mRemoteFillService.onFillRequest(pendingFillRequest);
        }
    }

    private FillRequest addSessionIdAndRequestIdToClientState(FillRequest pendingFillRequest,
            InlineSuggestionsRequest pendingInlineSuggestionsRequest, int sessionId) {
        if (pendingFillRequest.getClientState() == null) {
            pendingFillRequest = new FillRequest(pendingFillRequest.getId(),
                    pendingFillRequest.getFillContexts(),
                    pendingFillRequest.getHints(),
                    new Bundle(),
                    pendingFillRequest.getFlags(),
                    pendingInlineSuggestionsRequest,
                    pendingFillRequest.getDelayedFillIntentSender());
        }
        pendingFillRequest.getClientState().putInt(SESSION_ID_KEY, sessionId);
        pendingFillRequest.getClientState().putInt(REQUEST_ID_KEY, pendingFillRequest.getId());
        return pendingFillRequest;
    }

    public void destroy() {
        mRemoteFillService.destroy();
    }

    interface SecondaryProviderCallback {
        void onSecondaryFillResponse(@Nullable FillResponse fillResponse, int flags);
    }
}
