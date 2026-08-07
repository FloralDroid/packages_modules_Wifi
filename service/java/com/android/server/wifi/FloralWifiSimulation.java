/*
 * Copyright (C) 2026 The FloralDroid Project
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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.net.module.util.Inet4AddressUtils;

import floral.device.wifi.IWifiState;
import floral.device.wifi.WifiAccessPoint;
import floral.device.wifi.WifiControlResult;
import floral.device.wifi.WifiProfile;
import floral.device.wifi.WifiSnapshot;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Provides an Android Wi-Fi connection backed by the existing default network.
 *
 * <p>This class never creates an interface, changes routes, or starts a supplicant. The real
 * network remains owned by ConnectivityService (normally redroid's {@code eth0}); the shared
 * vendor model supplies all simulated identity and link state.</p>
 */
final class FloralWifiSimulation {
    private static final String TAG = "FloralWifiSimulation";
    private static final int SIMULATED_NETWORK_ID = 0;

    static final int CONNECT_NOT_HANDLED = 0;
    static final int CONNECT_SUCCEEDED = 1;
    static final int CONNECT_FAILED = 2;

    interface BinderLookup {
        @Nullable IBinder getBinder();
    }

    interface StateProvider {
        @Nullable WifiProfile getProfile();
        @Nullable WifiSnapshot getSnapshot();
        @NonNull List<WifiAccessPoint> getAccessPoints();
        @Nullable WifiControlResult setEnabledFromSystem(boolean enabled);
        @Nullable WifiControlResult connectFromSystem(String ssid, String bssid,
                int security, String credential);
        @Nullable WifiControlResult disconnectFromSystem();
    }

    private static final class BinderStateProvider implements StateProvider {
        private final BinderLookup mBinderLookup;
        private IWifiState mService;

        BinderStateProvider(@NonNull BinderLookup binderLookup) {
            mBinderLookup = binderLookup;
        }

        @Nullable
        private synchronized IWifiState getService() {
            if (mService != null && mService.asBinder().isBinderAlive()) {
                return mService;
            }
            mService = null;
            IBinder binder = mBinderLookup.getBinder();
            if (binder != null) {
                mService = IWifiState.Stub.asInterface(binder);
            }
            return mService;
        }

        private synchronized void forgetService() {
            mService = null;
        }

        @Override
        @Nullable
        public WifiProfile getProfile() {
            IWifiState service = getService();
            if (service == null) return null;
            try {
                return service.getProfile();
            } catch (RemoteException | RuntimeException exception) {
                Log.w(TAG, "Unable to read Floral Wi-Fi profile", exception);
                forgetService();
                return null;
            }
        }

        @Override
        @Nullable
        public WifiSnapshot getSnapshot() {
            IWifiState service = getService();
            if (service == null) return null;
            try {
                return service.getSnapshot();
            } catch (RemoteException | RuntimeException exception) {
                Log.w(TAG, "Unable to read Floral Wi-Fi snapshot", exception);
                forgetService();
                return null;
            }
        }

        @Override
        @NonNull
        public List<WifiAccessPoint> getAccessPoints() {
            IWifiState service = getService();
            if (service == null) return Collections.emptyList();
            try {
                WifiAccessPoint[] accessPoints = service.getAccessPoints();
                if (accessPoints == null || accessPoints.length == 0) {
                    return Collections.emptyList();
                }
                List<WifiAccessPoint> result = new ArrayList<>(accessPoints.length);
                Collections.addAll(result, accessPoints);
                return result;
            } catch (RemoteException | RuntimeException exception) {
                Log.w(TAG, "Unable to read Floral Wi-Fi access points", exception);
                forgetService();
                return Collections.emptyList();
            }
        }

        @Override
        @Nullable
        public WifiControlResult setEnabledFromSystem(boolean enabled) {
            IWifiState service = getService();
            if (service == null) return null;
            try {
                return service.setEnabledFromSystem(enabled);
            } catch (RemoteException | RuntimeException exception) {
                Log.w(TAG, "Unable to set Floral Wi-Fi state", exception);
                forgetService();
                return null;
            }
        }

        @Override
        @Nullable
        public WifiControlResult connectFromSystem(String ssid, String bssid,
                int security, String credential) {
            IWifiState service = getService();
            if (service == null) return null;
            try {
                return service.connectFromSystem(ssid, bssid, security, credential);
            } catch (RemoteException | RuntimeException exception) {
                Log.w(TAG, "Unable to connect Floral Wi-Fi", exception);
                forgetService();
                return null;
            }
        }

        @Override
        @Nullable
        public WifiControlResult disconnectFromSystem() {
            IWifiState service = getService();
            if (service == null) return null;
            try {
                return service.disconnectFromSystem();
            } catch (RemoteException | RuntimeException exception) {
                Log.w(TAG, "Unable to disconnect Floral Wi-Fi", exception);
                forgetService();
                return null;
            }
        }
    }

