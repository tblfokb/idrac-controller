package com.example.idraccontroller;

public class ServerConfig {
    public String id;
    public String name;
    public String ip;
    public String username;
    public String password;
    public String mode;      // "ssh" or "redfish"
    public int sshPort;
    public int httpsPort;

    public ServerConfig() {
        this.id = "";
        this.name = "";
        this.ip = "";
        this.username = "root";
        this.password = "";
        this.mode = "ssh";
        this.sshPort = 22;
        this.httpsPort = 443;
    }

    public ServerConfig(String id, String name, String ip, String user, String pass,
                        String mode, int sshPort, int httpsPort) {
        this.id = id;
        this.name = name;
        this.ip = ip;
        this.username = user;
        this.password = pass;
        this.mode = mode;
        this.sshPort = sshPort;
        this.httpsPort = httpsPort;
    }
}
