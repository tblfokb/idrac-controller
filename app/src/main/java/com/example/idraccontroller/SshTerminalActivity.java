package com.example.idraccontroller;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SshTerminalActivity extends Activity {

    private TextView  tvOutput;
    private EditText  etCommand;
    private Button    btnSend;
    private Button    btnConn;
    private Button    btnClear;
    private View      vDot;
    private TextView  tvStatus;
    private ScrollView svOutput;

    private Session   sshSession;
    private volatile boolean connected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh_terminal);

        tvOutput  = findViewById(R.id.text_output);
        etCommand = findViewById(R.id.edit_command);
        btnSend  = findViewById(R.id.btn_send);
        btnConn  = findViewById(R.id.btn_connect);
        btnClear = findViewById(R.id.btn_clear);
        vDot     = findViewById(R.id.status_dot);
        tvStatus = findViewById(R.id.text_connection_status);
        svOutput = findViewById(R.id.scroll_output);

        tvOutput.setMovementMethod(new ScrollingMovementMethod());

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnConn.setOnClickListener(v -> {
            if (connected) disconnect();
            else connect();
        });

        btnSend.setOnClickListener(v -> send());

        etCommand.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                send();
                return true;
            }
            return false;
        });

        btnClear.setOnClickListener(v -> {
            tvOutput.setText("");
            append("终端已清空。\n");
        });

        updateStatus();
    }

    // ======== 状态 ====

    private void updateStatus() {
        runOnUiThread(() -> {
            if (connected) {
                vDot.setBackgroundResource(R.drawable.shape_status_green);
                tvStatus.setText("已连接 · SSH");
                btnConn.setText("断开");
                etCommand.setEnabled(true);
                btnSend.setEnabled(true);
            } else {
                vDot.setBackgroundResource(R.drawable.shape_status_red);
                tvStatus.setText("未连接");
                btnConn.setText("连接");
                etCommand.setEnabled(false);
                btnSend.setEnabled(false);
            }
        });
    }

    // ======== 连接 ====

    private void connect() {
        append("正在连接...\n");
        runOnUiThread(() -> vDot.setBackgroundResource(R.drawable.shape_status_orange));

        new Thread(() -> {
            try {
                String host = Prefs.getIpAddress(this);
                if (host == null || host.isEmpty()) {
                    runOnUiThread(() -> {
                        append("错误：请先在设置中配置 iDRAC。\n");
                        updateStatus();
                    });
                    return;
                }

                String user = Prefs.getUsername(this);
                String pass = Prefs.getPassword(this);
                int    port = Prefs.getSshPort(this);

                append("连接到 " + host + ":" + port + "...\n");

                JSch jsch = new JSch();
                sshSession = jsch.getSession(user, host, port);
                sshSession.setPassword(pass);
                Properties props = new Properties();
                props.put("StrictHostKeyChecking", "no");
                sshSession.setConfig(props);
                sshSession.connect(10000);

                connected = true;
                runOnUiThread(() -> {
                    updateStatus();
                    append("✅ 连接成功！\n");
                    append("提示：直接输入命令，自动添加 racadm 前缀\n\n");
                });

            } catch (Exception e) {
                String msg = e.getMessage();
                runOnUiThread(() -> {
                    append("❌ 连接失败：" + (msg != null ? msg : "未知") + "\n");
                    updateStatus();
                });
            }
        }).start();
    }

    private void disconnect() {
        try { if (sshSession != null && sshSession.isConnected()) sshSession.disconnect(); } catch (Exception ignored) {}
        sshSession = null;
        connected = false;
        runOnUiThread(() -> {
            updateStatus();
            append("已断开连接。\n");
        });
    }

    // ======== 发送命令 ====

    private void send() {
        String cmd = etCommand.getText().toString().trim();
        if (cmd.isEmpty()) return;

        if (!connected || sshSession == null || !sshSession.isConnected()) {
            append("错误：未连接。\n");
            return;
        }

        if (cmd.equalsIgnoreCase("clear") || cmd.equalsIgnoreCase("cls")) {
            tvOutput.setText("");
            etCommand.setText("");
            return;
        }
        if (cmd.equalsIgnoreCase("exit") || cmd.equalsIgnoreCase("quit")) {
            disconnect();
            etCommand.setText("");
            return;
        }
        if (cmd.equalsIgnoreCase("help") || cmd.equals("?")) {
            showHelp();
            etCommand.setText("");
            return;
        }

        append("racadm> " + cmd + "\n");
        etCommand.setText("");

        new Thread(() -> {
            ChannelExec ch = null;
            try {
                ch = (ChannelExec) sshSession.openChannel("exec");
                String full = cmd.startsWith("racadm ") ? cmd : "racadm " + cmd;
                ch.setCommand(full);
                InputStream in = ch.getInputStream();
                ch.connect(10000);

                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                br.close();
                ch.disconnect();

                String out = sb.toString().trim();
                String cleaned = filterAnsi(out);
                int rc = ch.getExitStatus();
                String finalOut = cleaned;
                int finalRc = rc;
                runOnUiThread(() -> {
                    if (finalOut.isEmpty()) {
                        append("（无输出，退出码：" + finalRc + "）\n\n");
                    } else {
                        append(finalOut + "\n\n");
                    }
                });

            } catch (Exception e) {
                String msg = e.getMessage();
                runOnUiThread(() -> append("❌ 执行失败：" + (msg != null ? msg : "未知") + "\n\n"));
            } finally {
                try { if (ch != null && ch.isConnected()) ch.disconnect(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void showHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== iDRAC SSH 终端帮助 ===\n");
        sb.append("直接输入命令，自动添加 racadm 前缀\n");
        sb.append("示例：\n");
        sb.append("  getsysinfo        查看系统信息\n");
        sb.append("  getled             查看 LED 状态\n");
        sb.append("  serveraction -h    查看电源操作帮助\n");
        sb.append("  getfanrpm          查看风扇转速\n");
        sb.append("  gettemp            查看温度\n");
        sb.append("  getversion         查看固件版本\n");
        sb.append("  help / ?           显示本帮助\n");
        sb.append("  clear / cls        清空终端\n");
        sb.append("  exit / quit        断开连接\n");
        sb.append("\n");
        append(sb.toString());
    }

    private String filterAnsi(String s) {
        if (s == null) return "";
        return s.replaceAll("\u001B\\[[0-9;]*[a-zA-Z]", "")
                 .replace("\r", "");
    }

    // ======== UI 工具 ======

    private void append(String text) {
        runOnUiThread(() -> {
            tvOutput.append(text);
            svOutput.post(() -> svOutput.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}
