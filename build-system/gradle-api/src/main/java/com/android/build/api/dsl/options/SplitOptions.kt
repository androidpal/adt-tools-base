/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.api.dsl.options

import com.android.build.api.dsl.Initializable
import org.gradle.api.Incubating

/** Base data representing how an FULL_APK should be split for a given dimension (density, abi).  */
@Incubating
interface SplitOptions : Initializable<SplitOptions> {

    /** Whether to split in this dimension.  */
    var isEnabled: Boolean

    /** excludes some values  */
    fun exclude(vararg excludes: String)

    /** includes some values  */
    fun include(vararg includes: String)

    /**
     * Resets the list of included split configuration.
     *
     *
     * Use this before calling include, in order to manually configure the list of configuration
     * to split on, rather than excluding from the default list.
     */
    fun reset()

    /**
     * Returns a list of all applicable filters for this dimension.
     *
     *
     * The list can return null, indicating that the no-filter option must also be used.
     *
     * @return the filters to use.
     */
    val applicableFilters: Set<String>

    // -- DEPRECATED
}
