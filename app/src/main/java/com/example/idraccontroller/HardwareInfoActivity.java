package com.example.idraccontroller;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

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

    @Override
    protected void onResume() {
        super.onResume();
        if (textHardware.getText().toString().isEmpty() || textHardware.getText().toString().equals("正在获取硬件信息...")) {
            refreshData();
        }
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
                    textHardware.setText(formatSshHardwareData(data));
                }
            });
        }).start();
    }

    private String formatHardwareData(String json) {
        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root.has("error")) {
                return "获取失败\n\n" + root.get("error").getAsString() +
                       "\n\n提示: 请确认:\n" +
                       "1. iDRAC 固件版本支持 Redfish\n" +
                       "2. IP/用户名/密码正确\n" +
                       "3. 端口设置正确 (默认 443)\n" +
                       "4. 使用 Redfish 连接模式";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("═══ 系统信息 ═══\n\n");
            if (root.has("Manufacturer"))
                sb.append("  制造商:    ").append(root.get("Manufacturer").getAsString()).append("\n");
            if (root.has("Model"))
                sb.append("  型号:      ").append(root.get("Model").getAsString()).append("\n");
            if (root.has("SystemType"))
                sb.append("  类型:      ").append(root.get("SystemType").getAsString()).append("\n");
            if (root.has("AssetTag"))
                sb.append("  资产标签:  ").append(root.get("AssetTag").getAsString()).append("\n");
            if (root.has("BiosVersion"))
                sb.append("  BIOS:      ").append(root.get("BiosVersion").getAsString()).append("\n");

            sb.append("\n═══ CPU ═══\n\n");
            if (root.has("ProcessorSummary")) {
                JsonObject cpu = root.getAsJsonObject("ProcessorSummary");
                if (cpu.has("Model"))
                    sb.append("  型号:      ").append(cpu.get("Model").getAsString()).append("\n");
                if (cpu.has("Count"))
                    sb.append("  数量:      ").append(cpu.get("Count").getAsInt()).append("\n");
                if (cpu.has("LogicalProcessorCount"))
                    sb.append("  逻辑核:    ").append(cpu.get("LogicalProcessorCount").getAsInt()).append("\n");
                if (cpu.has("Status")) {
                    JsonObject st = cpu.getAsJsonObject("Status");
                    if (st != null && st.has("Health"))
                        sb.append("  健康状态:  ").append(st.get("Health").getAsString()).append("\n");
                }
            }

            sb.append("\n═══ 内存 ═══\n\n");
            if (root.has("MemorySummary")) {
                JsonObject mem = root.getAsJsonObject("MemorySummary");
                if (mem.has("TotalSystemMemoryGiB"))
                    sb.append("  总容量:    ").append(String.format("%.0f GB", mem.get("TotalSystemMemoryGiB").getAsFloat())).append("\n");
                if (mem.has("TotalSystemPersistentMemoryGiB")) {
                    float pm = mem.get("TotalSystemPersistentMemoryGiB").getAsFloat();
                    if (pm > 0) sb.append("  持久内存:  ").append(String.format("%.0f GB", pm)).append("\n");
                }
                if (mem.has("Status")) {
                    JsonObject st = mem.getAsJsonObject("Status");
                    if (st != null && st.has("Health"))
                        sb.append("  健康状态:  ").append(st.get("Health").getAsString()).append("\n");
                }
            }

            // Storage devices
            if (root.has("StorageDevices") && root.get("StorageDevices").isJsonArray()) {
                JsonArray devices = root.getAsJsonArray("StorageDevices");
                sb.append("\n═══ 存储设备 (").append(devices.size()).append(") ═══\n\n");
                for (int i = 0; i < devices.size(); i++) {
                    JsonObject dev = devices.get(i).getAsJsonObject();
                    String name = dev.has("Name") ? dev.get("Name").getAsString() : "Disk " + i;
                    sb.append("  ").append(name).append("\n");
                    if (dev.has("Model"))
                        sb.append("    型号: ").append(dev.get("Model").getAsString()).append("\n");
                    if (dev.has("CapacityBytes")) {
                        long cap = dev.get("CapacityBytes").getAsLong();
                        sb.append("    容量: ").append(formatBytes(cap)).append("\n");
                    }
                    if (dev.has("Status")) {
                        JsonObject st = dev.getAsJsonObject("Status");
                        if (st != null && st.has("Health"))
                            sb.append("    状态: ").append(st.get("Health").getAsString()).append("\n");
                    }
                    sb.append("\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "解析错误: " + e.getMessage() + "\n\n原始数据:\n" + json.substring(0, Math.min(json.length(), 500));
        }
    }

    private String formatSshHardwareData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return "未获取到数据\n\n请确认:\n1. SSH 模式已正确配置\n2. iDRAC 支持 SSH (iDRAC 6/7/8)\n3. 用户名和密码正确\n4. SSH 端口设置正确 (默认 22)";
        }
        return data;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1000) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1000) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1000) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        if (gb < 1000) return String.format("%.1f GB", gb);
        return String.format("%.1f TB", gb / 1024.0);
    }
}
