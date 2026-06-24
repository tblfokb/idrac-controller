package com.example.idraccontroller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class SettingsActivity extends Activity {

    private EditText editIp, editUser, editPass, editSshPort, editHttpsPort;
    private Spinner spinnerMode;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editIp = findViewById(R.id.edit_ip);
        editUser = findViewById(R.id.edit_user);
        editPass = findViewById(R.id.edit_pass);
        editSshPort = findViewById(R.id.edit_ssh_port);
        editHttpsPort = findViewById(R.id.edit_https_port);
        spinnerMode = findViewById(R.id.spinner_mode);
        btnSave = findViewById(R.id.btn_save);

        // Setup spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
            R.array.mode_array, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerMode.setAdapter(adapter);

        loadSettings();

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
    }

    private void loadSettings() {
        editIp.setText(Prefs.getIpAddress(this));
        editUser.setText(Prefs.getUsername(this));
        editPass.setText(Prefs.getPassword(this));
        editSshPort.setText(String.valueOf(Prefs.getSshPort(this)));
        editHttpsPort.setText(String.valueOf(Prefs.getHttpsPort(this)));
        String mode = Prefs.getConnectionMode(this);
        spinnerMode.setSelection("redfish".equals(mode) ? 1 : 0);
    }

    private void saveSettings() {
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
        if (pass.isEmpty()) {
            Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
            return;
        }

        int sshPort = sshPortStr.isEmpty() ? 22 : Integer.parseInt(sshPortStr);
        int httpsPort = httpsPortStr.isEmpty() ? 443 : Integer.parseInt(httpsPortStr);

        Prefs.setIpAddress(this, ip);
        Prefs.setUsername(this, user.isEmpty() ? "root" : user);
        Prefs.setPassword(this, pass);
        Prefs.setConnectionMode(this, mode);
        Prefs.setSshPort(this, sshPort);
        Prefs.setHttpsPort(this, httpsPort);

        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
        finish();
    }
}
