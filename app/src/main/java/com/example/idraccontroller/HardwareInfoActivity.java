package com.example.idraccontroller;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

public class HardwareInfoActivity extends Activity {

    private TextView textHardware;
    private Button btnBack, btnRefresh;
    private IdracApiService apiService;

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
                textHardware.setText(data);
            });
        }).start();
    }
}
