/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.splitscreen;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.os.Bundle;
import android.os.UserHandle;
import com.android.internal.logging.InstanceId;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationTarget;
import android.window.RemoteTransition;

import com.android.wm.shell.splitscreen.ISplitScreenListener;

/**
 * Interface that is exposed to remote callers to manipulate the splitscreen feature.
 */
interface ISplitScreen {

    /**
     * Registers a split screen listener.
     */
    oneway void registerSplitScreenListener(in ISplitScreenListener listener) = 1;

    /**
     * Unregisters a split screen listener.
     */
    oneway void unregisterSplitScreenListener(in ISplitScreenListener listener) = 2;

    /**
     * Removes a task from the side stage.
     */
    oneway void removeFromSideStage(int taskId) = 4;

    /**
     * Removes the split-screen stages and leaving indicated task to top. Passing INVALID_TASK_ID
     * to indicate leaving no top task after leaving split-screen.
     */
    oneway void exitSplitScreen(int toTopTaskId) = 5;

    /**
     * @param exitSplitScreenOnHide if to exit split-screen if both stages are not visible.
     */
    oneway void exitSplitScreenOnHide(boolean exitSplitScreenOnHide) = 6;

    /**
     * Starts a task in a stage.
     */
    oneway void startTask(int taskId, int position, in Bundle options) = 7;

    /**
     * Starts a shortcut in a stage.
     */
    oneway void startShortcut(String packageName, String shortcutId, int position,
            in Bundle options, in UserHandle user, in InstanceId instanceId) = 8;

    /**
     * Starts an activity in a stage.
     */
    oneway void startIntent(in PendingIntent intent, in Intent fillInIntent, int position,
            in Bundle options, in InstanceId instanceId) = 9;

    /**
     * Starts tasks simultaneously in one transition.
     */
    oneway void startTasks(int mainTaskId, in Bundle mainOptions, int sideTaskId,
            in Bundle sideOptions, int sidePosition, float splitRatio,
            in RemoteTransition remoteTransition, in InstanceId instanceId) = 10;

    /**
     * Version of startTasks using legacy transition system.
     */
    oneway void startTasksWithLegacyTransition(int mainTaskId, in Bundle mainOptions,
            int sideTaskId, in Bundle sideOptions, int sidePosition,
            float splitRatio, in RemoteAnimationAdapter adapter, in InstanceId instanceId) = 11;

    /**
     * Starts a pair of intent and task using legacy transition system.
     */
    oneway void startIntentAndTaskWithLegacyTransition(in PendingIntent pendingIntent,
            in Intent fillInIntent, int taskId, in Bundle mainOptions,in Bundle sideOptions,
            int sidePosition, float splitRatio, in RemoteAnimationAdapter adapter,
            in InstanceId instanceId) = 12;

    /**
     * Blocking call that notifies and gets additional split-screen targets when entering
     * recents (for example: the dividerBar).
     * @param appTargets apps that will be re-parented to display area
     */
    RemoteAnimationTarget[] onGoingToRecentsLegacy(in RemoteAnimationTarget[] appTargets) = 13;

    /**
     * Blocking call that notifies and gets additional split-screen targets when entering
     * recents (for example: the dividerBar). Different than the method above in that this one
     * does not expect split to currently be running.
     */
    RemoteAnimationTarget[] onStartingSplitLegacy(in RemoteAnimationTarget[] appTargets) = 14;

    /**
     * Starts a pair of shortcut and task using legacy transition system.
     */
    oneway void startShortcutAndTaskWithLegacyTransition(in ShortcutInfo shortcutInfo, int taskId,
            in Bundle mainOptions, in Bundle sideOptions, int sidePosition, float splitRatio,
            in RemoteAnimationAdapter adapter, in InstanceId instanceId) = 15;
}
