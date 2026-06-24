package com.example.idraccontroller;

import android.content.Context;
import com.jcraft.jsch.*;
import java.io.*;

/**
 * iDRAC API 服务：通过 SSH 执行 racadm 命令
 * 优化：一次 SSH 会话执行多条命令（runSshCommandsInOneSession）
 * 优化：30 秒缓存避免重复查询（getAllInfo()）
 */
@SuppressWarnings("deprecation") // 兼容 minSdk=21，不使用 AndroidX
public class IdracApiService {

    private Context context;

    public IdracApiService(Context ctx) {
        this.context = ctx;
    }

    // ===================== SSH 核心 =====================

    /**
     * 执行单条 racadm 命令：先尝试 ChannelExec，失败则回退 ChannelShell。
     */
    private SshResult runSshCommand(String command) {
        String racadmCmd = command.startsWith("racadm ") ? command.substring(7) : command;
        SshResult execResult = runChannelExec(racadmCmd);
        if (execResult.hasData()) return execResult;
        return runChannelShell(racadmCmd);
    }

    /**
     * 一次 SSH 会话执行多条命令（性能优化）。
     * 使用 ChannelShell 按顺序发送命令，分别收集输出。
     * 优化：更快检测命令结束（检测提示符 + 缩短超时）
     */
    private SshResult[] runSshCommandsInOneSession(String... commands) {
        Session session = null;
        ChannelShell channel = null;
        SshResult[] results = new SshResult[commands.length];
        for (int i = 0; i < commands.length; i++) results[i] = new SshResult(null, "无输出");

        try {
            session = openSession();
            channel = (ChannelShell) session.openChannel("shell");
            channel.setPty(true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            channel.setOutputStream(baos);

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect();

            // 读取 banner，最多等 1.5 秒（优化：从 2 秒缩短）
            StringBuilder allOutput = new StringBuilder();
            long deadline = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < deadline) {
                if (in.available() > 0) {
                    byte[] buf = new byte[8192];
                    int n = in.read(buf);
                    if (n > 0) {
                        String chunk = new String(buf, 0, n, "UTF-8");
                        allOutput.append(chunk);
                        // 优化：如果已读取到提示符，提前结束
                        if (chunk.contains("racadm >>") || chunk.contains("iDRAC>") || chunk.contains("->")) {
                            break;
                        }
                    }
                } else {
                    Thread.sleep(80); // 优化：从 100ms 缩短到 80ms
                }
            }

            // 逐条发送命令并收集输出
            for (int i = 0; i < commands.length; i++) {
                String cmd = commands[i];
                // 发送命令
                out.write(("racadm " + cmd + "\n").getBytes("UTF-8"));
                out.flush();

                // 等待输出，最多 8 秒（优化：从 10 秒缩短），2.5 秒无新数据则结束
                long lastData = System.currentTimeMillis();
                long cmdDeadline = System.currentTimeMillis() + 8000;
                StringBuilder cmdOutput = new StringBuilder();
                boolean gotPrompt = false;

                while (System.currentTimeMillis() < cmdDeadline && !gotPrompt) {
                    if (in.available() > 0) {
                        byte[] buf = new byte[8192];
                        int n = in.read(buf);
                        if (n > 0) {
                            String chunk = new String(buf, 0, n, "UTF-8");
                            cmdOutput.append(chunk);
                            lastData = System.currentTimeMillis();

                            // 优化：检测提示符，提前结束等待
                            if (chunk.contains("racadm >>") || chunk.contains("iDRAC>") ||
                                (chunk.contains("->") && chunk.contains("admin"))) {
                                gotPrompt = true;
                                // 优化：再等 200ms 确保输出完整
                                Thread.sleep(200);
                                break;
                            }
                        }
                    } else {
                        Thread.sleep(80); // 优化：从 100ms 缩短到 80ms
                        if (System.currentTimeMillis() - lastData > 2500) break; // 优化：从 3 秒缩短到 2.5 秒
                    }
                }

                String raw = cmdOutput.toString();
                // 清理输出：去掉命令回显和提示符
                String cleaned = cleanShellOutput(raw, cmd);
                if (!cleaned.isEmpty()) {
                    results[i] = new SshResult(cleaned, "");
                }
            }

            out.write("exit\n".getBytes("UTF-8"));
            out.flush();
            Thread.sleep(300);

            try { channel.disconnect(); } catch (Exception ignored) {}
            try { session.disconnect(); } catch (Exception ignored) {}

        } catch (Exception e) {
            for (int i = 0; i < results.length; i++) {
                if (!results[i].hasData()) {
                    results[i] = new SshResult(null, "SSH失败: " + e.getMessage());
                }
            }
        } finally {
            try { if (channel != null && channel.isConnected()) channel.disconnect(); } catch (Exception ignored) {}
            try { if (session != null && session.isConnected()) session.disconnect(); } catch (Exception ignored) {}
        }

        return results;
    }

