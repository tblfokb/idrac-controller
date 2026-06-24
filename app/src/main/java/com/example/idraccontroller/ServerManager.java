package com.example.idraccontroller;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ServerManager {

    private static final String PREF_SERVERS = "idrac_servers";
    private static final String KEY_LIST = "server_list";
    private static final String KEY_ACTIVE = "active_server_id";
    private static Gson gson = new Gson();

    public static List<ServerConfig> getServers(Context ctx) {
        String json = ctx.getSharedPreferences(PREF_SERVERS, Context.MODE_PRIVATE).getString(KEY_LIST, "[]");
        Type listType = new TypeToken<ArrayList<ServerConfig>>(){}.getType();
        List<ServerConfig> list = gson.fromJson(json, listType);
        return list != null ? list : new ArrayList<ServerConfig>();
    }

    public static void saveServers(Context ctx, List<ServerConfig> list) {
        String json = gson.toJson(list);
        ctx.getSharedPreferences(PREF_SERVERS, Context.MODE_PRIVATE).edit().putString(KEY_LIST, json).apply();
    }

    public static ServerConfig addServer(Context ctx, ServerConfig config) {
        if (config.id == null || config.id.isEmpty()) {
            config.id = UUID.randomUUID().toString();
        }
        List<ServerConfig> list = getServers(ctx);
        list.add(config);
        saveServers(ctx, list);
        return config;
    }

    public static void updateServer(Context ctx, ServerConfig config) {
        List<ServerConfig> list = getServers(ctx);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(config.id)) {
                list.set(i, config);
                saveServers(ctx, list);
                return;
            }
        }
    }

    public static void deleteServer(Context ctx, String id) {
        List<ServerConfig> list = getServers(ctx);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                saveServers(ctx, list);
                String activeId = getActiveServerId(ctx);
                if (activeId != null && activeId.equals(id)) {
                    setActiveServerId(ctx, list.isEmpty() ? "" : list.get(0).id);
                }
                return;
            }
        }
    }

    public static String getActiveServerId(Context ctx) {
        return ctx.getSharedPreferences(PREF_SERVERS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, "");
    }

    public static void setActiveServerId(Context ctx, String id) {
        ctx.getSharedPreferences(PREF_SERVERS, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE, id).apply();
    }

    public static ServerConfig getActiveServer(Context ctx) {
        String activeId = getActiveServerId(ctx);
        if (activeId.isEmpty()) return null;
        List<ServerConfig> list = getServers(ctx);
        for (ServerConfig s : list) {
            if (s.id.equals(activeId)) return s;
        }
        return null;
    }

    public static void applyActiveToPrefs(Context ctx) {
        ServerConfig s = getActiveServer(ctx);
        if (s != null) {
            Prefs.setIpAddress(ctx, s.ip);
            Prefs.setUsername(ctx, s.username);
            Prefs.setPassword(ctx, s.password);
            Prefs.setSshPort(ctx, s.sshPort);
        }
    }
}
