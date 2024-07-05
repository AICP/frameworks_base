/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.platform.test.ravenwood;

import static android.platform.test.ravenwood.RavenwoodRule.ENABLE_PROBE_IGNORED;
import static android.platform.test.ravenwood.RavenwoodRule.IS_ON_RAVENWOOD;
import static android.platform.test.ravenwood.RavenwoodRule.shouldEnableOnDevice;
import static android.platform.test.ravenwood.RavenwoodRule.shouldEnableOnRavenwood;
import static android.platform.test.ravenwood.RavenwoodRule.shouldStillIgnoreInProbeIgnoreMode;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnabledOnRavenwood;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@code @ClassRule} that respects Ravenwood-specific class annotations. This rule has no effect
 * when tests are run on non-Ravenwood test environments.
 *
 * By default, all tests are executed on Ravenwood, but annotations such as
 * {@link DisabledOnRavenwood} and {@link EnabledOnRavenwood} can be used at both the method
 * and class level to "ignore" tests that may not be ready.
 */
public class RavenwoodClassRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        // No special treatment when running outside Ravenwood; run tests as-is
        if (!IS_ON_RAVENWOOD) {
            Assume.assumeTrue(shouldEnableOnDevice(description));
            return base;
        }

        if (ENABLE_PROBE_IGNORED) {
            Assume.assumeFalse(shouldStillIgnoreInProbeIgnoreMode(description));
            // Pass through to possible underlying RavenwoodRule for both environment
            // configuration and handling method-level annotations
            return base;
        } else {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Assume.assumeTrue(shouldEnableOnRavenwood(description));
                    // Pass through to possible underlying RavenwoodRule for both environment
                    // configuration and handling method-level annotations
                    base.evaluate();
                }
            };
        }
    }
}
