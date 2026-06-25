package com.example.idraccontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.*;
import java.util.List;

/**
 * 主 Activity：显示服务器状态、电源控制、传感器监控、硬件信息
 * 优化：一次 SSH 会话获取所有信息（电源状态+传感器+硬件信息）
 */
@SuppressWarnings("deprecation") // 兼容 minSdk=21，不使用 AndroidX
public class MainActivity extends Activity {

    private static final String TAG = "iDRAC_Main";
    private TextView textStatus, textMode, textServerName;
    private TextView textCpuTemp, textFanSpeed, textPowerWatt;
    private TextView textHardwareSummary;
    private Button btnPowerOn, btnPowerOff, btnPowerCycle, btnCheckStatus, btnSettings;
    private Button btnSwitchServer, btnAddServer, btnSshTerminal;
    private IdracApiService apiService;

    /**
     * Activity 创建时调用：初始化所有 UI 控件和事件监听
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "=== onCreate START ===");
        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "setContentView OK");
        } catch (Exception e) {
            Log.e(TAG, "setContentView FAILED", e);
            return;
        }

        try {
            apiService = new IdracApiService(this);
            Log.d(TAG, "IdracApiService created OK");
        } catch (Exception e) {
            Log.e(TAG, "IdracApiService creation FAILED", e);
        }

        try {
            textStatus = findViewById(R.id.text_status);
            textMode = findViewById(R.id.text_mode);
            textServerName = findViewById(R.id.text_server_name);
            textCpuTemp = findViewById(R.id.text_cpu_temp);
            textFanSpeed = findViewById(R.id.text_fan_speed);
            textPowerWatt = findViewById(R.id.text_power_watt);
            textHardwareSummary = findViewById(R.id.text_hardware_summary);
            textHardwareSummary.setMovementMethod(new ScrollingMovementMethod());
            Log.d(TAG, "TextViews bound OK");
        } catch (Exception e) {
            Log.e(TAG, "TextView binding FAILED", e);
        }

        try {
            btnPowerOn = findViewById(R.id.btn_power_on);
            btnPowerOff = findViewById(R.id.btn_power_off);
            btnPowerCycle = findViewById(R.id.btn_power_cycle);
            btnCheckStatus = findViewById(R.id.btn_check_status);
            btnSettings = findViewById(R.id.btn_settings);
            btnSwitchServer = findViewById(R.id.btn_switch_server);
            btnAddServer = findViewById(R.id.btn_add_server);
            btnSshTerminal = findViewById(R.id.btn_ssh_terminal);
            Log.d(TAG, "Buttons bound OK, btnCheckStatus=" + btnCheckStatus);
        } catch (Exception e) {
            Log.e(TAG, "Button binding FAILED", e);
        }

        updateServerDisplay();
        updateModeDisplay();

        try {
            btnSettings.setOnClickListener(v -> {
                Log.d(TAG, "Settings button clicked");
                Intent i = new Intent(this, SettingsActivity.class);
                ServerConfig active = ServerManager.getActiveServer(this);
                if (active != null) i.putExtra("server_id", active.id);
                startActivity(i);
            });
            Log.d(TAG, "Settings click listener set");

            btnSshTerminal.setOnClickListener(v -> {
                Log.d(TAG, "SSH Terminal button clicked");
                openSshTerminal();
            });
            Log.d(TAG, "SSH Terminal click listener set");

            btnAddServer.setOnClickListener(v -> {
                Log.d(TAG, "Add Server button clicked");
                startActivity(new Intent(this, SettingsActivity.class));
            });
            Log.d(TAG, "Add Server click listener set");

            btnSwitchServer.setOnClickListener(v -> {
                Log.d(TAG, "Switch Server button clicked");
                showServerPicker();
            });
            Log.d(TAG, "Switch Server click listener set");

            btnPowerOn.setOnClickListener(v -> {
                Log.d(TAG, "Power On button clicked");
                showPowerConfirmationDialog("on");
            });
            btnPowerOff.setOnClickListener(v -> {
                Log.d(TAG, "Power Off button clicked");
                showPowerConfirmationDialog("off");
            });
            btnPowerCycle.setOnClickListener(v -> {
                Log.d(TAG, "Power Cycle button clicked");
                showPowerConfirmationDialog("cycle");
            });
            btnCheckStatus.setOnClickListener(v -> {
                Log.d(TAG, ">>> CHECK STATUS BUTTON CLICKED <<<");
                checkStatus();
            });
            Log.d(TAG, "ALL click listeners set OK");
        } catch (Exception e) {
            Log.e(TAG, "Setting click listeners FAILED", e);
        }

        Log.d(TAG, "=== onCreate END ===");
    }

    /**
     * Activity 恢复时调用：刷新服务器显示和连接模式
     * 优化：如果配置完整且缓存过期，自动刷新状态
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        updateServerDisplay();
        updateModeDisplay();
        // 不再自动查询，避免启动时按钮被禁用导致用户第一次按没反应
        Log.d(TAG, "onResume: 不自动查询，用户需手动按按钮");
    }

    /**
     * 更新服务器名称显示，如果未配置则创建默认服务器
     */
    private void updateServerDisplay() {
        Log.d(TAG, "updateServerDisplay called");
        ServerManager.applyActiveToPrefs(this);
        ServerConfig active = ServerManager.getActiveServer(this);
        if (active != null) {
            textServerName.setText(active.name != null && !active.name.isEmpty() ? active.name : active.ip);
            Log.d(TAG, "Server display updated: " + active.name);
        } else {
            if (Prefs.isConfigured(this)) {
                ServerConfig sc = new ServerConfig();
                sc.name = "默认服务器";
                sc.ip = Prefs.getIpAddress(this);
                sc.username = Prefs.getUsername(this);
                sc.password = Prefs.getPassword(this);
                sc.sshPort = Prefs.getSshPort(this);
                ServerManager.addServer(this, sc);
                ServerManager.setActiveServerId(this, sc.id);
                textServerName.setText(sc.name);
                Log.d(TAG, "Default server created");
            } else {
                textServerName.setText("未配置");
                Log.d(TAG, "No server configured");
            }
        }
    }

