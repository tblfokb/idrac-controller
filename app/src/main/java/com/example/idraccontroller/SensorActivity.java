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
                textSensorRaw.setText(data);
            });
        }).start();
    }
}
