package com.example.idraccontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.*;
import java.util.List;

public class MainActivity extends Activity {

    private TextView textStatus, textMode, textServerName;
    private TextView textCpuTemp, textFanSpeed, textPowerWatt;
    private TextView textHardwareSummary;
    private Button btnPowerOn, btnPowerOff, btnPowerCycle, btnCheckStatus, btnSettings;
    private Button btnSwitchServer, btnAddServer, btnSshTerminal;
    private IdracApiService apiService;

    // 风扇调速相关
    private SeekBar seekbarFan;
    private TextView textFanPercent;
    private Button btnApplyFan, btnAutoFan;
    private TextView textFanStatus;
    private int currentFanPercent = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiService = new IdracApiService(this);
        textStatus = findViewById(R.id.text_status);
        textMode = findViewById(R.id.text_mode);
        textServerName = findViewById(R.id.text_server_name);
        textCpuTemp = findViewById(R.id.text_cpu_temp);
        textFanSpeed = findViewById(R.id.text_fan_speed);
        textPowerWatt = findViewById(R.id.text_power_watt);
        textHardwareSummary = findViewById(R.id.text_hardware_summary);
        // 让硬件信息TextView可以滚动
        textHardwareSummary.setMovementMethod(new ScrollingMovementMethod());
        btnPowerOn = findViewById(R.id.btn_power_on);
        btnPowerOff = findViewById(R.id.btn_power_off);
        btnPowerCycle = findViewById(R.id.btn_power_cycle);
        btnCheckStatus = findViewById(R.id.btn_check_status);
        btnSettings = findViewById(R.id.btn_settings);
        btnSwitchServer = findViewById(R.id.btn_switch_server);
        btnAddServer = findViewById(R.id.btn_add_server);
        btnSshTerminal = findViewById(R.id.btn_ssh_terminal);

        // 风扇调速控件初始化
        seekbarFan = findViewById(R.id.seekbar_fan);
        textFanPercent = findViewById(R.id.text_fan_percent);
        btnApplyFan = findViewById(R.id.btn_apply_fan);
        btnAutoFan = findViewById(R.id.btn_auto_fan);
        textFanStatus = findViewById(R.id.text_fan_status);

