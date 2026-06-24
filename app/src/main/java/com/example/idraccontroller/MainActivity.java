package com.example.idraccontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;

public class MainActivity extends Activity {

    private TextView textStatus, textMode, textServerName;
    private TextView textCpuTemp, textFanSpeed, textPowerWatt;
    private Button btnPowerOn, btnPowerOff, btnPowerCycle, btnCheckStatus, btnSettings;
    private Button btnGracefulShutdown, btnGracefulRestart;
    private Button btnSwitchServer, btnAddServer, btnSensorDetail, btnHardwareInfo, btnRefreshSensors;
    private IdracApiService apiService;
    private Gson gson = new Gson();

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
        btnPowerOn = findViewById(R.id.btn_power_on);
        btnPowerOff = findViewById(R.id.btn_power_off);
        btnPowerCycle = findViewById(R.id.btn_power_cycle);
        btnCheckStatus = findViewById(R.id.btn_check_status);
        btnSettings = findViewById(R.id.btn_settings);
        btnGracefulShutdown = findViewById(R.id.btn_graceful_shutdown);
        btnGracefulRestart = findViewById(R.id.btn_graceful_restart);
        btnSwitchServer = findViewById(R.id.btn_switch_server);
        btnAddServer = findViewById(R.id.btn_add_server);
        btnSensorDetail = findViewById(R.id.btn_sensor_detail);
        btnHardwareInfo = findViewById(R.id.btn_hardware_info);
        btnRefreshSensors = findViewById(R.id.btn_refresh_sensors);

        updateServerDisplay();
        updateModeDisplay();

        btnSettings.setOnClickListener(v -> {
            Intent i = new Intent(this, SettingsActivity.class);
            ServerConfig active = ServerManager.getActiveServer(this);
            if (active != null) i.putExtra("server_id", active.id);
            startActivity(i);
        });

        btnAddServer.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnSwitchServer.setOnClickListener(v -> showServerPicker());

        btnPowerOn.setOnClickListener(v -> powerAction("on"));
        btnPowerOff.setOnClickListener(v -> powerAction("off"));
        btnPowerCycle.setOnClickListener(v -> powerAction("cycle"));
        btnCheckStatus.setOnClickListener(v -> checkStatus());
        btnGracefulShutdown.setOnClickListener(v -> gracefulAction("shutdown"));
        btnGracefulRestart.setOnClickListener(v -> gracefulAction("restart"));
        btnRefreshSensors.setOnClickListener(v -> refreshSensors());

        btnSensorDetail.setOnClickListener(v ->
            startActivity(new Intent(this, SensorActivity.class)));

        btnHardwareInfo.setOnClickListener(v ->
            startActivity(new Intent(this, HardwareInfoActivity.class)));
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
            // Migrate legacy config
            if (Prefs.isConfigured(this)) {
                ServerConfig sc = new ServerConfig();
                sc.name = "默认服务器";
                sc.ip = Prefs.getIpAddress(this);
                sc.username = Prefs.getUsername(this);
                sc.password = Prefs.getPassword(this);
                sc.mode = Prefs.getConnectionMode(this);
                sc.sshPort = Prefs.getSshPort(this);
                sc.httpsPort = Prefs.getHttpsPort(this);
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
        String mode = Prefs.getConnectionMode(this);
        String label = "ssh".equals(mode) ? "SSH · iDRAC 6/7/8" : "Redfish · iDRAC 9+";
        textMode.setText(label);
    }

    private void setPowerButtons(boolean enabled) {
        btnPowerOn.setEnabled(enabled);
        btnPowerOff.setEnabled(enabled);
        btnPowerCycle.setEnabled(enabled);
        btnGracefulShutdown.setEnabled(enabled);
        btnGracefulRestart.setEnabled(enabled);
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

    private void gracefulAction(String type) {
        String label = "shutdown".equals(type) ? "优雅关机" : "优雅重启";
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }
        textStatus.setText("正在" + label + "...");
        setPowerButtons(false);
        new Thread(() -> {
            final String result = "shutdown".equals(type) ? apiService.gracefulShutdown() : apiService.gracefulRestart();
            runOnUiThread(() -> {
                textStatus.setText(result);
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                setPowerButtons(true);
            });
        }).start();
    }

    private void checkStatus() {
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }
        textStatus.setText("查询中...");
        new Thread(() -> {
            final String status = apiService.getPowerStatus();
            runOnUiThread(() -> textStatus.setText(status));
        }).start();
    }

    private void refreshSensors() {
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            return;
        }
        textCpuTemp.setText("···");
        textFanSpeed.setText("···");
        textPowerWatt.setText("···");
        new Thread(() -> {
            final String data = apiService.getSensorData();
            runOnUiThread(() -> parseSensorData(data));
        }).start();
    }

    private void parseSensorData(String data) {
        try {
            JsonObject root = gson.fromJson(data, JsonObject.class);
            if (root.has("error")) {
                textCpuTemp.setText("ERR");
                textFanSpeed.setText("ERR");
                textPowerWatt.setText("ERR");
                return;
            }
            // Parse thermal
            float maxTemp = 0;
            if (root.has("thermal")) {
                JsonObject thermal = root.getAsJsonObject("thermal");
                if (thermal.has("Temperatures") && thermal.get("Temperatures").isJsonArray()) {
                    for (int i = 0; i < thermal.getAsJsonArray("Temperatures").size(); i++) {
                        JsonObject t = thermal.getAsJsonArray("Temperatures").get(i).getAsJsonObject();
                        if (t.has("ReadingCelsius")) {
                            float v = t.get("ReadingCelsius").getAsFloat();
                            if (v > maxTemp) maxTemp = v;
                        }
                    }
                }
                // Fan speed
                if (thermal.has("Fans") && thermal.get("Fans").isJsonArray()
                    && thermal.getAsJsonArray("Fans").size() > 0) {
                    JsonObject fan = thermal.getAsJsonArray("Fans").get(0).getAsJsonObject();
                    if (fan.has("Reading")) {
                        textFanSpeed.setText(String.valueOf(fan.get("Reading").getAsInt()));
                    }
                }
            }
            textCpuTemp.setText(maxTemp > 0 ? String.valueOf((int)maxTemp) : "--");

            // Parse power
            if (root.has("power")) {
                JsonObject power = root.getAsJsonObject("power");
                if (power.has("PowerControl") && power.get("PowerControl").isJsonArray()
                    && power.getAsJsonArray("PowerControl").size() > 0) {
                    JsonObject pc = power.getAsJsonArray("PowerControl").get(0).getAsJsonObject();
                    if (pc.has("PowerConsumedWatts")) {
                        textPowerWatt.setText(String.valueOf(pc.get("PowerConsumedWatts").getAsInt()));
                    }
                }
            }
        } catch (Exception e) {
            textCpuTemp.setText("--");
            textFanSpeed.setText("--");
            textPowerWatt.setText("--");
        }
    }
}
