package com.example.idraccontroller;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class HardwareInfoActivity extends Activity {

    private TextView textHardware;
    private Button btnBack, btnRefresh;
    private IdracApiService apiService;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hardware);

        apiService = new IdracApiService(this);
        textHardware = findViewById(R.id.text_hardware);
        btnBack = findViewById(R.id.btn_back);
        btnRefresh = findViewById(R.id.btn_refresh);

        btnBack.setOnClickListener(v -> finish());
        btnRefresh.setOnClickListener(v -> refreshData());
    }

    private void refreshData() {
        if (!Prefs.isConfigured(this)) {
            textHardware.setText("请先在主界面配置 iDRAC 连接信息");
            return;
        }
        textHardware.setText("正在获取硬件信息...");
        new Thread(() -> {
            final String data = apiService.getHardwareInfo();
            runOnUiThread(() -> {
                String mode = Prefs.getConnectionMode(this);
                if ("redfish".equals(mode)) {
                    textHardware.setText(formatHardwareData(data));
                } else {
                    textHardware.setText(data);
                }
            });
        }).start();
    }

    private String formatHardwareData(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root.has("error")) return "错误: " + root.get("error").getAsString();

            StringBuilder sb = new StringBuilder();

            sb.append("═════ 系统信息 ═════\n\n");
            if (root.has("Manufacturer"))
                sb.append("  制造商:  ").append(root.get("Manufacturer").getAsString()).append("\n");
            if (root.has("Model"))
                sb.append("  型号:    ").append(root.get("Model").getAsString()).append("\n");
            if (root.has("BiosVersion"))
                sb.append("  BIOS:    ").append(root.get("BiosVersion").getAsString()).append("\n");

            sb.append("\n═════ CPU ═════\n\n");
            if (root.has("ProcessorSummary")) {
                JsonObject cpu = root.getAsJsonObject("ProcessorSummary");
                if (cpu.has("Model"))
                    sb.append("  型号:    ").append(cpu.get("Model").getAsString()).append("\n");
                if (cpu.has("Count"))
                    sb.append("  数量:    ").append(cpu.get("Count").getAsInt()).append("\n");
                if (cpu.has("LogicalProcessorCount"))
                    sb.append("  逻辑核:  ").append(cpu.get("LogicalProcessorCount").getAsInt()).append("\n");
            }

            sb.append("\n═════ 内存 ═════\n\n");
            if (root.has("MemorySummary")) {
                JsonObject mem = root.getAsJsonObject("MemorySummary");
                if (mem.has("TotalSystemMemoryGiB"))
                    sb.append("  总容量:  ").append(String.format("%.0f GB", mem.get("TotalSystemMemoryGiB").getAsFloat())).append("\n");
                if (mem.has("Status")) {
                    com.google.gson.JsonObject statusObj = mem.getAsJsonObject("Status");
                    if (statusObj != null && statusObj.has("Health"))
                        sb.append("  状态:    ").append(statusObj.get("Health").getAsString()).append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "解析错误: " + e.getMessage();
        }
    }
}
