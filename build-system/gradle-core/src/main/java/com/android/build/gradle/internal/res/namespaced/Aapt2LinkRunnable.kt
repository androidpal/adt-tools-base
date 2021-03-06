/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.res.namespaced

import com.android.build.gradle.internal.LoggerWrapper
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.repository.Revision
import org.gradle.api.logging.Logging
import java.io.Serializable
import javax.inject.Inject

class Aapt2LinkRunnable @Inject constructor(
        private val params: Params) : Runnable {

    override fun run() {
        val logger = LoggerWrapper(Logging.getLogger(this::class.java))
        useAaptDaemon(params.revision) { daemon ->
            daemon.link(params.request, logger)
        }
    }

    class Params(
            val revision: Revision,
            val request: AaptPackageConfig) : Serializable
}
