/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.collection.listbuilder

import com.android.systemui.statusbar.notification.collection.PipelineDumpable
import com.android.systemui.statusbar.notification.collection.PipelineDumper
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifComparator
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifSectioner
import com.android.systemui.statusbar.notification.collection.render.NodeController
import com.android.systemui.statusbar.notification.stack.PriorityBucket

data class NotifSection(
    val sectioner: NotifSectioner,
    val index: Int
) : PipelineDumpable {
    @PriorityBucket
    val bucket: Int = sectioner.bucket
    val label: String = "$index:$bucket:${sectioner.name}"
    val headerController: NodeController? = sectioner.headerNodeController
    val comparator: NotifComparator? = sectioner.comparator

    override fun dumpPipeline(d: PipelineDumper) = with(d) {
        dump("index", index)
        dump("bucket", bucket)
        dump("sectioner", sectioner)
        dump("headerController", headerController)
        dump("comparator", comparator)
    }
}
