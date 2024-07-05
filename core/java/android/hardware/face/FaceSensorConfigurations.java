/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.hardware.face;

import static android.hardware.biometrics.BiometricAuthenticator.TYPE_FACE;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.biometrics.face.IFace;
import android.hardware.biometrics.face.SensorProps;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Provides the sensor props for face sensor, if available.
 * @hide
 */
public class FaceSensorConfigurations implements Parcelable {
    private static final String TAG = "FaceSensorConfigurations";

    private final boolean mResetLockoutRequiresChallenge;
    private final Map<String, SensorProps[]> mSensorPropsMap;

    public static final Creator<FaceSensorConfigurations> CREATOR =
            new Creator<FaceSensorConfigurations>() {
                @Override
                public FaceSensorConfigurations createFromParcel(Parcel in) {
                    return new FaceSensorConfigurations(in);
                }

                @Override
                public FaceSensorConfigurations[] newArray(int size) {
                    return new FaceSensorConfigurations[size];
                }
            };

    public FaceSensorConfigurations(boolean resetLockoutRequiresChallenge) {
        mResetLockoutRequiresChallenge = resetLockoutRequiresChallenge;
        mSensorPropsMap = new HashMap<>();
    }

    protected FaceSensorConfigurations(Parcel in) {
        mResetLockoutRequiresChallenge = in.readByte() != 0;
        mSensorPropsMap = in.readHashMap(null, String.class, SensorProps[].class);
    }

    /**
     * Process AIDL instances to extract sensor props and add it to the sensor map.
     * @param aidlInstances available face AIDL instances
     * @param getIFace function that provides the daemon for the specific instance
     */
    public void addAidlConfigs(@NonNull String[] aidlInstances,
            @NonNull Function<String, IFace> getIFace) {
        for (String aidlInstance : aidlInstances) {
            final String fqName = IFace.DESCRIPTOR + "/" + aidlInstance;
            IFace face = getIFace.apply(fqName);
            try {
                if (face != null) {
                    mSensorPropsMap.put(aidlInstance, face.getSensorProps());
                } else {
                    Slog.e(TAG, "Unable to get declared service: " + fqName);
                }
            } catch (RemoteException e) {
                Log.d(TAG, "Unable to get sensor properties!");
            }
        }
    }

    /**
     * Parse through HIDL configuration and add it to the sensor map.
     */
    public void addHidlConfigs(@NonNull String[] hidlConfigStrings,
            @NonNull Context context) {
        final List<HidlFaceSensorConfig> hidlFaceSensorConfigs = new ArrayList<>();
        for (String hidlConfig: hidlConfigStrings) {
            final HidlFaceSensorConfig hidlFaceSensorConfig = new HidlFaceSensorConfig();
            try {
                hidlFaceSensorConfig.parse(hidlConfig, context);
            } catch (Exception e) {
                Log.e(TAG, "HIDL sensor configuration format is incorrect.");
                continue;
            }
            if (hidlFaceSensorConfig.getModality() == TYPE_FACE) {
                hidlFaceSensorConfigs.add(hidlFaceSensorConfig);
            }
        }
        final String hidlHalInstanceName = "defaultHIDL";
        mSensorPropsMap.put(hidlHalInstanceName, hidlFaceSensorConfigs.toArray(
                new SensorProps[hidlFaceSensorConfigs.size()]));
    }

    /**
     * Returns true if any face sensors have been added.
     */
    public boolean hasSensorConfigurations() {
        return mSensorPropsMap.size() > 0;
    }

    /**
     * Returns true if there is only a single face sensor configuration available.
     */
    public boolean isSingleSensorConfigurationPresent() {
        return mSensorPropsMap.size() == 1;
    }

    /**
     * Return sensor props for the given instance. If instance is not available,
     * then null is returned.
     */
    @Nullable
    public Pair<String, SensorProps[]> getSensorPairForInstance(String instance) {
        if (mSensorPropsMap.containsKey(instance)) {
            return new Pair<>(instance, mSensorPropsMap.get(instance));
        }

        return null;
    }

    /**
     * Return the first pair of instance and sensor props, which does not correspond to the given
     * If instance is not available, then null is returned.
     */
    @Nullable
    public Pair<String, SensorProps[]> getSensorPairNotForInstance(String instance) {
        Optional<String> notAVirtualInstance = mSensorPropsMap.keySet().stream().filter(
                (instanceName) -> !instanceName.equals(instance)).findFirst();
        return notAVirtualInstance.map(this::getSensorPairForInstance).orElseGet(
                this::getSensorPair);
    }

    /**
     * Returns the first pair of instance and sensor props that has been added to the map.
     */
    @Nullable
    public Pair<String, SensorProps[]> getSensorPair() {
        Optional<String> optionalInstance = mSensorPropsMap.keySet().stream().findFirst();
        return optionalInstance.map(this::getSensorPairForInstance).orElse(null);

    }

    public boolean getResetLockoutRequiresChallenge() {
        return mResetLockoutRequiresChallenge;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByte((byte) (mResetLockoutRequiresChallenge ? 1 : 0));
        dest.writeMap(mSensorPropsMap);
    }
}
