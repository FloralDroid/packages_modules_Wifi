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
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiSsid;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.android.net.module.util.Inet4AddressUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Provides an application-visible Wi-Fi connection backed by the existing default network.
 *
 * <p>This class never creates an interface, changes routes, or starts a supplicant. The real
 * network remains owned by ConnectivityService (normally redroid's {@code eth0}); only the
 * information returned through WifiManager is synthesized.</p>
 */
final class FloralWifiSimulation {
    private static final String TAG = "FloralWifiSimulation";

    private static final String PROP_ENABLED = "ro.boot.floral_wifi_simulation";
    private static final String PROP_SSID = "ro.boot.floral_wifi_ssid";
    private static final String PROP_SSID_BASE64 = "ro.boot.floral_wifi_ssid_b64";
    private static final String PROP_BSSID = "ro.boot.floral_wifi_bssid";
    private static final String PROP_MAC_ADDRESS = "ro.boot.floral_wifi_mac";
    private static final String PROP_RSSI = "ro.boot.floral_wifi_rssi";
    private static final String PROP_FREQUENCY = "ro.boot.floral_wifi_frequency";
    private static final String PROP_LINK_SPEED = "ro.boot.floral_wifi_link_speed";
    private static final String PROP_SECURITY = "ro.boot.floral_wifi_security";

    private static final String DEFAULT_SSID = "FloralDroid";
    private static final String DEFAULT_BSSID = "02:00:00:12:00:01";
    private static final String DEFAULT_MAC_ADDRESS = "02:00:00:12:00:02";
    private static final int DEFAULT_RSSI = -45;
    private static final int DEFAULT_FREQUENCY_MHZ = 5180;
    private static final int DEFAULT_LINK_SPEED_MBPS = 866;
    private static final int SIMULATED_NETWORK_ID = 0;

    private final Context mContext;
    private final boolean mEnabled;
    private final byte[] mSsidBytes;
    private final String mSsid;
    private final String mBssid;
    private final String mMacAddress;
    private final int mRssi;
    private final int mFrequency;
    private final int mLinkSpeed;
    private final int mSecurityType;
    private final String mScanCapabilities;

    FloralWifiSimulation(@NonNull Context context, @NonNull PropertyService propertyService) {
        mContext = context;
        mEnabled = propertyService.getBoolean(PROP_ENABLED, false);
        mSsidBytes = readSsid(propertyService);
        mSsid = new String(mSsidBytes, StandardCharsets.UTF_8);
        mBssid = readMacAddress(propertyService, PROP_BSSID, DEFAULT_BSSID);
        mMacAddress = readMacAddress(propertyService, PROP_MAC_ADDRESS, DEFAULT_MAC_ADDRESS);
        mRssi = readInt(propertyService, PROP_RSSI, DEFAULT_RSSI, -127, -1);
        mFrequency = readInt(propertyService, PROP_FREQUENCY, DEFAULT_FREQUENCY_MHZ,
                2412, 7125);
        mLinkSpeed = readInt(propertyService, PROP_LINK_SPEED, DEFAULT_LINK_SPEED_MBPS, 1, 10000);

        String security = propertyService.getString(PROP_SECURITY, "wpa2")
                .trim().toLowerCase(Locale.ROOT);
        if ("open".equals(security)) {
            mSecurityType = WifiConfiguration.SECURITY_TYPE_OPEN;
            mScanCapabilities = "[ESS]";
        } else if ("wpa3".equals(security)) {
            mSecurityType = WifiConfiguration.SECURITY_TYPE_SAE;
            mScanCapabilities = "[RSN-SAE-CCMP][ESS]";
        } else {
            mSecurityType = WifiConfiguration.SECURITY_TYPE_PSK;
            mScanCapabilities = "[WPA2-PSK-CCMP][ESS]";
        }
    }

    boolean isEnabled() {
        return mEnabled;
    }

    /** Creates a fresh value so caller-specific redaction can be applied safely. */
    @NonNull
    WifiInfo createConnectionInfo() {
        WifiInfo info = new WifiInfo.Builder()
                .setSsid(mSsidBytes)
                .setBssid(mBssid)
                .setRssi(mRssi)
                .setNetworkId(SIMULATED_NETWORK_ID)
                .setCurrentSecurityType(mSecurityType)
                .build();
        info.setMacAddress(mMacAddress);
        info.setFrequency(mFrequency);
        info.setLinkSpeed(mLinkSpeed);
        info.setTxLinkSpeedMbps(mLinkSpeed);
        info.setRxLinkSpeedMbps(mLinkSpeed);
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

    /** Returns the simulated access point as the latest scan result. */
    @NonNull
    List<ScanResult> createScanResults() {
        ScanResult result = new ScanResult(WifiSsid.createFromByteArray(mSsidBytes), mSsid,
                mBssid, 0, 0, mScanCapabilities, mRssi, mFrequency,
                SystemClock.elapsedRealtimeNanos() / 1000, 0, 0, 0, 0, 0, false);
        return Collections.singletonList(result);
    }

    /** Builds the deprecated DHCP view from the real default network without changing it. */
    @NonNull
    DhcpInfo createDhcpInfo() {
        DhcpInfo info = new DhcpInfo();
        LinkProperties linkProperties = getActiveLinkProperties();
        if (linkProperties == null) {
            return info;
        }

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
            if (!(dns instanceof Inet4Address)) {
                continue;
            }
            if (dnsIndex == 0) {
                info.dns1 = Inet4AddressUtils.inet4AddressToIntHTL((Inet4Address) dns);
            } else {
                info.dns2 = Inet4AddressUtils.inet4AddressToIntHTL((Inet4Address) dns);
                break;
            }
            dnsIndex++;
        }
        return info;
    }

    private LinkProperties getActiveLinkProperties() {
        ConnectivityManager connectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return null;
        }
        Network network = connectivityManager.getActiveNetwork();
        return network == null ? null : connectivityManager.getLinkProperties(network);
    }

    private static byte[] readSsid(PropertyService propertyService) {
        String encodedSsid = propertyService.getString(PROP_SSID_BASE64, "");
        if (!TextUtils.isEmpty(encodedSsid)) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encodedSsid);
                if (isValidSsid(decoded)) {
                    return decoded;
                }
                Log.w(TAG, "Ignoring floral Wi-Fi SSID outside the 1..32 byte range");
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "Ignoring invalid floral Wi-Fi base64 SSID", exception);
            }
        }

        byte[] rawSsid = propertyService.getString(PROP_SSID, DEFAULT_SSID)
                .getBytes(StandardCharsets.UTF_8);
        if (isValidSsid(rawSsid)) {
            return rawSsid;
        }
        Log.w(TAG, "Using default floral Wi-Fi SSID because the configured value is invalid");
        return DEFAULT_SSID.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isValidSsid(byte[] ssid) {
        return ssid.length > 0 && ssid.length <= 32;
    }

    private static String readMacAddress(PropertyService propertyService, String property,
            String defaultValue) {
        String value = propertyService.getString(property, defaultValue).trim();
        if (value.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) {
            return value.toLowerCase(Locale.ROOT);
        }
        Log.w(TAG, "Using default value for invalid property " + property);
        return defaultValue;
    }

    private static int readInt(PropertyService propertyService, String property, int defaultValue,
            int minimum, int maximum) {
        String value = propertyService.getString(property, Integer.toString(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= minimum && parsed <= maximum) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Invalid boot parameters use a safe, deterministic default.
        }
        Log.w(TAG, "Using default value for invalid property " + property);
        return defaultValue;
    }

    private static int prefixLengthToNetmaskIntHTL(int prefixLength) {
        if (prefixLength <= 0) {
            return 0;
        }
        int networkOrderMask = prefixLength >= 32
                ? 0xffffffff : 0xffffffff << (32 - prefixLength);
        return Integer.reverseBytes(networkOrderMask);
    }
}