    private SshResult runChannelExec(String racadmCmd) {
        Session session = null;
        ChannelExec channel = null;
        try {
            session = openSession();
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("racadm " + racadmCmd);

            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            channel.setOutputStream(outBuf);
            channel.setErrStream(errBuf);

            channel.connect(10000);

            long deadline = System.currentTimeMillis() + 15000;
            while (channel.isConnected() && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }
            Thread.sleep(300);

            String output = outBuf.toString("UTF-8").trim();
            String error = errBuf.toString("UTF-8").trim();

            try { channel.disconnect(); } catch (Exception ignored) {}
            try { session.disconnect(); } catch (Exception ignored) {}

            if (!output.isEmpty()) return new SshResult(output, null);
            if (!error.isEmpty()) return new SshResult(null, "SSH错误: " + error);
            return new SshResult(null, "无输出");
        } catch (Exception e) {
            return new SshResult(null, "Exec失败: " + e.getMessage());
        } finally {
            try { if (channel != null && channel.isConnected()) channel.disconnect(); } catch (Exception ignored) {}
            try { if (session != null && session.isConnected()) session.disconnect(); } catch (Exception ignored) {}
        }
    }

    private SshResult runChannelShell(String racadmCmd) {
        Session session = null;
        ChannelShell channel = null;
        try {
            session = openSession();
            channel = (ChannelShell) session.openChannel("shell");
            channel.setPty(true);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            channel.setOutputStream(baos);

            InputStream in = channel.getInputStream();
            OutputStream out = channel.getOutputStream();
            channel.connect();

            StringBuilder output = new StringBuilder();
            long deadline = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < deadline) {
                if (in.available() > 0) {
                    byte[] buf = new byte[8192];
                    int n = in.read(buf);
                    if (n > 0) output.append(new String(buf, 0, n, "UTF-8"));
                } else {
                    Thread.sleep(100);
                }
            }

            out.write(("racadm " + racadmCmd + "\n").getBytes("UTF-8"));
            out.flush();

            long lastData = System.currentTimeMillis();
            deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                if (in.available() > 0) {
                    byte[] buf = new byte[8192];
                    int n = in.read(buf);
                    if (n > 0) {
                        output.append(new String(buf, 0, n, "UTF-8"));
                        lastData = System.currentTimeMillis();
                    }
                } else {
                    Thread.sleep(100);
                    if (System.currentTimeMillis() - lastData > 3000) break;
                }
            }

            out.write("exit\n".getBytes("UTF-8"));
            out.flush();
            Thread.sleep(300);

            try { channel.disconnect(); } catch (Exception ignored) {}
            try { session.disconnect(); } catch (Exception ignored) {}

