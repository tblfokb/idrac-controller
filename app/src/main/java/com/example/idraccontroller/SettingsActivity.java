package com.example.idraccontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class SettingsActivity extends Activity {

    private EditText editServerName, editIp, editUser, editPass, editSshPort, editHttpsPort;
    private Spinner spinnerMode;
    private Button btnSave, btnDelete, btnBack;
    private String serverId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editServerName = findViewById(R.id.edit_server_name);
        editIp = findViewById(R.id.edit_ip);
        editUser = findViewById(R.id.edit_user);
        editPass = findViewById(R.id.edit_pass);
        editSshPort = findViewById(R.id.edit_ssh_port);
        editHttpsPort = findViewById(R.id.edit_https_port);
        spinnerMode = findViewById(R.id.spinner_mode);
        btnSave = findViewById(R.id.btn_save);
        btnDelete = findViewById(R.id.btn_delete);
        btnBack = findViewById(R.id.btn_back);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
            R.array.mode_array, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerMode.setAdapter(adapter);

        serverId = getIntent().getStringExtra("server_id");
        loadSettings();

        btnBack.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> confirmDelete());
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        if (serverId != null && !serverId.isEmpty()) {
            java.util.List<ServerConfig> list = ServerManager.getServers(this);
            for (ServerConfig s : list) {
                if (s.id.equals(serverId)) {
                    editServerName.setText(s.name);
                    editIp.setText(s.ip);
                    editUser.setText(s.username);
                    editPass.setText(s.password);
                    editSshPort.setText(String.valueOf(s.sshPort));
                    editHttpsPort.setText(String.valueOf(s.httpsPort));
                    spinnerMode.setSelection("redfish".equals(s.mode) ? 1 : 0);
                    btnDelete.setVisibility(View.VISIBLE);
                    return;
                }
            }
        }
        // New server or legacy - load from Prefs
        editIp.setText(Prefs.getIpAddress(this));
        editUser.setText(Prefs.getUsername(this));
        editPass.setText(Prefs.getPassword(this));
        editSshPort.setText(String.valueOf(Prefs.getSshPort(this)));
        editHttpsPort.setText(String.valueOf(Prefs.getHttpsPort(this)));
        String mode = Prefs.getConnectionMode(this);
        spinnerMode.setSelection("redfish".equals(mode) ? 1 : 0);
        btnDelete.setVisibility(View.GONE);
    }

    private void saveSettings() {
        String name = editServerName.getText().toString().trim();
        String ip = editIp.getText().toString().trim();
        String user = editUser.getText().toString().trim();
        String pass = editPass.getText().toString().trim();
        String sshPortStr = editSshPort.getText().toString().trim();
        String httpsPortStr = editHttpsPort.getText().toString().trim();
        String mode = spinnerMode.getSelectedItemPosition() == 1 ? "redfish" : "ssh";

        if (ip.isEmpty()) {
            Toast.makeText(this, "请输入 iDRAC IP 地址", Toast.LENGTH_SHORT).show();
            return;
        }

        int sshPort = sshPortStr.isEmpty() ? 22 : Integer.parseInt(sshPortStr);
        int httpsPort = httpsPortStr.isEmpty() ? 443 : Integer.parseInt(httpsPortStr);

        if (name.isEmpty()) name = ip;

        ServerConfig sc = new ServerConfig(
            serverId != null ? serverId : "",
            name, ip,
            user.isEmpty() ? "root" : user,
            pass, mode, sshPort, httpsPort
        );

        if (serverId != null && !serverId.isEmpty()) {
            ServerManager.updateServer(this, sc);
        } else {
            sc = ServerManager.addServer(this, sc);
            ServerManager.setActiveServerId(this, sc.id);
        }

        // Also update Prefs for backward compat
        ServerManager.applyActiveToPrefs(this);

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这台服务器配置吗？")
            .setPositiveButton("删除", (d, w) -> {
                ServerManager.deleteServer(this, serverId);
                ServerManager.applyActiveToPrefs(this);
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                finish();
            })
            .setNegativeButton("取消", null)
            .show();
    }
}
