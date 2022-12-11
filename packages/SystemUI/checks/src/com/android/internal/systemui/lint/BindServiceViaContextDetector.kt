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

package com.android.internal.systemui.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

@Suppress("UnstableApiUsage")
class BindServiceViaContextDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("bindService", "bindServiceAsUser", "unbindService")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInSubClassOf(method, "android.content.Context")) {
            context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(node),
                    "Binding or unbinding services are synchronous calls, please make " +
                            "sure you're on a @Background Executor."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "BindServiceViaContextDetector",
                briefDescription = "Service bound/unbound via Context, please make sure " +
                        "you're on a background thread.",
                explanation =
                "Binding or unbinding services are synchronous calls to ActivityManager, " +
                        "they usually take multiple milliseconds to complete and will make" +
                        "the caller drop frames. Make sure you're on a @Background Executor.",
                category = Category.PERFORMANCE,
                priority = 8,
                severity = Severity.WARNING,
                implementation =
                Implementation(BindServiceViaContextDetector::class.java, Scope.JAVA_FILE_SCOPE)
            )
    }
}
