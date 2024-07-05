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

package com.android.systemui.qs.tiles.base.actions

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.view.View
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.ActivityStarter
import javax.inject.Inject

/**
 * Provides a shortcut to start an activity from [QSTileUserActionInteractor]. It supports keyguard
 * dismissing and tile from-view animations.
 */
interface QSTileIntentUserInputHandler {

    fun handle(view: View?, intent: Intent)

    /** @param requestLaunchingDefaultActivity used in case !pendingIndent.isActivity */
    fun handle(
        view: View?,
        pendingIntent: PendingIntent,
        requestLaunchingDefaultActivity: Boolean = false
    )
}

@SysUISingleton
class QSTileIntentUserInputHandlerImpl
@Inject
constructor(
    private val activityStarter: ActivityStarter,
    private val packageManager: PackageManager,
    private val userHandle: UserHandle,
) : QSTileIntentUserInputHandler {

    override fun handle(view: View?, intent: Intent) {
        val animationController: ActivityTransitionAnimator.Controller? =
            view?.let {
                ActivityTransitionAnimator.Controller.fromView(
                    it,
                    InteractionJankMonitor.CUJ_SHADE_APP_LAUNCH_FROM_QS_TILE,
                )
            }
        activityStarter.postStartActivityDismissingKeyguard(intent, 0, animationController)
    }

    // TODO(b/249804373): make sure to allow showing activities over the lockscreen. See b/292112939
    override fun handle(
        view: View?,
        pendingIntent: PendingIntent,
        requestLaunchingDefaultActivity: Boolean
    ) {
        if (pendingIntent.isActivity) {
            val animationController: ActivityTransitionAnimator.Controller? =
                view?.let {
                    ActivityTransitionAnimator.Controller.fromView(
                        it,
                        InteractionJankMonitor.CUJ_SHADE_APP_LAUNCH_FROM_QS_TILE,
                    )
                }
            activityStarter.postStartActivityDismissingKeyguard(pendingIntent, animationController)
        } else if (requestLaunchingDefaultActivity) {
            val intent =
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(pendingIntent.creatorPackage)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
            val intents =
                packageManager.queryIntentActivitiesAsUser(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L),
                    userHandle.identifier
                )
            intents
                .firstOrNull { it.activityInfo.exported }
                ?.let { resolved ->
                    intent.setPackage(null)
                    intent.setComponent(resolved.activityInfo.componentName)
                    handle(view, intent)
                }
        }
    }
}