    private final Context mContext;
    private final StateProvider mStateProvider;

    FloralWifiSimulation(@NonNull Context context, @NonNull BinderLookup binderLookup) {
        this(context, new BinderStateProvider(binderLookup));
    }

    FloralWifiSimulation(@NonNull Context context, @NonNull StateProvider stateProvider) {
        mContext = context;
        mStateProvider = stateProvider;
    }

    boolean isConfigured() {
        return !mStateProvider.getAccessPoints().isEmpty();
    }

    boolean isEnabled() {
        WifiSnapshot snapshot = mStateProvider.getSnapshot();
        return snapshot != null && snapshot.enabled;
    }

    boolean isConnected() {
        WifiSnapshot snapshot = mStateProvider.getSnapshot();
        return snapshot != null && snapshot.enabled && snapshot.connectedAccessPointId != 0;
    }

    boolean setEnabledFromSystem(boolean enabled) {
        boolean wasEnabled = isEnabled();
        WifiControlResult result = mStateProvider.setEnabledFromSystem(enabled);
        if (!isApplied(result)) return false;
        sendWifiStateChangedBroadcast(wasEnabled, enabled);
        if (!enabled) {
            sendNetworkStateChangedBroadcast(NetworkInfo.DetailedState.DISCONNECTED);
        }
        return true;
    }

    int connectFromSystem(@Nullable WifiConfiguration configuration) {
        if (configuration == null || TextUtils.isEmpty(configuration.SSID)) {
            return CONNECT_NOT_HANDLED;
        }
        String ssid = removeDoubleQuotes(configuration.SSID);
        String bssid = configuration.BSSID == null
                ? "" : configuration.BSSID.toLowerCase(Locale.ROOT);
        int security = getModelSecurity(configuration);
        List<WifiAccessPoint> accessPoints = mStateProvider.getAccessPoints();
        boolean matches = false;
        for (WifiAccessPoint accessPoint : accessPoints) {
            if (ssid.equals(accessPoint.ssid)
                    && (TextUtils.isEmpty(bssid) || bssid.equalsIgnoreCase(accessPoint.bssid))) {
                matches = true;
                break;
            }
        }
        if (!matches) return CONNECT_NOT_HANDLED;
        if (security < 0) return CONNECT_FAILED;

        String credential = security == 0 || configuration.preSharedKey == null
                ? "" : removeDoubleQuotes(configuration.preSharedKey);
        WifiControlResult result = mStateProvider.connectFromSystem(
                ssid, bssid, security, credential);
        if (!isApplied(result)) return CONNECT_FAILED;
        sendNetworkStateChangedBroadcast(NetworkInfo.DetailedState.CONNECTED);
        return CONNECT_SUCCEEDED;
    }

    boolean disconnectFromSystem() {
        WifiControlResult result = mStateProvider.disconnectFromSystem();
        if (!isApplied(result)) return false;
        sendNetworkStateChangedBroadcast(NetworkInfo.DetailedState.DISCONNECTED);
        return true;
    }

    /** Creates a fresh value so caller-specific redaction can be applied safely. */
    @NonNull
    WifiInfo createConnectionInfo() {
        WifiSnapshot snapshot = mStateProvider.getSnapshot();
        WifiProfile profile = mStateProvider.getProfile();
        if (snapshot == null || !snapshot.enabled || snapshot.connectedAccessPointId == 0) {
            WifiInfo disconnected = new WifiInfo();
            if (profile != null) disconnected.setMacAddress(profile.stationMacAddress);
            disconnected.setSupplicantState(SupplicantState.DISCONNECTED);
            return disconnected;
        }

        byte[] ssidBytes = snapshot.ssid.getBytes(StandardCharsets.UTF_8);
        WifiInfo info = new WifiInfo.Builder()
                .setSsid(ssidBytes)
                .setBssid(snapshot.bssid)
                .setRssi(snapshot.rssiDbm)
                .setNetworkId(SIMULATED_NETWORK_ID)
                .setCurrentSecurityType(toFrameworkSecurity(snapshot.security))
                .build();
        if (profile != null) info.setMacAddress(profile.stationMacAddress);
        info.setFrequency(snapshot.frequencyMhz);
        info.setLinkSpeed(snapshot.linkSpeedMbps);
        info.setTxLinkSpeedMbps(snapshot.linkSpeedMbps);
        info.setRxLinkSpeedMbps(snapshot.linkSpeedMbps);
        info.setSupplicantState(SupplicantState.COMPLETED);
        info.setIsPrimary(true);

        LinkProperties linkProperties = getActiveLinkProperties();
        if (linkProperties != null) {
            for (LinkAddress address : linkProperties.getLinkAddresses()) {
                if (address.getAddress() instanceof Inet4Address) {
                    info.setInetAddress(address.getAddress());
                    break;
                }
            }
        }
        return info;
    }

