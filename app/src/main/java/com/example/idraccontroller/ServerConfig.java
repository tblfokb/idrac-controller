package com.example.idraccontroller;

public class ServerConfig {
    public String id;
    public String name;
    public String ip;
    public String username;
    public String password;
    public int sshPort;
    // 纯SSH模式不再使用，保留兼容旧数据
    @Deprecated
    public int httpsPort;

    public ServerConfig() {
        this.id = "";
        this.name = "";
        this.ip = "";
        this.username = "root";
        this.password = "";
        this.sshPort = 22;
        this.httpsPort = 443;
    }

    public ServerConfig(String id, String name, String ip, String user, String pass,
                        int sshPort) {
        this.id = id;
        this.name = name;
        this.ip = ip;
        this.username = user;
        this.password = pass;
        this.sshPort = sshPort;
        this.httpsPort = 443;
    }
}
