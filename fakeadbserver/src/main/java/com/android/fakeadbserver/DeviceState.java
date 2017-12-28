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

package com.android.fakeadbserver;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory;
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHub;
import com.android.fakeadbserver.statechangehubs.StateChangeQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceState {

    protected final ClientStateChangeHub mClientStateChangeHub = new ClientStateChangeHub();
    protected final Map<String, byte[]> mPathsToFiles = new HashMap<>();
    protected final List<String> mLogcatMessages = new ArrayList<>();
    protected final Map<Integer, ClientState> mClients = new HashMap<>();
    @NonNull
    protected FakeAdbServer mServer;
    @NonNull
    protected HostConnectionType mHostConnectionType;
    @NonNull
    protected String mDeviceId;
    @NonNull
    protected String mManufacturer;
    @NonNull
    protected String mModel;
    @NonNull
    protected String mBuildVersionRelease;
    @NonNull
    protected String mBuildVersionSdk;
    @NonNull protected DeviceStatus mDeviceStatus;

    DeviceState(
            @NonNull FakeAdbServer server,
            @NonNull String deviceId,
            @NonNull String manufacturer,
            @NonNull String model,
            @NonNull String release,
            @NonNull String sdk,
            @NonNull HostConnectionType hostConnectionType) {
        mServer = server;
        mDeviceId = deviceId;
        mManufacturer = manufacturer;
        mModel = model;
        mBuildVersionRelease = release;
        mBuildVersionSdk = sdk;
        mHostConnectionType = hostConnectionType;
        mDeviceStatus = DeviceStatus.OFFLINE;
    }

    public void stop() {
        mClientStateChangeHub.stop();
    }

    @NonNull
    public String getDeviceId() {
        return mDeviceId;
    }

    @NonNull
    public String getManufacturer() {
        return mManufacturer;
    }

    @NonNull
    public String getModel() {
        return mModel;
    }

    @NonNull
    public String getBuildVersionRelease() {
        return mBuildVersionRelease;
    }

    @NonNull
    public String getBuildVersionSdk() {
        return mBuildVersionSdk;
    }

    @NonNull
    public DeviceStatus getDeviceStatus() {
        return mDeviceStatus;
    }

    public void setDeviceStatus(@NonNull DeviceStatus status) {
        mDeviceStatus = status;
        mServer.getDeviceChangeHub().deviceStatusChanged(this, status);
    }

    @NonNull
    public ClientStateChangeHub getClientChangeHub() {
        return mClientStateChangeHub;
    }

    public void addLogcatMessage(@NonNull String message) {
        synchronized (mLogcatMessages) {
            mLogcatMessages.add(message);
            mClientStateChangeHub.logcatMessageAdded(message);
        }
    }

    @Nullable
    public LogcatChangeHandlerSubscriptionResult subscribeLogcatChangeHandler(
            @NonNull ClientStateChangeHandlerFactory handlerFactory) {
        synchronized (mLogcatMessages) {
            StateChangeQueue queue = getClientChangeHub().subscribe(handlerFactory);
            if (queue == null) {
                return null;
            }

            return new LogcatChangeHandlerSubscriptionResult(queue,
                    new ArrayList<>(mLogcatMessages));
        }
    }

    public void createFile(@NonNull String filepath, @NonNull byte[] data) {
        synchronized (mPathsToFiles) {
            mPathsToFiles.put(filepath, data);
        }
    }

    public byte[] getFile(@NonNull String filepath) {
        synchronized (mPathsToFiles) {
            return mPathsToFiles.get(filepath);
        }
    }

    @NonNull
    public ClientState startClient(
            int pid, int uid, @NonNull String packageName, boolean isWaiting) {
        synchronized (mClients) {
            ClientState clientState = new ClientState(pid, uid, packageName, isWaiting);
            mClients.put(pid, clientState);
            mClientStateChangeHub.clientListChanged(getClientListCopy());
            return clientState;
        }
    }

    public void stopClient(int pid) {
        synchronized (mClients) {
            mClients.remove(pid);
            mClientStateChangeHub.clientListChanged(getClientListCopy());
        }
    }

    @Nullable
    public ClientState getClient(int pid) {
        synchronized (mClients) {
            return mClients.get(pid);
        }
    }

    @NonNull
    public HostConnectionType getHostConnectionType() {
        return mHostConnectionType;
    }

    @NonNull
    private List<ClientState> getClientListCopy() {
        return new ArrayList<>(mClients.values());
    }

    /** The state of a device. */
    public enum DeviceStatus {
        BOOTLOADER("bootloader"), //$NON-NLS-1$
        OFFLINE("offline"), //$NON-NLS-1$
        ONLINE("device"), //$NON-NLS-1$
        RECOVERY("recovery"), //$NON-NLS-1$
        /** Device is in "sideload" state either through `adb sideload` or recovery menu */
        SIDELOAD("sideload"), //$NON-NLS-1$
        UNAUTHORIZED("unauthorized"), //$NON-NLS-1$
        DISCONNECTED("disconnected"), //$NON-NLS-1$
        ;

        private String mState;

        DeviceStatus(String state) {
            mState = state;
        }

        /**
         * Returns a {@link DeviceStatus} from the string returned by <code>adb devices</code>.
         *
         * @param state the device state.
         * @return a {@link DeviceStatus} object or <code>null</code> if the state is unknown.
         */
        @Nullable
        public static DeviceStatus getState(String state) {
            for (DeviceStatus deviceStatus : values()) {
                if (deviceStatus.mState.equals(state)) {
                    return deviceStatus;
                }
            }
            return null;
        }

        public String getState() {
            return mState;
        }
    }

    public enum HostConnectionType {
        USB,
        LOCAL
    }

    /**
     * This class represents the result of calling {@link #subscribeLogcatChangeHandler(ClientStateChangeHandlerFactory)}.
     * This is needed to synchronize between adding the listener and getting the correct lines from
     * the logcat buffer.
     */
    public static final class LogcatChangeHandlerSubscriptionResult {

        @NonNull
        public final StateChangeQueue mQueue;

        @NonNull
        public final List<String> mLogcatContents;

        public LogcatChangeHandlerSubscriptionResult(@NonNull StateChangeQueue queue,
                @NonNull List<String> logcatContents) {
            mQueue = queue;
            mLogcatContents = logcatContents;
        }
    }
}