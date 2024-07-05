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
package com.android.credentialmanager.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.material.ChipColors
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.android.credentialmanager.R
import com.android.credentialmanager.model.get.AuthenticationEntryInfo
import com.android.credentialmanager.ui.components.CredentialsScreenChip.TOPPADDING

/* Used as credential suggestion or user action chip. */
@Composable
fun CredentialsScreenChip(
    label: String,
    onClick: () -> Unit,
    secondaryLabel: String? = null,
    icon: Drawable? = null,
    isAuthenticationEntryLocked: Boolean = false,
    modifier: Modifier = Modifier,
    colors: ChipColors = ChipDefaults.secondaryChipColors(),
) {
    val labelParam: (@Composable RowScope.() -> Unit) =
        {
            Text(
                text = label,
                overflow = TextOverflow.Ellipsis,
                maxLines = if (secondaryLabel != null) 1 else 2,
            )
        }

    val secondaryLabelParam: (@Composable RowScope.() -> Unit)? =
        secondaryLabel?.let {
            {
                Row {
                    Text(
                        text = secondaryLabel,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )

                    if (isAuthenticationEntryLocked)
                        // TODO(b/324465527) change this to lock icon and correct size once figma mocks are
                        // updated
                        Icon(
                            bitmap = checkNotNull(icon?.toBitmap()?.asImageBitmap()),
                            // Decorative purpose only.
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                }
            }
        }

    val iconParam: (@Composable BoxScope.() -> Unit)? =
        icon?.toBitmap()?.asImageBitmap()?.let {
            {
                Icon(
                    bitmap = it,
                    // Decorative purpose only.
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.Unspecified
                )
            }
        }

    Chip(
        label = labelParam,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        secondaryLabel = secondaryLabelParam,
        icon = iconParam,
        colors = colors,
        enabled = true,
    )
}

@Preview
@Composable
fun CredentialsScreenChipPreview() {
    CredentialsScreenChip(
        label = "Elisa Beckett",
        onClick = { },
        secondaryLabel = "beckett_bakery@gmail.com",
        icon = null,
        modifier = Modifier
            .clipToBounds()
            .padding(top = 2.dp)
    )
}

@Composable
fun SignInOptionsChip(onClick: () -> Unit) {
    CredentialsScreenChip(
        label = stringResource(R.string.dialog_sign_in_options_button),
        onClick = onClick,
        modifier = Modifier
            .padding(top = TOPPADDING)
    )
}

@Preview
@Composable
fun SignInOptionsChipPreview() {
    SignInOptionsChip({})
}

@Composable
fun ContinueChip(onClick: () -> Unit) {
    CredentialsScreenChip(
        label = stringResource(R.string.dialog_continue_button),
        onClick = onClick,
        modifier = Modifier
            .padding(top = TOPPADDING),
        colors = ChipDefaults.primaryChipColors(),
    )
}

@Preview
@Composable
fun ContinueChipPreview() {
    ContinueChip({})
}

@Composable
fun DismissChip(onClick: () -> Unit) {
    CredentialsScreenChip(
        label = stringResource(R.string.dialog_dismiss_button),
        onClick = onClick,
        modifier = Modifier
            .padding(top = TOPPADDING),
    )
}

@Composable
fun SignInOnPhoneChip(onClick: () -> Unit) {
    CredentialsScreenChip(
        label = stringResource(R.string.sign_in_on_phone_button),
        onClick = onClick,
        modifier = Modifier
            .padding(top = TOPPADDING),
    )
}

@Composable
fun LockedProviderChip(
    authenticationEntryInfo: AuthenticationEntryInfo,
    onClick: () -> Unit
) {
    val secondaryLabel = stringResource(
        if (authenticationEntryInfo.isUnlockedAndEmpty)
            R.string.locked_credential_entry_label_subtext_no_sign_in
        else R.string.locked_credential_entry_label_subtext_tap_to_unlock
    )

    CredentialsScreenChip(
        label = authenticationEntryInfo.title,
        icon = authenticationEntryInfo.icon,
        secondaryLabel = secondaryLabel,
        isAuthenticationEntryLocked = !authenticationEntryInfo.isUnlockedAndEmpty,
        onClick = onClick,
        modifier = Modifier.padding(top = TOPPADDING),
    )
}

@Preview
@Composable
fun DismissChipPreview() {
    DismissChip({})
}

private object CredentialsScreenChip {
    val TOPPADDING = 8.dp
}

