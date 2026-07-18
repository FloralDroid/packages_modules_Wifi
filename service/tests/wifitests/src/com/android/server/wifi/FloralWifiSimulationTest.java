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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.util.Base64;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Unit tests for {@link FloralWifiSimulation}. */
@SmallTest
public class FloralWifiSimulationTest extends WifiBaseTest {
    @Mock private Context mContext;
    @Mock private PropertyService mPropertyService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mPropertyService.getString(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    public void disabledByDefault() {
        FloralWifiSimulation simulation =
                new FloralWifiSimulation(mContext, mPropertyService);

        assertFalse(simulation.isEnabled());
    }

    @Test
    public void bootPropertiesPopulateConnectionAndScanInformation() {
        String ssid = "Floral Test WiFi";
        String encodedSsid = Base64.encodeToString(
                ssid.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        when(mPropertyService.getBoolean("ro.boot.floral_wifi_simulation", false))
                .thenReturn(true);
        when(mPropertyService.getString("ro.boot.floral_wifi_ssid_b64", ""))
                .thenReturn(encodedSsid);
        when(mPropertyService.getString("ro.boot.floral_wifi_bssid",
                "02:00:00:12:00:01")).thenReturn("02:11:22:33:44:55");
        when(mPropertyService.getString("ro.boot.floral_wifi_rssi", "-45"))
                .thenReturn("-51");
        when(mPropertyService.getString("ro.boot.floral_wifi_frequency", "5180"))
                .thenReturn("2412");
        when(mPropertyService.getString("ro.boot.floral_wifi_link_speed", "866"))
                .thenReturn("150");

        FloralWifiSimulation simulation =
                new FloralWifiSimulation(mContext, mPropertyService);
        WifiInfo info = simulation.createConnectionInfo();
        List<ScanResult> scanResults = simulation.createScanResults();

        assertTrue(simulation.isEnabled());
        assertEquals("\"Floral Test WiFi\"", info.getSSID());
        assertEquals("02:11:22:33:44:55", info.getBSSID());
        assertEquals(-51, info.getRssi());
        assertEquals(2412, info.getFrequency());
        assertEquals(150, info.getLinkSpeed());
        assertEquals(SupplicantState.COMPLETED, info.getSupplicantState());
        assertEquals(1, scanResults.size());
        assertEquals(ssid, scanResults.get(0).SSID);
        assertEquals("02:11:22:33:44:55", scanResults.get(0).BSSID);
    }

    @Test
    public void invalidValuesUseSafeDefaults() {
        when(mPropertyService.getString("ro.boot.floral_wifi_ssid_b64", ""))
                .thenReturn("not valid base64%%% ");
        when(mPropertyService.getString("ro.boot.floral_wifi_bssid",
                "02:00:00:12:00:01")).thenReturn("invalid");
        when(mPropertyService.getString("ro.boot.floral_wifi_rssi", "-45"))
                .thenReturn("100");

        FloralWifiSimulation simulation =
                new FloralWifiSimulation(mContext, mPropertyService);
        WifiInfo info = simulation.createConnectionInfo();

        assertEquals("\"FloralDroid\"", info.getSSID());
        assertEquals("02:00:00:12:00:01", info.getBSSID());
        assertEquals(-45, info.getRssi());
    }
}
