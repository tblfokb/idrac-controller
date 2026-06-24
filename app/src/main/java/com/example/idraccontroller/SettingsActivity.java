package com.example.idraccontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class SettingsActivity extends Activity {

    private EditText editServerName, editIp, editUser, editPass, editSshPort;
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
        btnSave = findViewById(R.id.btn_save);
        btnDelete = findViewById(R.id.btn_delete);
        btnBack = findViewById(R.id.btn_back);

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
                    btnDelete.setVisibility(View.VISIBLE);
                    return;
                }
            }
        }
        editIp.setText(Prefs.getIpAddress(this));
        editUser.setText(Prefs.getUsername(this));
        editPass.setText(Prefs.getPassword(this));
        editSshPort.setText(String.valueOf(Prefs.getSshPort(this)));
        btnDelete.setVisibility(View.GONE);
    }

    private void saveSettings() {
        String name = editServerName.getText().toString().trim();
        String ip = editIp.getText().toString().trim();
        String user = editUser.getText().toString().trim();
        String pass = editPass.getText().toString().trim();
        String sshPortStr = editSshPort.getText().toString().trim();

        if (ip.isEmpty()) {
            Toast.makeText(this, "\u8bf7\u8f93\u5165 iDRAC IP \u5730\u5740", Toast.LENGTH_SHORT).show();
            return;
        }

        int sshPort = sshPortStr.isEmpty() ? 22 : Integer.parseInt(sshPortStr);
        if (name.isEmpty()) name = ip;

        ServerConfig sc = new ServerConfig(
            serverId != null ? serverId : "",
            name, ip,
            user.isEmpty() ? "root" : user,
            pass, sshPort
        );

        if (serverId != null && !serverId.isEmpty()) {
            ServerManager.updateServer(this, sc);
        } else {
            sc = ServerManager.addServer(this, sc);
            ServerManager.setActiveServerId(this, sc.id);
        }

        ServerManager.applyActiveToPrefs(this);
        Toast.makeText(this, "\u4fdd\u5b58\u6210\u529f", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
            .setTitle("\u786e\u8ba4\u5220\u9664")
            .setMessage("\u786e\u5b9a\u8981\u5220\u9664\u8fd9\u53f0\u670d\u52a1\u5668\u914d\u7f6e\u5417\uff1f")
            .setPositiveButton("\u5220\u9664", (d, w) -> {
                ServerManager.deleteServer(this, serverId);
                ServerManager.applyActiveToPrefs(this);
                Toast.makeText(this, "\u5df2\u5220\u9664", Toast.LENGTH_SHORT).show();
                finish();
            })
            .setNegativeButton("\u53d6\u6d88", null)
            .show();
    }
}
