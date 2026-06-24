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

    // ===================== 优雅关机/重启 =====================

    public String gracefulShutdown() {
        if ("redfish".equals(Prefs.getConnectionMode(context))) {
            return gracefulShutdownRedfish();
        } else {
            return gracefulShutdownSsh();
        }
    }

    private String gracefulShutdownRedfish() {
        try {
            String url = getBaseUrl() + "/redfish/v1/Systems/System.Embedded.1/Actions/ComputerSystem.Reset";
            JsonObject json = new JsonObject();
            json.addProperty("ResetType", "GracefulShutdown");
            RequestBody body = RequestBody.create(gson.toJson(json), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                .url(url).header("Authorization", getAuth())
                .header("Content-Type", "application/json").post(body).build();
            OkHttpClient client = getClient();
            Response response = client.newCall(request).execute();
            return response.isSuccessful() ? "优雅关机指令已发送" : "操作失败 (" + response.code() + ")";
        } catch (Exception e) { return "错误: " + e.getMessage(); }
    }

    private String gracefulShutdownSsh() {
        SshResult r = runSshCommand("racadm serveraction gracefulshutdown");
        return (r.success || r.output.toLowerCase().contains("success")) ? "优雅关机指令已发送" : "结果: " + (r.output.isEmpty() ? r.error : r.output);
    }

    public String gracefulRestart() {
        if ("redfish".equals(Prefs.getConnectionMode(context))) {
            return gracefulRestartRedfish();
        } else {
            return gracefulRestartSsh();
        }
    }

    private String gracefulRestartRedfish() {
        try {
            String url = getBaseUrl() + "/redfish/v1/Systems/System.Embedded.1/Actions/ComputerSystem.Reset";
            JsonObject json = new JsonObject();
            json.addProperty("ResetType", "GracefulRestart");
            RequestBody body = RequestBody.create(gson.toJson(json), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                .url(url).header("Authorization", getAuth())
                .header("Content-Type", "application/json").post(body).build();
            OkHttpClient client = getClient();
            Response response = client.newCall(request).execute();
            return response.isSuccessful() ? "优雅重启指令已发送" : "操作失败 (" + response.code() + ")";
        } catch (Exception e) { return "错误: " + e.getMessage(); }
    }

    private String gracefulRestartSsh() {
        SshResult r = runSshCommand("racadm serveraction gracefulrestart");
        return (r.success || r.output.toLowerCase().contains("success")) ? "优雅重启指令已发送" : "结果: " + (r.output.isEmpty() ? r.error : r.output);
    }

    // ===================== 传感器数据 =====================

    public String getSensorData() {
        if ("redfish".equals(Prefs.getConnectionMode(context))) {
            return getSensorDataRedfish();
        } else {
            return getSensorDataSsh();
        }
    }

    private String getSensorDataRedfish() {
        try {
            OkHttpClient client = getClient();
            String baseUrl = getBaseUrl();

            // Get thermal data
            String thermalUrl = baseUrl + "/redfish/v1/Chassis/System.Embedded.1/Thermal";
            Request req = new Request.Builder().url(thermalUrl).header("Authorization", getAuth())
                .header("Accept", "application/json").build();
            Response resp = client.newCall(req).execute();
            String thermalBody = resp.body() != null ? resp.body().string() : "{}";
            JsonObject thermal = gson.fromJson(thermalBody, JsonObject.class);

            // Get power data
            String powerUrl = baseUrl + "/redfish/v1/Chassis/System.Embedded.1/Power";
            Request req2 = new Request.Builder().url(powerUrl).header("Authorization", getAuth())
                .header("Accept", "application/json").build();
            Response resp2 = client.newCall(req2).execute();
            String powerBody = resp2.body() != null ? resp2.body().string() : "{}";
            JsonObject power = gson.fromJson(powerBody, JsonObject.class);

            // Build summary
            JsonObject result = new JsonObject();
            result.add("thermal", thermal);
            result.add("power", power);
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String getSensorDataSsh() {
        // Fallback: basic sensors via SSH
        StringBuilder sb = new StringBuilder();
        SshResult r1 = runSshCommand("racadm getsensorinfo | head -40");
        if (r1.success) sb.append(r1.output);
        SshResult r2 = runSshCommand("racadm getpminfo");
        if (r2.success) sb.append("\n===PM===\n").append(r2.output);
        return sb.toString();
    }

    // ===================== 硬件清单 =====================

    public String getHardwareInfo() {
        if ("redfish".equals(Prefs.getConnectionMode(context))) {
            return getHardwareInfoRedfish();
        } else {
            return getHardwareInfoSsh();
        }
    }

    private String getHardwareInfoRedfish() {
        try {
            OkHttpClient client = getClient();
            String baseUrl = getBaseUrl();

            // System info
            String sysUrl = baseUrl + "/redfish/v1/Systems/System.Embedded.1";
            Request req = new Request.Builder().url(sysUrl).header("Authorization", getAuth())
                .header("Accept", "application/json").build();
            Response resp = client.newCall(req).execute();
            String sysBody = resp.body() != null ? resp.body().string() : "{}";
            JsonObject sys = gson.fromJson(sysBody, JsonObject.class);

            JsonObject result = new JsonObject();
            // CPU
            if (sys.has("ProcessorSummary")) {
                result.add("ProcessorSummary", sys.get("ProcessorSummary"));
            }
            // Memory
            if (sys.has("MemorySummary")) {
                result.add("MemorySummary", sys.get("MemorySummary"));
            }
            // Model
            if (sys.has("Model")) {
                result.addProperty("Model", sys.get("Model").getAsString());
            }
            // Manufacturer
            if (sys.has("Manufacturer")) {
                result.addProperty("Manufacturer", sys.get("Manufacturer").getAsString());
            }
            // BIOS
            if (sys.has("BiosVersion")) {
                result.addProperty("BiosVersion", sys.get("BiosVersion").getAsString());
            }
            return gson.toJson(result);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String getHardwareInfoSsh() {
        StringBuilder sb = new StringBuilder();
        SshResult r1 = runSshCommand("racadm getsysinfo | head -30");
        if (r1.success) sb.append(r1.output);
        return sb.toString();
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
