package com.example.idraccontroller;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import okhttp3.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

public class IdracApiService {

    private static final String TAG = "IdracApiService";
    private Context context;
    private Gson gson = new Gson();

    public IdracApiService(Context ctx) {
        this.context = ctx;
    }

    // ===================== Redfish API =====================

    private OkHttpClient getClient() {
        try {
            // Trust all certs (self-signed)
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS);
            builder.hostnameVerifier((h, s) -> true);
            builder.sslSocketFactory(sc.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAll[0]);
            return builder.build();
        } catch (Exception e) {
            return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .hostnameVerifier((h, s) -> true)
                .build();
        }
    }

    private String getAuth() {
        String user = Prefs.getUsername(context);
        String pwd = Prefs.getPassword(context);
        return Credentials.basic(user, pwd);
    }

    private String getBaseUrl() {
        int port = Prefs.getHttpsPort(context);
        if (port == 443) {
            return "https://" + Prefs.getIpAddress(context);
        }
        return "https://" + Prefs.getIpAddress(context) + ":" + port;
    }

    // ===================== SSH =====================

    private static class SshResult {
        boolean success;
        String output;
        String error;
        int exitCode;
        SshResult(boolean s, String o, String e, int c) { success=s; output=o; error=e; exitCode=c; }
    }

    private SshResult runSshCommand(String command) {
        try {
            JSch jsch = new JSch();
            String ip = Prefs.getIpAddress(context);
            String user = Prefs.getUsername(context);
            String pwd = Prefs.getPassword(context);

            com.jcraft.jsch.Session session = jsch.getSession(user, ip, Prefs.getSshPort(context));
            session.setPassword(pwd);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(15000);
            session.connect();

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errOutput = new ByteArrayOutputStream();
            channel.setOutputStream(output);
            channel.setErrStream(errOutput);

            channel.connect();
            while (!channel.isClosed()) {
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            channel.disconnect();
            session.disconnect();

            return new SshResult(exitCode == 0, output.toString("UTF-8").trim(), errOutput.toString("UTF-8").trim(), exitCode);
        } catch (Exception e) {
            return new SshResult(false, "", e.getMessage() != null ? e.getMessage() : "SSH 连接失败", -1);
        }
    }

    // ===================== 统一接口 =====================

    public String getPowerStatus() {
        if ("redfish".equals(Prefs.getConnectionMode(context))) {
            return getPowerStatusRedfish();
        } else {
            return getPowerStatusSsh();
        }
    }

    private String getPowerStatusRedfish() {
        try {
            OkHttpClient client = getClient();
            String url = getBaseUrl() + "/redfish/v1/Systems/System.Embedded.1";
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuth())
                .header("Accept", "application/json")
                .build();
            Response response = client.newCall(request).execute();
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) return "查询失败: " + response.code() + " " + body.substring(0, Math.min(200, body.length()));
            JsonObject json = gson.fromJson(body, JsonObject.class);
            String power = json.get("PowerState") != null ? json.get("PowerState").getAsString() : "Unknown";
            return "电源: " + power;
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    private String getPowerStatusSsh() {
        SshResult result = runSshCommand("racadm serveraction powerstatus");
        if (result.success || !result.output.isEmpty()) {
            String out = result.output;
            if (out.toLowerCase().contains("on")) return "电源: ON";
            if (out.toLowerCase().contains("off")) return "电源: OFF";
            return "电源状态: " + out;
        }
        return "查询失败: " + result.error;
    }

    public String powerControl(String action) {
        if ("redfish".equals(Prefs.getConnectionMode(context))) {
            return powerControlRedfish(action);
        } else {
            return powerControlSsh(action);
        }
    }

    private String powerControlRedfish(String action) {
        try {
            String resetType;
            switch (action) {
                case "on": resetType = "On"; break;
                case "off": resetType = "ForceOff"; break;
                default: resetType = "ForceRestart";
            }
            String url = getBaseUrl() + "/redfish/v1/Systems/System.Embedded.1/Actions/ComputerSystem.Reset";
            JsonObject json = new JsonObject();
            json.addProperty("ResetType", resetType);
            RequestBody body = RequestBody.create(gson.toJson(json), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", getAuth())
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            OkHttpClient client = getClient();
            Response response = client.newCall(request).execute();
            String respBody = response.body() != null ? response.body().string() : "";
            return response.isSuccessful() ? "操作成功: " + action : "操作失败 (" + response.code() + "): " + respBody.substring(0, Math.min(200, respBody.length()));
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    private String powerControlSsh(String action) {
        String racadmAction;
        switch (action) {
            case "on": racadmAction = "powerup"; break;
            case "off": racadmAction = "powerdown"; break;
            default: racadmAction = "powercycle";
        }
        SshResult result = runSshCommand("racadm serveraction " + racadmAction);
        if (result.success || result.output.toLowerCase().contains("success")) {
            return "操作成功: " + action;
        }
        String msg = result.output.isEmpty() ? result.error : result.output;
        return "操作结果: " + msg;
    }
}
