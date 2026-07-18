# FloralDroid Wi-Fi simulation

FloralDroid can expose a stable Wi-Fi identity to Android applications while keeping the existing
Ethernet interface as the only data path. The feature is disabled unless explicitly enabled at
container startup.

## Startup parameters

| Parameter | Default | Description |
| --- | --- | --- |
| `androidboot.floral_wifi_simulation` | `0` | Set to `1` to enable simulation. |
| `androidboot.floral_wifi_ssid_b64` | empty | Base64-encoded SSID; preferred for spaces or non-ASCII text. |
| `androidboot.floral_wifi_ssid` | `FloralDroid` | Raw SSID fallback when the Base64 value is absent. |
| `androidboot.floral_wifi_bssid` | `02:00:00:12:00:01` | Simulated access point BSSID. |
| `androidboot.floral_wifi_mac` | `02:00:00:12:00:02` | Simulated station MAC address. |
| `androidboot.floral_wifi_rssi` | `-45` | Signal level in dBm, from `-127` through `-1`. |
| `androidboot.floral_wifi_frequency` | `5180` | Frequency in MHz. |
| `androidboot.floral_wifi_link_speed` | `866` | Link speed in Mbps. |
| `androidboot.floral_wifi_security` | `wpa2` | One of `open`, `wpa2`, or `wpa3`. |

The redroid init converts each `androidboot.*` argument into a read-only `ro.boot.*` property.
Invalid values use deterministic defaults and never change network configuration.

## Network ownership

- `WifiServiceImpl` supplies simulated state, connection information, DHCP information, and scan
  results through standard Android APIs.
- Connectivity keeps the original Ethernet transport and adds an application-visible Wi-Fi
  transport to the same `NetworkAgent`.
- IP addresses, routes, DNS servers, validation state, and traffic continue to belong to the
  existing default network, normally `eth0` in redroid.
- No `wlan0`, supplicant connection, host wireless device, or additional kernel module is needed.

Android's normal SSID, BSSID, and local MAC permission redaction remains in effect.


# FloralDroid Wi-Fi 模拟

FloralDroid 可以在保留现有以太网接口作为唯一数据通路的同时，向 Android 应用提供稳定的 Wi-Fi 身份信息。除非在容器启动时显式启用，否则该功能默认关闭。

## 启动参数

| 参数                                   | 默认值                 | 说明                                              |
| ------------------------------------ | ------------------- | ----------------------------------------------- |
| `androidboot.floral_wifi_simulation` | `0`                 | 设置为 `1` 以启用 Wi-Fi 模拟。                           |
| `androidboot.floral_wifi_ssid_b64`   | 空                   | 使用 Base64 编码的 SSID；当 SSID 包含空格或非 ASCII 文本时优先使用。 |
| `androidboot.floral_wifi_ssid`       | `FloralDroid`       | 当未提供 Base64 值时使用的原始 SSID 备用值。                   |
| `androidboot.floral_wifi_bssid`      | `02:00:00:12:00:01` | 模拟接入点的 BSSID。                                   |
| `androidboot.floral_wifi_mac`        | `02:00:00:12:00:02` | 模拟终端的 MAC 地址。                                   |
| `androidboot.floral_wifi_rssi`       | `-45`               | 信号强度，单位为 dBm，取值范围为 `-127` 至 `-1`。               |
| `androidboot.floral_wifi_frequency`  | `5180`              | 工作频率，单位为 MHz。                                   |
| `androidboot.floral_wifi_link_speed` | `866`               | 链路速率，单位为 Mbps。                                  |
| `androidboot.floral_wifi_security`   | `wpa2`              | 可选值为 `open`、`wpa2` 或 `wpa3`。                    |

redroid 的 init 会将每个 `androidboot.*` 参数转换为只读的 `ro.boot.*` 属性。

无效参数会使用确定性的默认值，并且绝不会修改实际网络配置。

## 网络归属

* `WifiServiceImpl` 通过标准 Android API 提供模拟的 Wi-Fi 状态、连接信息、DHCP 信息和扫描结果。
* Connectivity 保留原有的以太网传输类型，并在同一个 `NetworkAgent` 上增加一个对应用可见的 Wi-Fi 传输类型。
* IP 地址、路由、DNS 服务器、网络验证状态和网络流量仍归属于现有默认网络；在 redroid 中通常为 `eth0`。
* 不需要 `wlan0`、supplicant 连接、宿主机无线设备或额外的内核模块。

Android 对 SSID、BSSID 和本地 MAC 地址原有的权限脱敏机制仍然生效。