        // 风扇调速拖动条监听
        seekbarFan.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentFanPercent = progress;
                textFanPercent.setText(progress + "%");
                // 根据百分比给出建议
                if (progress < 20) {
                    textFanStatus.setText("⚠️ 转速较低，注意散热");
                    textFanStatus.setTextColor(getResources().getColor(R.color.warning));
                } else if (progress > 80) {
                    textFanStatus.setText("⚠️ 转速较高，噪音和功耗增加");
                    textFanStatus.setTextColor(getResources().getColor(R.color.warning));
                } else {
                    textFanStatus.setText("✅ 推荐转速范围");
                    textFanStatus.setTextColor(getResources().getColor(R.color.text_dim));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 应用风扇转速按钮
        btnApplyFan.setOnClickListener(v -> applyFanSpeed());

        // 恢复自动调速按钮
        btnAutoFan.setOnClickListener(v -> setFanAuto());

        updateServerDisplay();
        updateModeDisplay();

        btnSettings.setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            ServerConfig active = ServerManager.getActiveServer(this);
            if (active != null) i.putExtra("server_id", active.id);
            startActivity(i);
        });

        btnSshTerminal.setOnClickListener(v -> openSshTerminal());

        btnAddServer.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnSwitchServer.setOnClickListener(v -> showServerPicker());

        btnPowerOn.setOnClickListener(v -> showPowerConfirmationDialog("on"));
        btnPowerOff.setOnClickListener(v -> showPowerConfirmationDialog("off"));
        btnPowerCycle.setOnClickListener(v -> showPowerConfirmationDialog("cycle"));
        btnCheckStatus.setOnClickListener(v -> checkStatus());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServerDisplay();
        updateModeDisplay();
    }

    private void updateServerDisplay() {
        ServerManager.applyActiveToPrefs(this);
        ServerConfig active = ServerManager.getActiveServer(this);
        if (active != null) {
            textServerName.setText(active.name != null && !active.name.isEmpty() ? active.name : active.ip);
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
            } else {
                textServerName.setText("未配置");
            }
        }
    }

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
     * 查询服务器状态：一次性获取电源状态 + 传感器信息 + 硬件信息 + CPU信息 + 内存信息（性能优化）
     */
    private void checkStatus() {
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }
        textStatus.setText("查询中...");
        textCpuTemp.setText("...");
        textFanSpeed.setText("...");
        textPowerWatt.setText("...");
        textHardwareSummary.setText("获取中...");
        setPowerButtons(false);

        new Thread(() -> {
            // 一次SSH会话获取所有信息（电源状态+传感器+硬件信息+CPU+内存）
            IdracApiService.SshResult[] results = apiService.getAllInfo();

            final String powerStatus = parsePowerStatus(results[0]);
            final String sensorData = results[1].hasData() ? results[1].output : null;
            final String hardwareData = results[2].hasData() ? results[2].output : null;
            final String cpuData = results[3].hasData() ? results[3].output : null;
            final String memoryData = results[4].hasData() ? results[4].output : null;

            // 合并硬件信息、CPU信息和内存信息
            final String combinedHardwareInfo = combineHardwareInfo(hardwareData, cpuData, memoryData);

            runOnUiThread(() -> {
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
                    textHardwareSummary.setText(summary);
                }

                setPowerButtons(true);
            });
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

        String output = result.output.toLowerCase();
        if (output.contains("on") && (output.contains("power") || output.contains("currently"))) {
            return "电源: ON";
        }
        if (output.contains("off") && (output.contains("power") || output.contains("currently"))) {
            return "电源: OFF";
        }
        return "状态: " + result.output.trim();
    }

    // ========== SSH 终端 ==========

    private void openSshTerminal() {
        Intent intent = new Intent(this, SshTerminalActivity.class);
        startActivity(intent);
    }

    // ========== 风扇调速 ==========

    /**
     * 应用风扇转速设置
     */
    private void applyFanSpeed() {
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 确认对话框
        new AlertDialog.Builder(this)
            .setTitle("🌪 确认调速")
            .setMessage("确定要将风扇转速设置为 " + currentFanPercent + "% 吗？\n\n" +
                        "⚠️ 过低可能导致过热！\n" +
                        "⚠️ 过高会增加噪音和功耗！")
            .setPositiveButton("确定", (dialog, which) -> {
                textFanStatus.setText("正在设置风扇转速...");
                textFanStatus.setTextColor(getResources().getColor(R.color.text_dim));
                btnApplyFan.setEnabled(false);
                btnAutoFan.setEnabled(false);

                new Thread(() -> {
                    final String result = apiService.setFanSpeed(currentFanPercent);
                    runOnUiThread(() -> {
                        textFanStatus.setText(result);
                        if (result.contains("✅")) {
                            textFanStatus.setTextColor(getResources().getColor(R.color.green));
                        } else {
                            textFanStatus.setTextColor(getResources().getColor(R.color.warning));
                        }
                        btnApplyFan.setEnabled(true);
                        btnAutoFan.setEnabled(true);
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                    });
                }).start();
            })
            .setNegativeButton("取消", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    /**
     * 恢复风扇自动调速
     */
    private void setFanAuto() {
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("🔄 恢复自动调速")
            .setMessage("确定要恢复风扇自动调速吗？\n\n系统将根据温度自动调节风扇转速。")
            .setPositiveButton("确定", (dialog, which) -> {
                textFanStatus.setText("正在恢复自动调速...");
                textFanStatus.setTextColor(getResources().getColor(R.color.text_dim));
                btnApplyFan.setEnabled(false);
                btnAutoFan.setEnabled(false);

                new Thread(() -> {
                    final String result = apiService.setFanAuto();
                    runOnUiThread(() -> {
                        textFanStatus.setText(result);
                        if (result.contains("✅")) {
                            textFanStatus.setTextColor(getResources().getColor(R.color.green));
                            // 恢复自动后，将滑块重置为50%
                            seekbarFan.setProgress(50);
                        } else {
                            textFanStatus.setTextColor(getResources().getColor(R.color.warning));
                        }
                        btnApplyFan.setEnabled(true);
                        btnAutoFan.setEnabled(true);
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                    });
                }).start();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
