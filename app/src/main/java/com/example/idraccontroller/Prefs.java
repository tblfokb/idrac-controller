package com.example.idraccontroller;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String PREF_NAME = "idrac_prefs";
    private static final String KEY_IP = "ip_address";
    private static final String KEY_USER = "username";
    private static final String KEY_PASS = "password";
    private static final String KEY_MODE = "connection_mode";
    private static final String KEY_SSH_PORT = "ssh_port";
    private static final String KEY_HTTPS_PORT = "https_port";

    public static String getIpAddress(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_IP, "");
    }
    public static void setIpAddress(Context ctx, String v) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_IP, v).apply();
    }

    public static String getUsername(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USER, "root");
    }
    public static void setUsername(Context ctx, String v) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_USER, v).apply();
    }

    public static String getPassword(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_PASS, "");
    }
    public static void setPassword(Context ctx, String v) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_PASS, v).apply();
    }

    // "ssh" or "redfish"
    public static String getConnectionMode(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_MODE, "ssh");
    }
    public static void setConnectionMode(Context ctx, String v) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_MODE, v).apply();
    }

    public static int getSshPort(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_SSH_PORT, 22);
    }
    public static void setSshPort(Context ctx, int v) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_SSH_PORT, v).apply();
    }

    public static int getHttpsPort(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt(KEY_HTTPS_PORT, 443);
    }
    public static void setHttpsPort(Context ctx, int v) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putInt(KEY_HTTPS_PORT, v).apply();
    }

    public static boolean isConfigured(Context ctx) {
        return !getIpAddress(ctx).isEmpty() && !getPassword(ctx).isEmpty();
    }
}
