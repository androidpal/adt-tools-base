/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.support.network.okhttp.reflection.okhttp3;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

public class Headers$ {
    public final Object obj;

    public Headers$(Object headers) {
        this.obj = headers;
    }

    public Set<String> names()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        //noinspection unchecked
        return (Set<String>) obj.getClass().getDeclaredMethod("names").invoke(obj);
    }

    public List<String> values(String name)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        //noinspection unchecked
        return (List<String>)
                obj.getClass().getDeclaredMethod("values", String.class).invoke(obj, name);
    }
}