/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.unfold;

import static android.view.WindowManager.TRANSIT_CHANGE;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_TRANSITIONS;

import android.app.ActivityManager;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback;
import com.android.wm.shell.transition.Transitions.TransitionHandler;
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider.UnfoldListener;
import com.android.wm.shell.unfold.animation.FullscreenUnfoldTaskAnimator;
import com.android.wm.shell.unfold.animation.SplitTaskUnfoldAnimator;
import com.android.wm.shell.unfold.animation.UnfoldTaskAnimator;
import com.android.wm.shell.util.TransitionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Transition handler that is responsible for animating app surfaces when unfolding of foldable
 * devices. It does not handle the folding animation, which is done in
 * {@link UnfoldAnimationController}.
 */
public class UnfoldTransitionHandler implements TransitionHandler, UnfoldListener {

    private final ShellUnfoldProgressProvider mUnfoldProgressProvider;
    private final Transitions mTransitions;
    private final Executor mExecutor;
    private final TransactionPool mTransactionPool;

    @Nullable
    private TransitionFinishCallback mFinishCallback;
    @Nullable
    private IBinder mTransition;

    private boolean mAnimationFinished = false;
    private final List<UnfoldTaskAnimator> mAnimators = new ArrayList<>();

    public UnfoldTransitionHandler(ShellInit shellInit,
            ShellUnfoldProgressProvider unfoldProgressProvider,
            FullscreenUnfoldTaskAnimator fullscreenUnfoldAnimator,
            SplitTaskUnfoldAnimator splitUnfoldTaskAnimator,
            TransactionPool transactionPool,
            Executor executor,
            Transitions transitions) {
        mUnfoldProgressProvider = unfoldProgressProvider;
        mTransactionPool = transactionPool;
        mExecutor = executor;
        mTransitions = transitions;

        mAnimators.add(splitUnfoldTaskAnimator);
        mAnimators.add(fullscreenUnfoldAnimator);
        // TODO(b/238217847): Temporarily add this check here until we can remove the dynamic
        //                    override for this controller from the base module
        if (unfoldProgressProvider != ShellUnfoldProgressProvider.NO_PROVIDER
                && Transitions.ENABLE_SHELL_TRANSITIONS) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    /**
     * Called when the transition handler is initialized.
     */
    public void onInit() {
        for (int i = 0; i < mAnimators.size(); i++) {
            mAnimators.get(i).init();
        }
        mTransitions.addHandler(this);
        mUnfoldProgressProvider.addListener(mExecutor, this);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull TransitionFinishCallback finishCallback) {
        if (transition != mTransition) return false;

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            animator.clearTasks();

            info.getChanges().forEach(change -> {
                if (change.getTaskInfo() != null) {
                    ProtoLog.v(WM_SHELL_TRANSITIONS,
                            "startAnimation, check taskInfo: %s, mode: %s, isApplicableTask: %s",
                            change.getTaskInfo(), TransitionInfo.modeToString(change.getMode()),
                            animator.isApplicableTask(change.getTaskInfo()));
                }
                if (change.getTaskInfo() != null && (change.getMode() == TRANSIT_CHANGE
                        || TransitionUtil.isOpeningType(change.getMode()))
                        && animator.isApplicableTask(change.getTaskInfo())) {
                    animator.onTaskAppeared(change.getTaskInfo(), change.getLeash());
                }
            });

            if (animator.hasActiveTasks()) {
                animator.prepareStartTransaction(startTransaction);
                animator.prepareFinishTransaction(finishTransaction);
                animator.start();
            }
        }

        startTransaction.apply();
        mFinishCallback = finishCallback;

        // Shell transition started when unfold animation has already finished,
        // finish shell transition immediately
        if (mAnimationFinished) {
            finishTransitionIfNeeded();
        }

        return true;
    }

    @Override
    public void onStateChangeProgress(float progress) {
        if (mTransition == null) return;

        SurfaceControl.Transaction transaction = null;

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);

            if (animator.hasActiveTasks()) {
                if (transaction == null) {
                    transaction = mTransactionPool.acquire();
                }

                animator.applyAnimationProgress(progress, transaction);
            }
        }

        if (transaction != null) {
            transaction.apply();
            mTransactionPool.release(transaction);
        }
    }

    @Override
    public void onStateChangeFinished() {
        mAnimationFinished = true;
        finishTransitionIfNeeded();
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull TransitionFinishCallback finishCallback) {
        if (info.getType() == TRANSIT_CHANGE) {
            // TODO (b/286928742) unfold transition handler should be part of mixed handler to
            //  handle merges better.
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                if (taskInfo != null
                        && taskInfo.configuration.windowConfiguration.isAlwaysOnTop()) {
                    // Tasks that are always on top (e.g. bubbles), will handle their own transition
                    // as they are on top of everything else. So skip merging transitions here.
                    return;
                }
            }
            // Apply changes happening during the unfold animation immediately
            t.apply();
            finishCallback.onTransitionFinished(null);
        }
    }

    /** Whether `request` contains an unfold action. */
    public boolean hasUnfold(@NonNull TransitionRequestInfo request) {
        return (request.getType() == TRANSIT_CHANGE
                && request.getDisplayChange() != null
                && request.getDisplayChange().isPhysicalDisplayChanged());
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (hasUnfold(request)) {
            mTransition = transition;
            return new WindowContainerTransaction();
        }
        return null;
    }

    public boolean willHandleTransition() {
        return mTransition != null;
    }

    @Override
    public void onFoldStateChanged(boolean isFolded) {
        if (isFolded) {
            mAnimationFinished = false;
        }
    }

    private void finishTransitionIfNeeded() {
        if (mFinishCallback == null) return;

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            animator.clearTasks();
            animator.stop();
        }

        mFinishCallback.onTransitionFinished(null);
        mFinishCallback = null;
        mTransition = null;
    }
}
