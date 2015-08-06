/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.rpclib.schema;

import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;

import java.io.IOException;

public final class Map implements BinaryObject {
    //<<<Start:Java.ClassBody:1>>>
    String mAlias;
    Type mKeyType;
    Type mValueType;

    // Constructs a default-initialized {@link Map}.
    public Map() {}


    public String getAlias() {
        return mAlias;
    }

    public Map setAlias(String v) {
        mAlias = v;
        return this;
    }

    public Type getKeyType() {
        return mKeyType;
    }

    public Map setKeyType(Type v) {
        mKeyType = v;
        return this;
    }

    public Type getValueType() {
        return mValueType;
    }

    public Map setValueType(Type v) {
        mValueType = v;
        return this;
    }

    @Override @NotNull
    public BinaryClass klass() { return Klass.INSTANCE; }

    private static final byte[] IDBytes = {32, -52, -102, 82, -34, 117, 95, 22, 43, 52, -117, 51, -50, 24, 103, -99, -123, 64, -77, 100, };
    public static final BinaryID ID = new BinaryID(IDBytes);

    static {
        Namespace.register(ID, Klass.INSTANCE);
    }
    public static void register() {}
    //<<<End:Java.ClassBody:1>>>
    public enum Klass implements BinaryClass {
        //<<<Start:Java.KlassBody:2>>>
        INSTANCE;

        @Override @NotNull
        public BinaryID id() { return ID; }

        @Override @NotNull
        public BinaryObject create() { return new Map(); }

        @Override
        public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
            Map o = (Map)obj;
            e.string(o.mAlias);
            e.object(o.mKeyType.unwrap());
            e.object(o.mValueType.unwrap());
        }

        @Override
        public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
            Map o = (Map)obj;
            o.mAlias = d.string();
            o.mKeyType = Type.wrap(d.object());
            o.mValueType = Type.wrap(d.object());
        }
        //<<<End:Java.KlassBody:2>>>
    }
}