    /**
     * 显示服务器选择对话框，允许用户切换当前活跃服务器
     */
    private void showServerPicker() {
        List<ServerConfig> servers = ServerManager.getServers(this);
        if (servers.isEmpty()) {
            Toast.makeText(this, "请先添加服务器", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        String[] names = new String[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            names[i] = (servers.get(i).name != null && !servers.get(i).name.isEmpty())
                ? servers.get(i).name : servers.get(i).ip;
        }
        new AlertDialog.Builder(this)
            .setTitle("选择服务器")
            .setItems(names, (d, which) -> {
                ServerManager.setActiveServerId(this, servers.get(which).id);
                updateServerDisplay();
                updateModeDisplay();
                Toast.makeText(this, "已切换到: " + names[which], Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void updateModeDisplay() {
        textMode.setText("SSH 连接模式");
    }

    /**
     * 批量启用/禁用电源控制按钮（防止重复点击）
     */
    private void setPowerButtons(boolean enabled) {
        btnPowerOn.setEnabled(enabled);
        btnPowerOff.setEnabled(enabled);
        btnPowerCycle.setEnabled(enabled);
        btnCheckStatus.setEnabled(enabled);
    }

    private void powerAction(String action) {
        String label = "on".equals(action) ? "开机" : ("off".equals(action) ? "关机" : "重启");
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }
        textStatus.setText("正在" + label + "...");
        setPowerButtons(false);
        new Thread(() -> {
            final String result = apiService.powerControl(action);
            // 优化：电源操作后清除缓存，确保下次查询获取最新状态
            IdracApiService.clearCache();
            runOnUiThread(() -> {
                textStatus.setText(result);
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                setPowerButtons(true);
            });
        }).start();
    }

    /**
     * 显示电源操作确认对话框，防止误触
     */
    private void showPowerConfirmationDialog(String action) {
        String actionLabel;
        String actionDetail;
        switch (action) {
            case "on":
                actionLabel = "开机";
                actionDetail = "确定要开启服务器电源吗？";
                break;
            case "off":
                actionLabel = "关机";
                actionDetail = "确定要关闭服务器电源吗？\n\n⚠️ 这将强制断电，可能导致数据丢失！";
                break;
            case "cycle":
                actionLabel = "重启";
                actionDetail = "确定要强制重启服务器吗？\n\n⚠️ 这将强制重启，可能导致数据丢失！";
                break;
            default:
                return;
        }

        new AlertDialog.Builder(this)
            .setTitle("⚡ 确认" + actionLabel)
            .setMessage(actionDetail)
            .setPositiveButton("确定", (dialog, which) -> powerAction(action))
            .setNegativeButton("取消", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    /**
     * 查询服务器状态：强制刷新（按钮点击时调用）
     */
    private void checkStatus() {
        Log.d(TAG, "checkStatus() called");
        checkStatusInternal(true);
    }

    /**
     * 查询服务器状态（内部方法）
     * @param forceRefresh 是否强制刷新（true=跳过缓存，false=使用缓存）
     */
    private void checkStatusInternal(boolean forceRefresh) {
        Log.d(TAG, "checkStatusInternal(" + forceRefresh + ") called");
        if (!Prefs.isConfigured(this)) {
            Log.w(TAG, "NOT CONFIGURED - showing toast");
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "CONFIGURED - ip=" + Prefs.getIpAddress(this) + " port=" + Prefs.getSshPort(this));

        // 优化：如果是强制刷新，显示"查询中..."；否则不显示（使用缓存时很快）
        if (forceRefresh) {
            textStatus.setText("查询中...");
            textCpuTemp.setText("...");
            textFanSpeed.setText("...");
            textPowerWatt.setText("...");
            textHardwareSummary.setText("获取中...");
            Log.d(TAG, "UI set to '查询中...'");
        }
        setPowerButtons(false);
        Log.d(TAG, "Buttons disabled, starting SSH thread");

        new Thread(() -> {
            try {
                Log.d(TAG, "SSH thread started, calling getAllInfo(" + forceRefresh + ")");
                // 获取所有信息（根据 forceRefresh 参数决定是否使用缓存）
                IdracApiService.SshResult[] results = apiService.getAllInfo(forceRefresh);
                Log.d(TAG, "getAllInfo returned, length=" + results.length);

                // results[0].output 已经是解析好的电源状态（由 IdracApiService.parsePowerStatusFromSysInfo() 解析）
                final String powerStatus = results[0].hasData() ? results[0].output : "查询失败";
                final String sensorData = results[1].hasData() ? results[1].output : null;
                final String hardwareData = results[2].hasData() ? results[2].output : null;
                final String cpuData = results[3].hasData() ? results[3].output : null;
                final String memoryData = results[4].hasData() ? results[4].output : null;

                Log.d(TAG, "powerStatus=" + powerStatus + " sensorData=" + (sensorData != null ? "OK(" + sensorData.length() + ")" : "null"));
                Log.d(TAG, "hardwareData=" + (hardwareData != null ? "OK(" + hardwareData.length() + ")" : "null"));

                // 合并硬件信息、CPU信息和内存信息
                final String combinedHardwareInfo = combineHardwareInfo(hardwareData, cpuData, memoryData);

                runOnUiThread(() -> {
                    Log.d(TAG, "Updating UI on UI thread");
                    // 电源状态
                    textStatus.setText(powerStatus);

                    // 传感器数据
                    if (sensorData == null || sensorData.startsWith("获取失败") || sensorData.startsWith("SSH错误")) {
                        textCpuTemp.setText("ERR");
                        textFanSpeed.setText("ERR");
                        textPowerWatt.setText("ERR");
                    } else {
                        textCpuTemp.setText(IdracApiService.parseCpuTemp(sensorData));
                        textFanSpeed.setText(IdracApiService.parseFanSpeed(sensorData));
                        textPowerWatt.setText(IdracApiService.parsePowerWatt(sensorData));
                    }

                    // 硬件信息（包含CPU和内存信息）
                    if (combinedHardwareInfo == null || combinedHardwareInfo.isEmpty()) {
                        textHardwareSummary.setText("获取失败:\n无数据");
                    } else if (combinedHardwareInfo.startsWith("获取失败") || combinedHardwareInfo.startsWith("SSH错误")) {
                        textHardwareSummary.setText(combinedHardwareInfo);
                    } else {
                        String summary = IdracApiService.parseHardwareSummary(combinedHardwareInfo);
                        // 翻译英文术语为中文
                        summary = translateHardwareInfo(summary);
                        textHardwareSummary.setText(summary);
                    }

                    setPowerButtons(true);
                    Log.d(TAG, "UI updated, buttons re-enabled");
                });
            } catch (Exception e) {
                Log.e(TAG, "Exception in SSH thread", e);
                runOnUiThread(() -> {
                    textStatus.setText("查询异常: " + e.getMessage());
                    setPowerButtons(true);
                    Toast.makeText(MainActivity.this, "查询失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 合并硬件信息、CPU信息和内存信息
     */
    private String combineHardwareInfo(String hardwareData, String cpuData, String memoryData) {
        StringBuilder sb = new StringBuilder();
        
        if (hardwareData != null && !hardwareData.isEmpty()) {
            sb.append("=== 系统信息 ===\n");
            sb.append(hardwareData.trim()).append("\n\n");
        }
        
        if (cpuData != null && !cpuData.isEmpty() && !cpuData.startsWith("获取失败") && !cpuData.startsWith("SSH错误")) {
            sb.append("=== CPU信息 ===\n");
            sb.append(cpuData.trim()).append("\n\n");
        }
        
        if (memoryData != null && !memoryData.isEmpty() && !memoryData.startsWith("获取失败") && !memoryData.startsWith("SSH错误")) {
            sb.append("=== 内存信息 ===\n");
            sb.append(memoryData.trim()).append("\n");
        }
        
        return sb.toString().trim();
    }

    /**
     * 解析电源状态结果
     */
    private String parsePowerStatus(IdracApiService.SshResult result) {
        if (!result.hasData()) {
            return result.error != null && !result.error.isEmpty() ? result.error : "查询失败";
        }

        // 从 getsysinfo 输出中解析 Power Status
        String output = result.output;
        if (output != null) {
            for (String line : output.split("\n")) {
                if (line.contains("Power Status") || line.contains("Power:")) {
                    return "电源: " + line.substring(line.indexOf(':') + 1).trim();
                }
            }
        }
        
        // 兜底：检查是否包含 on/off
        String lower = output != null ? output.toLowerCase() : "";
        if (lower.contains("on") && lower.contains("power")) {
            return "电源: ON";
        }
        if (lower.contains("off") && lower.contains("power")) {
            return "电源: OFF";
        }
        return "状态: " + (output != null ? output.trim() : "未知");
    }

    // ========== SSH 终端 ==========

    private void openSshTerminal() {
        Intent intent = new Intent(this, SshTerminalActivity.class);
        startActivity(intent);
    }

    /**
     * 翻译硬件信息中的常见英文术语为中文
     */
    private String translateHardwareInfo(String text) {
        if (text == null || text.isEmpty()) return text;
        
        String translated = text;
        // 翻译键名
        translated = translated.replaceAll("(?i)System Model", "系统型号");
        translated = translated.replaceAll("(?i)Service Tag", "服务标签");
        translated = translated.replaceAll("(?i)BIOS Version", "BIOS版本");
        translated = translated.replaceAll("(?i)Firmware Version", "固件版本");
        translated = translated.replaceAll("(?i)Power Status", "电源状态");
        translated = translated.replaceAll("(?i)Host Name", "主机名");
        translated = translated.replaceAll("(?i)OS Name", "操作系统");
        translated = translated.replaceAll("(?i)OS Version", "OS版本");
        translated = translated.replaceAll("(?i)Estimated System Airflow", "系统气流");
        translated = translated.replaceAll("(?i)Estimated Exhaust Temperature", "排气温度");
        
        // 翻译状态值
        translated = translated.replaceAll("(?i)\\bPresent\\b", "已连接");
        translated = translated.replaceAll("(?i)\\bAbsent\\b", "未安装");
        translated = translated.replaceAll("(?i)\\bOk\\b", "正常");
        translated = translated.replaceAll("(?i)\\bError\\b", "错误");
        translated = translated.replaceAll("(?i)\\bON\\b", "开启");
        translated = translated.replaceAll("(?i)\\bOFF\\b", "关闭");
        translated = translated.replaceAll("(?i)\\bActive\\b", "活跃");
        translated = translated.replaceAll("(?i)Full Redundant", "完全冗余");
        
        // 翻译传感器类型
        translated = translated.replaceAll("(?i)Sensor Type\\s*:\\s*", "传感器类型: ");
        translated = translated.replaceAll("(?i)\\bPOWER\\b", "电源");
        translated = translated.replaceAll("(?i)\\bTEMPERATURE\\b", "温度");
        translated = translated.replaceAll("(?i)\\bFAN\\b", "风扇");
        translated = translated.replaceAll("(?i)\\bVOLTAGE\\b", "电压");
        translated = translated.replaceAll("(?i)\\bCURRENT\\b", "电流");
        translated = translated.replaceAll("(?i)\\bPROCESSOR\\b", "处理器");
        translated = translated.replaceAll("(?i)\\bMEMORY\\b", "内存");
        translated = translated.replaceAll("(?i)\\bBATTERY\\b", "电池");
        translated = translated.replaceAll("(?i)\\bPERFORMANCE\\b", "性能");
        translated = translated.replaceAll("(?i)\\bINTRUSION\\b", "入侵检测");
        translated = translated.replaceAll("(?i)\\bREDUNDANCY\\b", "冗余");
        translated = translated.replaceAll("(?i)\\bSD CARD\\b", "SD卡");
        translated = translated.replaceAll("(?i)\\bSYSTEM PERFORMANCE\\b", "系统性能");
        
        return translated;
    }
}
