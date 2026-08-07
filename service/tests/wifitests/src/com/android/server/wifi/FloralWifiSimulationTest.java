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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;

import androidx.test.filters.SmallTest;

import floral.device.wifi.WifiAccessPoint;
import floral.device.wifi.WifiControlResult;
import floral.device.wifi.WifiProfile;
import floral.device.wifi.WifiSnapshot;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unit tests for {@link FloralWifiSimulation}. */
@SmallTest
public class FloralWifiSimulationTest extends WifiBaseTest {
    @Mock private Context mContext;
    private FakeStateProvider mStateProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mStateProvider = new FakeStateProvider();
    }

    @Test
    public void missingProfileIsDisabledAndUnconfigured() {
        FloralWifiSimulation simulation =
                new FloralWifiSimulation(mContext, mStateProvider);

        assertFalse(simulation.isConfigured());
        assertFalse(simulation.isEnabled());
        assertTrue(simulation.createScanResults().isEmpty());
    }

    @Test
    public void binderStatePopulatesConnectionAndMultipleScanResults() {
        mStateProvider.addAccessPoint(1, "Floral Test WiFi", "02:11:22:33:44:55", 1, -51);
        mStateProvider.addAccessPoint(2, "Floral Guest", "02:11:22:33:44:66", 0, -67);
        mStateProvider.connect(1);
        FloralWifiSimulation simulation =
                new FloralWifiSimulation(mContext, mStateProvider);

        WifiInfo info = simulation.createConnectionInfo();
        List<ScanResult> scanResults = simulation.createScanResults();

        assertTrue(simulation.isConfigured());
        assertTrue(simulation.isEnabled());
        assertEquals("\"Floral Test WiFi\"", info.getSSID());
        assertEquals("02:11:22:33:44:55", info.getBSSID());
        assertEquals(-51, info.getRssi());
        assertEquals(5180, info.getFrequency());
        assertEquals(866, info.getLinkSpeed());
        assertEquals(SupplicantState.COMPLETED, info.getSupplicantState());
        assertEquals(2, scanResults.size());
        assertEquals("Floral Guest", scanResults.get(1).SSID);
    }

    @Test
    public void settingsConnectPassesUnquotedCredentialToStateService() {
        mStateProvider.addAccessPoint(8, "Secured", "02:11:22:33:44:88", 1, -55);
        FloralWifiSimulation simulation =
                new FloralWifiSimulation(mContext, mStateProvider);
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = "\"Secured\"";
        configuration.BSSID = "02:11:22:33:44:88";
        configuration.preSharedKey = "\"correct-password\"";
        configuration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);

        int result = simulation.connectFromSystem(configuration);

        assertEquals(FloralWifiSimulation.CONNECT_SUCCEEDED, result);
        assertEquals("Secured", mStateProvider.lastSsid);
        assertEquals("correct-password", mStateProvider.lastCredential);
        assertEquals(1, mStateProvider.lastSecurity);
    }

    @Test
    public void unrelatedNetworkFallsThroughToRealWifiStack() {
        mStateProvider.addAccessPoint(1, "Floral", "02:11:22:33:44:55", 0, -50);
        FloralWifiSimulation simulation =
                new FloralWifiSimulation(mContext, mStateProvider);
        WifiConfiguration configuration = new WifiConfiguration();
        configuration.SSID = "\"Other\"";
        configuration.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);

        assertEquals(FloralWifiSimulation.CONNECT_NOT_HANDLED,
                simulation.connectFromSystem(configuration));
    }

    private static final class FakeStateProvider implements FloralWifiSimulation.StateProvider {
        final WifiProfile profile = new WifiProfile();
        final WifiSnapshot snapshot = new WifiSnapshot();
        final List<WifiAccessPoint> accessPoints = new ArrayList<>();
        String lastSsid;
        String lastCredential;
        int lastSecurity;

        FakeStateProvider() {
            profile.version = 1;
            profile.countryCode = "CN";
            profile.stationMacAddress = "02:00:00:12:00:02";
            snapshot.ssid = "";
            snapshot.bssid = "";
        }

        void addAccessPoint(long identity, String ssid, String bssid, int security, int rssi) {
            WifiAccessPoint accessPoint = new WifiAccessPoint();
            accessPoint.identity = identity;
            accessPoint.ssid = ssid;
            accessPoint.bssid = bssid;
            accessPoint.security = security;
            accessPoint.rssiDbm = rssi;
            accessPoint.frequencyMhz = 5180;
            accessPoint.channelWidthMhz = 80;
            accessPoint.linkSpeedMbps = 866;
            accessPoints.add(accessPoint);
        }

        void connect(long identity) {
            WifiAccessPoint accessPoint = accessPoints.stream()
                    .filter(item -> item.identity == identity).findFirst().get();
            profile.enabled = true;
            profile.connectedAccessPointId = identity;
            snapshot.enabled = true;
            snapshot.connectedAccessPointId = identity;
            snapshot.ssid = accessPoint.ssid;
            snapshot.bssid = accessPoint.bssid;
            snapshot.security = accessPoint.security;
            snapshot.rssiDbm = accessPoint.rssiDbm;
            snapshot.frequencyMhz = accessPoint.frequencyMhz;
            snapshot.channelWidthMhz = accessPoint.channelWidthMhz;
            snapshot.linkSpeedMbps = accessPoint.linkSpeedMbps;
        }

        @Override
        public WifiProfile getProfile() {
            return profile;
        }

        @Override
        public WifiSnapshot getSnapshot() {
            return snapshot;
        }

        @Override
        public List<WifiAccessPoint> getAccessPoints() {
            return Collections.unmodifiableList(accessPoints);
        }

        @Override
        public WifiControlResult setEnabledFromSystem(boolean enabled) {
            profile.enabled = enabled;
            snapshot.enabled = enabled;
            return success();
        }

        @Override
        public WifiControlResult connectFromSystem(String ssid, String bssid,
                int security, String credential) {
            lastSsid = ssid;
            lastCredential = credential;
            lastSecurity = security;
            return success();
        }

        @Override
        public WifiControlResult disconnectFromSystem() {
            snapshot.connectedAccessPointId = 0;
            return success();
        }

        private static WifiControlResult success() {
            WifiControlResult result = new WifiControlResult();
            result.result = 0;
            return result;
        }
    }
}