    /** Returns every configured simulated access point as the latest scan result. */
    @NonNull
    List<ScanResult> createScanResults() {
        List<WifiAccessPoint> accessPoints = mStateProvider.getAccessPoints();
        if (accessPoints.isEmpty()) return Collections.emptyList();
        List<ScanResult> results = new ArrayList<>(accessPoints.size());
        long timestampUs = SystemClock.elapsedRealtimeNanos() / 1000;
        for (WifiAccessPoint accessPoint : accessPoints) {
            byte[] ssidBytes = accessPoint.ssid.getBytes(StandardCharsets.UTF_8);
            results.add(new ScanResult(WifiSsid.createFromByteArray(ssidBytes),
                    accessPoint.ssid, accessPoint.bssid, 0, 0,
                    getScanCapabilities(accessPoint.security), accessPoint.rssiDbm,
                    accessPoint.frequencyMhz, timestampUs, 0, 0, 0, 0, 0, false));
        }
        return results;
    }

    /** Builds the deprecated DHCP view from the real default network without changing it. */
    @NonNull
    DhcpInfo createDhcpInfo() {
        DhcpInfo info = new DhcpInfo();
        LinkProperties linkProperties = getActiveLinkProperties();
        if (linkProperties == null) return info;

        for (LinkAddress address : linkProperties.getLinkAddresses()) {
            if (address.getAddress() instanceof Inet4Address) {
                info.ipAddress = Inet4AddressUtils.inet4AddressToIntHTL(
                        (Inet4Address) address.getAddress());
                info.netmask = prefixLengthToNetmaskIntHTL(address.getPrefixLength());
                break;
            }
        }
        for (RouteInfo route : linkProperties.getRoutes()) {
            InetAddress gateway = route.getGateway();
            if (route.isDefaultRoute() && gateway instanceof Inet4Address) {
                info.gateway = Inet4AddressUtils.inet4AddressToIntHTL((Inet4Address) gateway);
                info.serverAddress = info.gateway;
                break;
            }
        }
        int dnsIndex = 0;
        for (InetAddress dns : linkProperties.getDnsServers()) {
            if (!(dns instanceof Inet4Address)) continue;
            if (dnsIndex++ == 0) {
                info.dns1 = Inet4AddressUtils.inet4AddressToIntHTL((Inet4Address) dns);
            } else {
                info.dns2 = Inet4AddressUtils.inet4AddressToIntHTL((Inet4Address) dns);
                break;
            }
        }
        return info;
    }

    private void sendWifiStateChangedBroadcast(boolean wasEnabled, boolean enabled) {
        Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_WIFI_STATE, enabled
                ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED);
        intent.putExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, wasEnabled
                ? WifiManager.WIFI_STATE_ENABLED : WifiManager.WIFI_STATE_DISABLED);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendNetworkStateChangedBroadcast(NetworkInfo.DetailedState state) {
        ClientModeImpl.sendNetworkChangeBroadcast(mContext, state, false);
    }

    @Nullable
    private LinkProperties getActiveLinkProperties() {
        ConnectivityManager connectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) return null;
        Network network = connectivityManager.getActiveNetwork();
        return network == null ? null : connectivityManager.getLinkProperties(network);
    }

    private static boolean isApplied(@Nullable WifiControlResult result) {
        return result != null && result.result == 0;
    }

    private static int getModelSecurity(WifiConfiguration configuration) {
        int security = configuration.getDefaultSecurityParams().getSecurityType();
        if (security == WifiConfiguration.SECURITY_TYPE_OPEN) return 0;
        if (security == WifiConfiguration.SECURITY_TYPE_PSK) return 1;
        if (security == WifiConfiguration.SECURITY_TYPE_SAE) return 2;
        return -1;
    }

    private static int toFrameworkSecurity(int security) {
        if (security == 0) return WifiConfiguration.SECURITY_TYPE_OPEN;
        if (security == 2) return WifiConfiguration.SECURITY_TYPE_SAE;
        return WifiConfiguration.SECURITY_TYPE_PSK;
    }

    private static String getScanCapabilities(int security) {
        if (security == 0) return "[ESS]";
        if (security == 2) return "[RSN-SAE-CCMP][ESS]";
        return "[WPA2-PSK-CCMP][ESS]";
    }

    private static String removeDoubleQuotes(String value) {
        if (value != null && value.length() >= 2 && value.charAt(0) == '"'
                && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value == null ? "" : value;
    }

    private static int prefixLengthToNetmaskIntHTL(int prefixLength) {
        if (prefixLength <= 0) return 0;
        int networkOrderMask = prefixLength >= 32
                ? 0xffffffff : 0xffffffff << (32 - prefixLength);
        return Integer.reverseBytes(networkOrderMask);
    }
}
