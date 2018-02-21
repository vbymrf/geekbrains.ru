package ru.jchat.core.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String nick;
    private long TimeCloseClient = 125_000;                       // Время на закрытия сокета и клиент хэндлера в миллисекундах
    private boolean Avtorisirovan =false;                           // Клиент вошел в чат?


    public String getNick() {
        return nick;
    }

    public void setTimeCloseClient(long TimeMillisClose) {
        TimeCloseClient = TimeMillisClose;
    }

    public void closeSocket() {
        if(!socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Client closed " + socket);
        }
    }

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            Thread reader;
            reader = new Thread(() -> {
                try {
                    // отслеживаем работу потока
                    while (!Thread.interrupted()) {

                        String msg = in.readUTF();

                        if (msg.startsWith("/auth") || msg.startsWith("/reg")) {
                            String[] data = msg.split("\\s");

                            if (msg.startsWith("/auth")) {
                                if (data.length == 3) {
                                    String newNick = server.getAuthService().getNickByLoginAndPass(data[1], data[2]);
                                    if (newNick != null) {
                                        if (!server.isNickBusy(newNick)) {
                                            nick = newNick;
                                            sendMsg("/authok " + newNick);
                                            server.subscribe(this);
                                            Avtorisirovan =true;
                                            break;
                                        } else {
                                            sendMsg("Учетная запись уже занята");
                                        }
                                    } else {
                                        sendMsg("Неверный логин/пароль");
                                    }
                                }
                            }
                            if (msg.startsWith("/reg")) {
                                if (data.length == 4) {
                                    try {
                                        if (!server.getAuthService().absentLoginReg(data[1]) && !server.getAuthService().absentNickReg(data[3])) {
                                            if (server.getAuthService().userRegistration(data[1], data[2], data[3])) {
                                                sendMsg("/out Пользователь " + data[1] + " зарегистрирован успешно");
                                            }
                                        } else if (server.getAuthService().absentLoginReg(data[1]))
                                            sendMsg("/out логин занят");
                                        else sendMsg("/out Ник занят");
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                        sendMsg("/out Регистрация недоступна. Обратитесь в службу поддержки");
                                    }
                                }

                            }
                        }
                    }

                    while (!Thread.interrupted()) {
                        String msg = in.readUTF();
                        //System.out.println("Поток жив? "+timeOut.isAlive());
                        System.out.println(nick + ": " + msg);
                        if (msg.startsWith("/")) {
                            if (msg.equals("/end")) break;
                            // /w nick1 shfsdjfsfjk fdf ddfdfdf
                            if (msg.startsWith("/w ")) {
                                String[] data = msg.split("\\s", 3);
                                server.sendPrivateMsg(this, data[1], data[2]);
                            }
                        } else {
                            server.broadcastMsg(nick + ": " + msg);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                } finally {
                    nick = null;
                   if(!socket.isClosed()) server.unsubscribe(this);
                   closeSocket();
                }

            });
            reader.start();
            reader.join(TimeCloseClient);   // Отключаем сокет если клиент не авторизовался за TimeCloseClient миллисекунд
            if (!Avtorisirovan && !socket.isClosed()) {
                reader.interrupt();
                closeSocket();
                in.close();
                out.close();


            }
            reader.join(3000);
                System.out.println("Жив поток reader "+reader.isAlive()+" Авторизация "+Avtorisirovan+" Сокет закрыт "+socket.isClosed());


        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

        public void sendMsg (String msg){
            try {
                out.writeUTF(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
