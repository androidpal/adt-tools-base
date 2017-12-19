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

package com.android.tools.profiler.network;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profiler.GrpcUtils;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.proto.Common.*;
import com.android.tools.profiler.proto.NetworkProfiler;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsRequest.Type;
import com.android.tools.profiler.proto.NetworkProfiler.HttpDetailsResponse;
import com.android.tools.profiler.proto.Profiler.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class HttpUrlTest {
    @Parameterized.Parameters
    public static Collection<Boolean> data() {
        return Arrays.asList(new Boolean[] {false, true});
    }

    private static final String ACTIVITY_CLASS = "com.activity.network.HttpUrlActivity";

    private boolean myIsOPlusDevice;
    private PerfDriver myPerfDriver;
    private GrpcUtils myGrpc;
    private Session mySession;

    public HttpUrlTest(boolean isOPlusDevice) {
        myIsOPlusDevice = isOPlusDevice;
    }

    @Before
    public void setup() throws Exception {
        myPerfDriver = new PerfDriver(myIsOPlusDevice);
        myPerfDriver.start(ACTIVITY_CLASS);
        myGrpc = myPerfDriver.getGrpc();

        // Invoke beginSession to establish a session we can use to query data
        BeginSessionResponse response =
                myGrpc.getProfilerStub()
                        .beginSession(
                                BeginSessionRequest.newBuilder()
                                        .setDeviceId(1234)
                                        .setProcessId(myGrpc.getProcessId())
                                        .build());
        mySession = response.getSession();
    }

    @Test
    public void testHttpGet() throws IOException {
        final String getSuccess = "HttpUrlGet SUCCESS";
        myPerfDriver.getFakeAndroidDriver().triggerMethod(ACTIVITY_CLASS, "runGet");
        assertThat(myPerfDriver.getFakeAndroidDriver().waitForInput(getSuccess)).isTrue();

        final NetworkStubWrapper stubWrapper = new NetworkStubWrapper(myGrpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        final long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?activity=HttpUrlGet")).isTrue();
        stubWrapper.waitFor(
                () -> {
                    HttpDetailsResponse details =
                            stubWrapper.getHttpDetails(connectionId, Type.RESPONSE);
                    return details.getResponse().getFields().contains("HTTP/1.0 200 OK");
                });

        String payloadId = stubWrapper.getPayloadId(connectionId, Type.RESPONSE_BODY);
        assertThat(payloadId.isEmpty()).isFalse();
        BytesResponse bytesResponse =
                myGrpc.getProfilerStub().getBytes(BytesRequest.newBuilder().setId(payloadId).build());
        assertThat(bytesResponse.getContents().toStringUtf8()).isEqualTo(getSuccess);
    }

    @Test
    public void testHttpPost() throws IOException {
        final String postSuccess = "HttpUrlPost SUCCESS";
        myPerfDriver.getFakeAndroidDriver().triggerMethod(ACTIVITY_CLASS, "runPost");
        assertThat(myPerfDriver.getFakeAndroidDriver().waitForInput(postSuccess)).isTrue();

        NetworkStubWrapper stubWrapper = new NetworkStubWrapper(myGrpc.getNetworkStub());
        NetworkProfiler.HttpRangeResponse httpRangeResponse =
                stubWrapper.getAllHttpRange(mySession);
        assertThat(httpRangeResponse.getDataList().size()).isEqualTo(1);

        long connectionId = httpRangeResponse.getDataList().get(0).getConnId();
        HttpDetailsResponse requestDetails = stubWrapper.getHttpDetails(connectionId, Type.REQUEST);
        assertThat(requestDetails.getRequest().getUrl().contains("?activity=HttpUrlPost")).isTrue();

        String payloadId = stubWrapper.getPayloadId(connectionId, Type.REQUEST_BODY);
        assertThat(payloadId.isEmpty()).isFalse();
        BytesResponse bytesResponse =
                myGrpc.getProfilerStub().getBytes(BytesRequest.newBuilder().setId(payloadId).build
                        ());
        assertThat(bytesResponse.getContents().toStringUtf8()).isEqualTo("TestRequestBody");
    }
}
