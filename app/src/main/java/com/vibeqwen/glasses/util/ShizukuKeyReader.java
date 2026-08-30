package com.vibeqwen.glasses.util;

import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

/**
 * Shizuku 密钥读取器（方案三）：通过 Shizuku 授权读取官方千问 APP
 * （com.alibaba.wow）存储的 BLE 认证密钥。
 *
 * 需要：手机已安装 Shizuku（moe.shizuku.privileged.api）并授权本 APP。
 */
public class ShizukuKeyReader {

    /** 官方千问 APP 包名 */
    public static final String OFFICIAL_PKG = "com.alibaba.wow";
    private static final String PREFS_DIR = "/data/data/" + OFFICIAL_PKG + "/shared_prefs";

    /** Shizuku 是否可用（binder 存在） */
    public static boolean isShizukuAvailable() {
        return Shizuku.pingBinder();
    }

    /** 是否已获得 Shizuku 授权 */
    public static boolean isGranted() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** 请求 Shizuku 授权（需要绑定 requestPermissionReceiver/onRequestPermissionResult） */
    public static void requestPermission() {
        try {
            Shizuku.requestPermission(0);
        } catch (Exception e) {
            // 忽略：UI 会提示授权方式
        }
    }

    /** 用 Shizuku 执行命令，返回 stdout（null=失败） */
    public static String sh(String command) {
        if (!isShizukuAvailable()) return null;
        if (!isGranted()) return null;
        try {
            java.lang.Process p = Shizuku.newProcess(new ProcessBuilder("sh", "-c", command));
            String text = new String(p.getInputStream().readAllBytes(), "UTF-8").trim();
            p.waitFor();
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取官方 APP 的 BLE 认证密钥 */
    public static String readOfficialBleKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 官方APP BLE密钥读取 ===\n");
        sb.append("包名: ").append(OFFICIAL_PKG).append("\n");
        sb.append("Shizuku: ").append(isShizukuAvailable() ? "可用" : "不可用")
            .append(" / 授权: ").append(isGranted() ? "已授权" : "未授权").append("\n\n");

        String ls = sh("ls -la " + PREFS_DIR + "/");
        sb.append("--- prefs 目录 ---\n").append(ls != null ? ls : "(无法访问，请确认 Shizuku 已授权)").append("\n\n");

        String grep = sh("grep -rE 'GMA_BLE_KEY|32BleKey|bleKey16|psk_key|local32BleKey|gma_last_success' " + PREFS_DIR + "/");
        sb.append("--- 密钥内容 ---\n").append(grep != null ? grep : "(未搜到密钥)").append("\n\n");

        String xml = sh("cat " + PREFS_DIR + "/*.xml");
        if (xml != null) {
            StringBuilder filtered = new StringBuilder();
            for (String line : xml.split("\n")) {
                if (line.contains("GMA_BLE_KEY") || line.contains("bleKey")
                        || line.contains("bind_device") || line.contains("local32BleKey")) {
                    filtered.append(line).append("\n");
                }
            }
            if (filtered.length() > 0) {
                sb.append("--- prefs XML 过滤 ---\n").append(filtered);
            }
        }
        return sb.toString();
    }
}