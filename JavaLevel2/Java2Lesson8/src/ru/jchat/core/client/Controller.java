package ru.jchat.core.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import ru.jchat.core.server.AuthService;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

//CREATE TABLE users (
//    id       INTEGER PRIMARY KEY AUTOINCREMENT,
//            login    TEXT    UNIQUE,
//            password TEXT,
//            nick     TEXT    UNIQUE
//            );


public class Controller implements Initializable {
    @FXML
    TextArea textArea;
    @FXML
    TextField msgField;
    @FXML
    HBox authPanel;
    @FXML
    VBox regPanel;
    @FXML
    HBox msgPanel;
    @FXML
    TextField loginField;
    @FXML
    PasswordField passField;
    @FXML
    TextField regLoginField;
    @FXML
    PasswordField regPassField;
    @FXML
    TextField regNickField;
    @FXML
    ListView<String> clientsListView;


    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    final String SERVER_IP = "localhost";
    final int SERVER_PORT = 8189;

    private boolean authorized;
    private String myNick;

    private ObservableList<String> clientsList;

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
        if (authorized){
            msgPanel.setVisible(true);
            msgPanel.setManaged(true);
            authPanel.setVisible(false);
            authPanel.setManaged(false);
            regPanel.setVisible(false);
            regPanel.setManaged(false);
            clientsListView.setVisible(true);
            clientsListView.setManaged(true);

        } else {
            msgPanel.setVisible(false);
            msgPanel.setManaged(false);
            authPanel.setVisible(true);
            authPanel.setManaged(true);
            regPanel.setVisible(false);
            regPanel.setManaged(false);
            clientsListView.setVisible(false);
            clientsListView.setManaged(false);
            myNick = "";
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthorized(false);
    }

    public void connect(){
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            clientsList = FXCollections.observableArrayList();
            clientsListView.setItems(clientsList);

            clientsListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
                @Override
                public ListCell<String> call(ListView<String> param) {
                    return new ListCell<String>(){
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            if (!empty) {
                                setText(item);
                                if (item.equals(myNick)){
                                    setStyle("-fx-font-weight: bold; -fx-background-color: red;");
                                }
                            } else {
                                setGraphic(null);
                            }
                        }
                    };
                }
            });

            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        String s = in.readUTF();
                        if (s.startsWith("/authok ")){
                            setAuthorized(true);
                            myNick = s.split("\\s")[1];
                            break;
                        }else if(s.startsWith("/out")) showAlert(s.substring(5));

                        textArea.appendText(s + "\n");
                    }
                    while (true) {
                        String s = in.readUTF();
                        if (s.startsWith("/")){
                            if (s.startsWith("/clientslist ")){
                                String[] data = s.split("\\s");
                                Platform.runLater(() -> {
                                    clientsList.clear();
                                    for (int i = 1; i < data.length; i++) {
                                        clientsList.addAll(data[i]);
                                    }
                                });
                            }
                        }
                        textArea.appendText(s + "\n");
                    }
                } catch (IOException e) {
                    showAlert("Сервер перестал отвечать");
                } finally {
                    setAuthorized(false);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            showAlert("Не удалось подключиться к серверу. Проверьте сетевое соединение");
        }
    }

    public void sendAuthMsg(){
        if (loginField.getText().isEmpty() || passField.getText().isEmpty()){
            showAlert("Не заполнено поле логин/пароль");
            return;
        }
        if (socket == null || socket.isClosed()){
            connect();
        }
        // /auth login pass
        try{
            out.writeUTF("/auth " + loginField.getText() + " " + passField.getText());
            loginField.clear();
            passField.clear();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void sendMsg() {
        try {
            out.writeUTF(msgField.getText());
            msgField.clear();
            msgField.requestFocus();
        } catch (IOException e) {
            showAlert("Не удалось подключиться к серверу. Проверьте сетевое соединение");
        }
    }

    public void showAlert(String msg){
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Возникли проблемы");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    public void clientsListClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2){
            msgField.setText("/w " + clientsListView.getSelectionModel().getSelectedItem() + " ");
            msgField.requestFocus();
            msgField.selectEnd();
        }
    }
// Переключение между регистрацией и авторизацией
    public void actionRegVisible(ActionEvent actionEvent) {
        authPanel.setVisible(false);
        authPanel.setManaged(false);
        regPanel.setVisible(true);
        regPanel.setManaged(true);
    }
    public void actionAuthVisible(ActionEvent actionEvent) {
        authPanel.setVisible(true);
        authPanel.setManaged(true);
        regPanel.setVisible(false);
        regPanel.setManaged(false);
    }

    public void sendRegMsg(ActionEvent actionEvent) {
        if (regLoginField.getText().isEmpty() || regPassField.getText().isEmpty() || regNickField.getText().isEmpty()){
            showAlert("Не заполнено поле логин/пароль/ник");
            return;
        }
        if (socket == null || socket.isClosed()){
            connect();
        }
        // Передаем команду на регистрацию пользователя
        try{
            out.writeUTF("/reg " + regLoginField.getText() + " " + regPassField.getText()+" "+regNickField.getText());
            regLoginField.clear();
            regPassField.clear();
            regNickField.clear();

        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
