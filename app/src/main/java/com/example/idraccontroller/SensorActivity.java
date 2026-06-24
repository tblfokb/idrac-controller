package com.example.idraccontroller;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

public class SensorActivity extends Activity {

    private TextView textSensorRaw;
    private Button btnBack, btnRefresh;
    private IdracApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        apiService = new IdracApiService(this);
        textSensorRaw = findViewById(R.id.text_sensor_raw);
        btnBack = findViewById(R.id.btn_back);
        btnRefresh = findViewById(R.id.btn_refresh);

        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> refreshData());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (textSensorRaw.getText().toString().isEmpty() || textSensorRaw.getText().toString().equals("正在获取传感器数据...")) {
            refreshData();
        }
    }

    private void refreshData() {
        if (!Prefs.isConfigured(this)) {
            textSensorRaw.setText("请先在主界面配置 iDRAC 连接信息");
            return;
        }
        textSensorRaw.setText("正在获取传感器数据...");
        new Thread(() -> {
            final String data = apiService.getSensorData();
            runOnUiThread(() -> {
                String mode = Prefs.getConnectionMode(this);
                if ("redfish".equals(mode)) {
                    textSensorRaw.setText(formatRedfishSensorData(data));
                } else {
                    textSensorRaw.setText(formatSshSensorData(data));
                }
            });
        }).start();
    }

    private String formatRedfishSensorData(String json) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonObject root = gson.fromJson(json, com.google.gson.JsonObject.class);
            if (root.has("error")) {
                return "获取失败\n\n" + root.get("error").getAsString() +
                       "\n\n提示: 请确认:\n" +
                       "1. iDRAC 固件版本支持 Redfish\n" +
                       "2. IP/用户名/密码正确\n" +
                       "3. 端口设置正确 (默认 443)\n" +
                       "4. 网络可以连通 iDRAC";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("═══ 温度传感器 ═══\n\n");

            if (root.has("thermal")) {
                com.google.gson.JsonObject thermal = root.getAsJsonObject("thermal");
                if (thermal.has("Temperatures") && thermal.get("Temperatures").isJsonArray()) {
                    int count = thermal.getAsJsonArray("Temperatures").size();
                    for (int i = 0; i < count; i++) {
                        com.google.gson.JsonObject t = thermal.getAsJsonArray("Temperatures").get(i).getAsJsonObject();
                        String name = t.has("Name") ? t.get("Name").getAsString() : "Sensor " + i;
                        float val = t.has("ReadingCelsius") ? t.get("ReadingCelsius").getAsFloat() : 0;
                        sb.append(String.format("  %-24s %5.0f °C\n", truncate(name, 24), val));
                    }
                    if (count == 0) sb.append("  (无温度传感器数据)\n");
                } else {
                    sb.append("  (温度数据不可用)\n");
                }
            }

            sb.append("\n═══ 风扇转速 ═══\n\n");
            if (root.has("thermal")) {
                com.google.gson.JsonObject thermal = root.getAsJsonObject("thermal");
                if (thermal.has("Fans") && thermal.get("Fans").isJsonArray()) {
                    int count = thermal.getAsJsonArray("Fans").size();
                    for (int i = 0; i < count; i++) {
                        com.google.gson.JsonObject f = thermal.getAsJsonArray("Fans").get(i).getAsJsonObject();
                        String name = f.has("Name") ? f.get("Name").getAsString() : "Fan " + i;
                        int val = f.has("Reading") ? f.get("Reading").getAsInt() : 0;
                        String unit = f.has("ReadingUnits") ? f.get("ReadingUnits").getAsString() : "RPM";
                        sb.append(String.format("  %-24s %5d %s\n", truncate(name, 24), val, unit));
                    }
                    if (count == 0) sb.append("  (无风扇数据)\n");
                } else {
                    sb.append("  (风扇数据不可用)\n");
                }
            }

            sb.append("\n═══ 电源信息 ═══\n\n");
            if (root.has("power")) {
                com.google.gson.JsonObject power = root.getAsJsonObject("power");
                if (power.has("PowerControl") && power.get("PowerControl").isJsonArray()
                    && power.getAsJsonArray("PowerControl").size() > 0) {
                    com.google.gson.JsonObject pc = power.getAsJsonArray("PowerControl").get(0).getAsJsonObject();
                    int consumed = pc.has("PowerConsumedWatts") ? pc.get("PowerConsumedWatts").getAsInt() : 0;
                    int capacity = pc.has("PowerCapacityWatts") ? pc.get("PowerCapacityWatts").getAsInt() : 0;
                    sb.append(String.format("  %-24s %4d W\n", "当前功耗", consumed));
                    sb.append(String.format("  %-24s %4d W\n", "额定功率", capacity));
                } else {
                    sb.append("  (功耗数据不可用)\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "解析错误: " + e.getMessage() + "\n\n原始数据:\n" + json.substring(0, Math.min(json.length(), 500));
        }
    }

    private String formatSshSensorData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return "未获取到数据\n\n请确认:\n1. SSH 模式已正确配置\n2. iDRAC 支持 SSH (iDRAC 6/7/8)\n3. 用户名和密码正确";
        }
        return data;
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 2) + "..";
    }
}
