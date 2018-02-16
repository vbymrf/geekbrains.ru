package ru.jchat.core.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String nick;

    public String getNick() {
        return nick;
    }

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            new Thread(() -> {
                    try {
                        while (true) {
                            String msg = in.readUTF();
                            if (msg.startsWith("/auth ")){
                                String[] data = msg.split("\\s");
                                String newNick = server.getAuthService().getNickByLoginAndPass(data[1], data[2]);
                                if (newNick != null){
                                    nick = newNick;
                                    sendMsg("/authok");
                                    server.subscribe(this);
                                    break;
                                } else {
                                    sendMsg("Неверный логин/пароль");
                                }
                            }
                        }
                        while (true) {
                            String msg = in.readUTF();
// Добавление команды /w  ДЗ2
                            if (msg.startsWith("/w")){
                                String[] data = msg.split("\\s");
                                String Mess="";
                                        for (String Mes : data) {
                                            if (Mes == data[0]) ;
                                            else if (Mes == data[1]) Mess = "<" + Mes + "> ";
                                            else Mess += " " + Mes;
                                        }
                                System.out.println(nick + ": " + Mess);
                                sendMsg(nick+": "+Mess);
                                server.forNick(data[1],Mess);
// И соответвенно метод в Server (forNick())
                            }else {
                                System.out.println(nick + ": " + msg);
                                if (msg.equals("/end")) break;
                                server.broadcastMsg(nick + ": " + msg);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        server.unsubscribe(this);
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void sendMsg(String msg){
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
