package com.vibeqwen.glasses.util;

/**
 * 官方千问 APP BLE 密钥读取器（方案三）。
 * 读 /data/data/com.alibaba.wow/shared_prefs 中的 GMA_BLE_KEY。
 * 方式：root（su）直接读取；Shizuku 仅作状态提示。
 */
public class ShizukuKeyReader {

    public static final String OFFICIAL_PKG = "com.alibaba.wow";
    private static final String PREFS_DIR = "/data/data/" + OFFICIAL_PKG + "/shared_prefs";

    /** 是否有 root（su 可用） */
    public static boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            String out = new String(p.getInputStream().readAllBytes(), "UTF-8");
            p.waitFor();
            return out.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    /** 用 root 执行命令 */
    public static String shRoot(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            String out = new String(p.getInputStream().readAllBytes(), "UTF-8");
            String err = new String(p.getErrorStream().readAllBytes(), "UTF-8");
            p.waitFor();
            String t = out.trim();
            return t.isEmpty() ? (err.trim().isEmpty() ? null : err.trim()) : t;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isShizukuAvailable() {
        try {
            return rikka.shizuku.Shizuku.pingBinder();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isShizukuGranted() {
        try {
            return rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** 读取官方 APP 的 BLE 认证密钥 */
    public static String readOfficialBleKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 官方APP BLE密钥读取 ===\n");
        sb.append("包名: ").append(OFFICIAL_PKG).append("\n");
        sb.append("root: ").append(hasRoot() ? "可用" : "不可用（需 Magisk/KernelSU）").append("\n");
        sb.append("Shizuku: ").append(isShizukuAvailable() ? "binder可用" : "不可用")
            .append(" / 授权: ").append(isShizukuGranted() ? "已授权" : "未授权").append("\n\n");

        if (!hasRoot()) {
            sb.append("说明：读取 /data/data/").append(OFFICIAL_PKG).append(" 需要 root（su）\n");
            sb.append("如已装 Shizuku，可先授权后用 adb 手动验证：\n");
            sb.append("  sh /data/user_de/0/moe.shizuku.privileged.api/start.sh\n");
            sb.append("  cat ").append(PREFS_DIR).append("/*.xml\n");
            return sb.toString();
        }

        String ls = shRoot("ls -la " + PREFS_DIR + "/");
        sb.append("--- prefs 目录 ---\n").append(ls != null ? ls : "(ls 失败)").append("\n\n");

        String grep = shRoot("grep -rE 'GMA_BLE_KEY|32BleKey|bleKey16|psk_key|local32BleKey|gma_last_success' " + PREFS_DIR + "/");
        sb.append("--- 密钥内容 ---\n").append(grep != null ? grep : "(未搜到)").append("\n\n");

        String xml = shRoot("cat " + PREFS_DIR + "/*.xml");
        if (xml != null) {
            StringBuilder filtered = new StringBuilder();
            for (String line : xml.split("\n")) {
                if (line.contains("GMA_BLE_KEY") || line.contains("bleKey")
                        || line.contains("bind_device") || line.contains("local32BleKey")) {
                    filtered.append(line).append("\n");
                }
            }
            if (filtered.length() > 0) sb.append("--- prefs XML 过滤 ---\n").append(filtered);
        }
        return sb.toString();
    }
}