            String raw = output.toString();
            String cleaned = cleanShellOutput(raw, racadmCmd);
            if (cleaned.isEmpty()) return new SshResult(null, "无输出");
            return new SshResult(cleaned, "");
        } catch (Exception e) {
            return new SshResult(null, "Shell失败: " + e.getMessage());
        } finally {
            try { if (channel != null && channel.isConnected()) channel.disconnect(); } catch (Exception ignored) {}
            try { if (session != null && session.isConnected()) session.disconnect(); } catch (Exception ignored) {}
        }
    }

    private Session openSession() throws JSchException {
        JSch jsch = new JSch();
        String ip = Prefs.getIpAddress(context);
        String user = Prefs.getUsername(context);
        String pwd = Prefs.getPassword(context);
        int port = Prefs.getSshPort(context);

        Session session = jsch.getSession(user, ip, port);
        session.setPassword(pwd);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
        // 优化：缩短超时时间（15秒 → 10秒）
        session.setTimeout(10000);
        session.connect(10000);
        return session;
    }

    private String cleanShellOutput(String raw, String command) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = raw.split("\r?\n");
        boolean afterCmd = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("Last login") || trimmed.startsWith("racadm >>")) continue;
            if (trimmed.toLowerCase().contains("copyright") && trimmed.length() < 100) continue;
            if (trimmed.startsWith("***") || trimmed.startsWith("===")) continue;

            if (trimmed.equals("racadm " + command) || trimmed.contains("racadm " + command)) {
                afterCmd = true;
                continue;
            }

            if (trimmed.endsWith("->") || trimmed.endsWith(">") || trimmed.endsWith("#") ||
                trimmed.matches(".*/admin.*->.*") || trimmed.startsWith("iDRAC>")) {
                if (!trimmed.contains("racadm")) continue;
            }

            if (afterCmd || sb.length() > 0) {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ===================== 公开接口 =====================

    // 缓存：上次查询结果和时间
    private static SshResult[] lastAllInfoResults = null;
    private static long lastAllInfoTime = 0;
    private static final long CACHE_TTL = 30 * 1000; // 30秒缓存

    /**
     * 一次 SSH 会话同时获取：电源状态 + 传感器 + 硬件信息（性能优化）
     * 返回数组：[0]=电源状态, [1]=传感器数据, [2]=硬件信息, [3]=CPU信息, [4]=内存信息
     * 优化：只发3条命令（CPU/内存信息已包含在 getsysinfo 输出中）
     * 优化：30秒缓存避免重复SSH连接
     * @param forceRefresh 是否强制刷新（true=跳过缓存，false=使用缓存）
     */
    public SshResult[] getAllInfo(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        // 缓存命中：30秒内直接返回上次结果（避免重复SSH连接）
        if (!forceRefresh && lastAllInfoResults != null && (now - lastAllInfoTime) < CACHE_TTL) {
            return lastAllInfoResults;
        }
        // 一次SSH会话发3条命令（不是5条）
        SshResult[] results = runSshCommandsInOneSession(
            "serveraction powerstatus",
            "getsensorinfo",
            "getsysinfo"
        );
        // 如果某条命令失败，单独重试一次
        if (!results[0].hasData()) results[0] = runSshCommand("serveraction powerstatus");
        if (!results[1].hasData()) results[1] = runSshCommand("getsensorinfo");
        if (!results[2].hasData()) results[2] = runSshCommand("getsysinfo");
        // CPU和内存信息已包含在 getsysinfo 输出中，直接复用（避免数组越界）
        // 创建长度为5的数组，兼容 MainActivity 的访问方式
        SshResult[] fullResults = new SshResult[5];
        fullResults[0] = results[0];
        fullResults[1] = results[1];
        fullResults[2] = results[2];
        fullResults[3] = results[2]; // CPU信息（从硬件信息中解析）
        fullResults[4] = results[2]; // 内存信息（从硬件信息中解析）
        // 更新缓存
        lastAllInfoResults = fullResults;
        lastAllInfoTime = now;
        return fullResults;
    }

    /**
     * 重载：默认使用缓存
     */
    public SshResult[] getAllInfo() {
        return getAllInfo(false);
    }

    /**
     * 清除缓存（在电源操作后调用，确保下次查询获取最新状态）
     */
    public static void clearCache() {
        lastAllInfoResults = null;
        lastAllInfoTime = 0;
    }

    /** 一次 SSH 会话同时获取传感器 + 硬件信息（性能优化） */
    public SshResult[] getSensorAndHardware() {
        SshResult[] results = runSshCommandsInOneSession("getsensorinfo", "getsysinfo");
        // 如果合并方法没拿到数据，分别单独重试
        if (!results[0].hasData()) results[0] = runSshCommand("getsensorinfo");
        if (!results[1].hasData()) results[1] = runSshCommand("getsysinfo");
        return results;
    }

    /**
     * 获取服务器电源状态
     * @return 电源状态字符串（例如 "电源: ON"）
     */
    public String getPowerStatus() {
        SshResult r = runSshCommand("serveraction powerstatus");
        if (r.hasData()) {
            String out = r.output.toLowerCase();
            if (out.contains("on") && (out.contains("power") || out.contains("currently")))
                return "电源: ON";
            if (out.contains("off") && (out.contains("power") || out.contains("currently")))
                return "电源: OFF";
            return "状态: " + r.output.trim();
        }
        return r.error != null && !r.error.isEmpty() ? r.error : "查询失败";
    }

    /**
     * 电源控制（开机/关机/重启）
     * @param action "on"|"off"|"cycle"
     * @return 操作结果字符串
     */
    public String powerControl(String action) {
        String cmd, label;
        switch (action) {
            case "on":  cmd = "powerup";    label = "开机"; break;
            case "off": cmd = "powerdown";  label = "关机"; break;
            default:    cmd = "powercycle"; label = "重启"; break;
        }
        SshResult r = runSshCommand("serveraction " + cmd);
        if (r.hasData()) {
            String out = r.output.toLowerCase();
            if (out.contains("success") || out.contains("initiated") || out.contains("ok"))
                return "操作成功: " + label;
            return "结果: " + r.output.trim();
        }
        return r.error != null && !r.error.isEmpty() ? r.error : "指令已发送";
    }

    /**
     * 优雅关机（操作系统级别关机，不是强制断电）
     */
    public String gracefulShutdown() {
        SshResult r = runSshCommand("serveraction gracefulshutdown");
        if (r.hasData()) {
            return r.output.toLowerCase().contains("error")
                ? "结果: " + r.output.trim()
                : "优雅关机指令已发送";
        }
        return r.error != null && !r.error.isEmpty() ? r.error : "指令已发送";
    }

    /**
     * 优雅重启（操作系统级别重启，不是强制重启）
     */
    public String gracefulRestart() {
        SshResult r = runSshCommand("serveraction gracefulrestart");
        if (r.hasData()) {
            return r.output.toLowerCase().contains("error")
                ? "结果: " + r.output.trim()
                : "优雅重启指令已发送";
        }
        return r.error != null && !r.error.isEmpty() ? r.error : "指令已发送";
    }

    public String getSensorData() {
        SshResult r = runSshCommand("getsensorinfo");
        if (r.hasData()) return r.output;
        return r.error != null && !r.error.isEmpty() ? r.error : "获取失败";
    }

    public String getHardwareInfo() {
        SshResult r = runSshCommand("getsysinfo");
        if (r.hasData()) return r.output;
        return r.error != null && !r.error.isEmpty() ? r.error : "获取失败";
    }

    // ===================== 解析工具（静态方法）=====================

    public static String parseCpuTemp(String sensorData) {
        if (sensorData == null || sensorData.startsWith("获取失败") || sensorData.startsWith("SSH错误")
            || sensorData.startsWith("Exec失败") || sensorData.startsWith("Shell失败"))
            return "--";
        String[] lines = sensorData.split("\n");
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("temp") || lower.contains("cpu") || lower.contains("inlet")
                || lower.contains("ambient") || lower.contains("system board")) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*[°º]?\\s*[cC]");
                java.util.regex.Matcher m = p.matcher(line);
                if (m.find()) return m.group(1);
            }
        }
        return "--";
    }

    public static String parseFanSpeed(String sensorData) {
        if (sensorData == null || sensorData.startsWith("获取失败") || sensorData.startsWith("SSH错误"))
            return "--";
        String[] lines = sensorData.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("fan") && line.toLowerCase().contains("rpm")) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{3,5})\\s*rpm", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(line);
                if (m.find()) return m.group(1);
            }
        }
        return "--";
    }

    public static String parsePowerWatt(String sensorData) {
        if (sensorData == null || sensorData.startsWith("获取失败") || sensorData.startsWith("SSH错误"))
            return "--";
        String[] lines = sensorData.split("\n");
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("watt") || lower.contains("pwr") || lower.contains("power")) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*[wW]");
                java.util.regex.Matcher m = p.matcher(line);
                if (m.find()) return m.group(1);
            }
        }
        return "--";
    }

    /**
     * 解析硬件信息 - 优化版，支持 "Key = Value" 格式
     * 提取关键硬件信息并以结构化方式显示
     */
    public static String parseHardwareSummary(String sysInfo) {
        if (sysInfo == null || sysInfo.startsWith("获取失败") || sysInfo.startsWith("SSH错误"))
            return "暂无数据";

        // 存储解析出的关键信息
        String model = "";
        String bios = "";
        String svcTag = "";
        String hostName = "";
        String osName = "";
        String osVersion = "";
        String powerStatus = "";
        String airflow = "";
        String exhaustTemp = "";
        String firmwareVersion = "";

        String[] lines = sysInfo.split("\n");
        for (String line : lines) {
            // 支持 "Key = Value" 格式
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim().toLowerCase();
                    String val = parts[1].trim();

                    if (key.contains("system model")) model = val;
                    if (key.contains("system bios version")) bios = val;
                    if (key.contains("service tag")) svcTag = val;
                    if (key.contains("host name")) hostName = val;
                    if (key.contains("os name")) osName = val;
                    if (key.contains("os version")) osVersion = val;
                    if (key.contains("power status")) powerStatus = val;
                    if (key.contains("estimatedsystemairflow")) airflow = val;
                    if (key.contains("estimatedexhausttemperature")) exhaustTemp = val;
                    if (key.contains("firmware version") && firmwareVersion.isEmpty()) firmwareVersion = val;
                }
            }
        }

        // 构建结构化摘要信息
        StringBuilder sb = new StringBuilder();
        if (!model.isEmpty()) sb.append("📦 型号: ").append(model).append("\n");
        if (!svcTag.isEmpty()) sb.append("🏷️ 服务标签: ").append(svcTag).append("\n");
        if (!bios.isEmpty()) sb.append("🔧 BIOS: ").append(bios).append("\n");
        if (!firmwareVersion.isEmpty()) sb.append("⚙️ 固件版本: ").append(firmwareVersion).append("\n");
        if (!powerStatus.isEmpty()) sb.append("⚡ 电源状态: ").append(powerStatus).append("\n");
        if (!hostName.isEmpty()) sb.append("💻 主机名: ").append(hostName).append("\n");
        if (!osName.isEmpty()) sb.append("🖥️ 操作系统: ").append(osName).append("\n");
        if (!osVersion.isEmpty()) sb.append("📊 OS版本: ").append(osVersion).append("\n");
        if (!airflow.isEmpty()) sb.append("🌬️ 系统气流: ").append(airflow).append("\n");
        if (!exhaustTemp.isEmpty()) sb.append("🌡️ 排气温度: ").append(exhaustTemp).append("\n");

        // 如果解析不到关键信息，显示清理后的完整输出
        String result = sb.toString().trim();
        if (result.isEmpty()) {
            // 降级方案：显示清理后的输出
            StringBuilder raw = new StringBuilder();
            int validLineCount = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.matches("^[=\\-\\s\\.]+$")) continue;
                if (trimmed.contains("@") && (trimmed.endsWith(">") || trimmed.endsWith("#") || trimmed.endsWith("->"))) continue;
                if (trimmed.toLowerCase().startsWith("racadm")) continue;
                if (trimmed.toLowerCase().contains("copyright") && trimmed.length() < 100) continue;
                if (trimmed.toLowerCase().startsWith("connected to")) continue;

                raw.append(trimmed).append("\n");
                validLineCount++;

                if (validLineCount >= 40) {
                    raw.append("...(更多内容请查看完整硬件信息)");
                    break;
                }
            }
            result = raw.toString().trim();
        }

        return result.isEmpty() ? "数据解析中..." : result;
    }

    public static String formatHardwareDetails(String sysInfo) {
        if (sysInfo == null || sysInfo.startsWith("获取失败") || sysInfo.startsWith("SSH错误"))
            return "获取失败:\n" + (sysInfo != null ? sysInfo : "无数据");
        return sysInfo;
    }

    static class SshResult {
        String output;
        String error;
        SshResult(String o, String e) { output = o; error = e; }
        boolean hasData() { return output != null && !output.trim().isEmpty(); }
    }
}
