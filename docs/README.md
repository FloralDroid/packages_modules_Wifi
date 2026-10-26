# FloralDroid Wi-Fi simulation

[简体中文](README.zh-CN.md)

FloralDroid exposes a dynamic multi-AP Wi-Fi identity to Android applications while keeping the
existing Ethernet interface as the only data path.

## Startup configuration

The vendor Wi-Fi model reads `/ipc/floral_stream/wifi.json`. A valid document enables the
simulation and supplies the AP list, connected BSSID, station MAC, security, credentials, and link
baselines. Missing, unreadable, or invalid content leaves simulation disabled. Boot parameters are
not supported.

## Network ownership

- `WifiServiceImpl` supplies simulated state, connection information, DHCP information, and scan
  results through standard Android APIs. Android Settings can connect, disconnect, and switch among
  configured APs; secured AP credentials are checked by the vendor model.
- Connectivity keeps the original Ethernet transport and adds an application-visible Wi-Fi
  transport to the same `NetworkAgent`.
- IP addresses, routes, DNS servers, validation state, and traffic continue to belong to the
  existing default network, normally `eth0` in redroid.
- No `wlan0`, supplicant connection, host wireless device, or additional kernel module is needed.

Android's normal SSID, BSSID, and local MAC permission redaction remains in effect.
