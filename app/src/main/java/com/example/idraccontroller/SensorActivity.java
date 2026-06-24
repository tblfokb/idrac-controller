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
                    textSensorRaw.setText(data);
                }
            });
        }).start();
    }

    private String formatRedfishSensorData(String json) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonObject root = gson.fromJson(json, com.google.gson.JsonObject.class);
            if (root.has("error")) return "错误: " + root.get("error").getAsString();

            StringBuilder sb = new StringBuilder();
            sb.append("═════ 温度传感器 ═════\n\n");

            if (root.has("thermal")) {
                com.google.gson.JsonObject thermal = root.getAsJsonObject("thermal");
                if (thermal.has("Temperatures") && thermal.get("Temperatures").isJsonArray()) {
                    for (int i = 0; i < thermal.getAsJsonArray("Temperatures").size(); i++) {
                        com.google.gson.JsonObject t = thermal.getAsJsonArray("Temperatures").get(i).getAsJsonObject();
                        String name = t.has("Name") ? t.get("Name").getAsString() : "Sensor " + i;
                        float val = t.has("ReadingCelsius") ? t.get("ReadingCelsius").getAsFloat() : 0;
                        sb.append(String.format("  %-20s %5.0f °C\n", name, val));
                    }
                }
            }

            sb.append("\n═════ 风扇转速 ═════\n\n");
            if (root.has("thermal")) {
                com.google.gson.JsonObject thermal = root.getAsJsonObject("thermal");
                if (thermal.has("Fans") && thermal.get("Fans").isJsonArray()) {
                    for (int i = 0; i < thermal.getAsJsonArray("Fans").size(); i++) {
                        com.google.gson.JsonObject f = thermal.getAsJsonArray("Fans").get(i).getAsJsonObject();
                        String name = f.has("Name") ? f.get("Name").getAsString() : "Fan " + i;
                        int val = f.has("Reading") ? f.get("Reading").getAsInt() : 0;
                        sb.append(String.format("  %-20s %5d RPM\n", name, val));
                    }
                }
            }

            sb.append("\n═════ 电源信息 ═════\n\n");
            if (root.has("power")) {
                com.google.gson.JsonObject power = root.getAsJsonObject("power");
                if (power.has("PowerControl") && power.get("PowerControl").isJsonArray()
                    && power.getAsJsonArray("PowerControl").size() > 0) {
                    com.google.gson.JsonObject pc = power.getAsJsonArray("PowerControl").get(0).getAsJsonObject();
                    int consumed = pc.has("PowerConsumedWatts") ? pc.get("PowerConsumedWatts").getAsInt() : 0;
                    int capacity = pc.has("PowerCapacityWatts") ? pc.get("PowerCapacityWatts").getAsInt() : 0;
                    sb.append(String.format("  %-20s %4d W\n", "当前功耗", consumed));
                    sb.append(String.format("  %-20s %4d W\n", "额定功率", capacity));
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "解析错误: " + e.getMessage();
        }
    }
}
