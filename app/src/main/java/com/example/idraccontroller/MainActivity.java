package com.example.idraccontroller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

public class MainActivity extends Activity {

    private TextView textStatus, textMode;
    private Button btnPowerOn, btnPowerOff, btnPowerCycle, btnCheckStatus, btnSettings;
    private IdracApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        apiService = new IdracApiService(this);
        textStatus = findViewById(R.id.text_status);
        textMode = findViewById(R.id.text_mode);
        btnPowerOn = findViewById(R.id.btn_power_on);
        btnPowerOff = findViewById(R.id.btn_power_off);
        btnPowerCycle = findViewById(R.id.btn_power_cycle);
        btnCheckStatus = findViewById(R.id.btn_check_status);
        btnSettings = findViewById(R.id.btn_settings);

        updateModeDisplay();

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnPowerOn.setOnClickListener(v -> powerAction("on"));
        btnPowerOff.setOnClickListener(v -> powerAction("off"));
        btnPowerCycle.setOnClickListener(v -> powerAction("cycle"));
        btnCheckStatus.setOnClickListener(v -> checkStatus());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateModeDisplay();
    }

    private void updateModeDisplay() {
        String mode = Prefs.getConnectionMode(this);
        String label = "ssh".equals(mode) ? "SSH（iDRAC 6/7/8）" : "Redfish API（iDRAC 9+）";
        textMode.setText(label);
    }

    private void powerAction(String action) {
        String label = "on".equals(action) ? "开机" : ("off".equals(action) ? "关机" : "重启");

        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        textStatus.setText("正在" + label + "...");
        btnPowerOn.setEnabled(false);
        btnPowerOff.setEnabled(false);
        btnPowerCycle.setEnabled(false);

        new Thread(() -> {
            final String result = apiService.powerControl(action);
            runOnUiThread(() -> {
                textStatus.setText(result);
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                btnPowerOn.setEnabled(true);
                btnPowerOff.setEnabled(true);
                btnPowerCycle.setEnabled(true);
            });
        }).start();
    }

    private void checkStatus() {
        if (!Prefs.isConfigured(this)) {
            Toast.makeText(this, "请先设置 iDRAC 连接信息", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        textStatus.setText("正在查询状态...");
        new Thread(() -> {
            final String status = apiService.getPowerStatus();
            runOnUiThread(() -> textStatus.setText(status));
        }).start();
    }
}
