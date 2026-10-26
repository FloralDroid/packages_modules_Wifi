# FloralDroid Wi-Fi 模拟

[English](README.md)

FloralDroid 在保留现有以太网接口作为唯一数据通路的同时，向 Android 应用提供动态的多 AP Wi-Fi 身份信息。

## 启动配置

Vendor Wi-Fi 模型读取 `/ipc/floral_stream/wifi.json`。整体校验通过后，文件提供 AP 列表、当前 BSSID、终端 MAC、安全类型、密码和链路基线并启动模拟。文件缺失、不可读或无效时保持关闭。不再支持 Wi-Fi 启动参数。

## 网络归属

- `WifiServiceImpl` 通过标准 Android API 提供模拟的 Wi-Fi 状态、连接信息、DHCP 信息和扫描结果。Android 自带设置可以连接、断开并切换文件中的 AP，加密 AP 密码由 Vendor 模型校验。
- Connectivity 保留原有的以太网传输类型，并在同一个 `NetworkAgent` 上增加一个对应用可见的 Wi-Fi 传输类型。
- IP 地址、路由、DNS 服务器、网络验证状态和网络流量仍归属于现有默认网络；在 redroid 中通常为 `eth0`。
- 不需要 `wlan0`、supplicant 连接、宿主机无线设备或额外的内核模块。

Android 对 SSID、BSSID 和本地 MAC 地址原有的权限脱敏机制仍然生效。
