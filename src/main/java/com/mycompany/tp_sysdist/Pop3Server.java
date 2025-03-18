package com.mycompany.tp_sysdist;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author USER
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Pop3Server {
    private static final int PORT = 1100;
    private static final String MAIL_DIR = "mailserver/";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur POP3 démarré sur le port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private enum State { AUTHORIZATION, TRANSACTION, UPDATE, END }
        private State state;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String user;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.state = State.AUTHORIZATION;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("+OK POP3 server ready");

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Client: " + line);
                    processCommand(line.trim());
                    if (state == State.END) break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }

        private void processCommand(String line) {
            if (line.startsWith("USER ")) {
                if (state == State.AUTHORIZATION) {
                    user = line.substring(5);
                    if (new File(MAIL_DIR + user).exists()) {
                        out.println("+OK user accepted");
                    } else {
                        out.println("-ERR user not found");
                    }
                } else {
                    out.println("-ERR bad sequence of commands");
                }
            } else if (line.startsWith("PASS ")) {
                if (state == State.AUTHORIZATION && user != null) {
                    state = State.TRANSACTION;
                    out.println("+OK password accepted");
                } else {
                    out.println("-ERR bad sequence of commands");
                }
            } else if (line.equals("STAT")) {
                if (state == State.TRANSACTION) {
                    File userDir = new File(MAIL_DIR + user);
                    String[] emails = userDir.list((dir, name) -> name.endsWith(".txt"));
                    out.println("+OK " + (emails != null ? emails.length : 0) + " messages");
                } else {
                    out.println("-ERR bad sequence of commands");
                }
            } else if (line.equals("LIST")) {
                if (state == State.TRANSACTION) {
                    File userDir = new File(MAIL_DIR + user);
                    String[] emails = userDir.list((dir, name) -> name.endsWith(".txt"));
                    if (emails != null && emails.length > 0) {
                        out.println("+OK " + emails.length + " messages");
                        for (int i = 0; i < emails.length; i++) {
                            out.println((i + 1) + " " + new File(userDir, emails[i]).length());
                        }
                    } else {
                        out.println("-ERR no messages");
                    }
                } else {
                    out.println("-ERR bad sequence of commands");
                }
            } else if (line.startsWith("RETR ")) {
                if (state == State.TRANSACTION) {
                    int msgNum = Integer.parseInt(line.substring(5));
                    File userDir = new File(MAIL_DIR + user);
                    String[] emails = userDir.list((dir, name) -> name.endsWith(".txt"));
                    if (emails != null && msgNum > 0 && msgNum <= emails.length) {
                        File emailFile = new File(userDir, emails[msgNum - 1]);
                        try (BufferedReader emailReader = new BufferedReader(new FileReader(emailFile))) {
                            out.println("+OK message follows");
                            String emailLine;
                            while ((emailLine = emailReader.readLine()) != null) {
                                out.println(emailLine);
                            }
                            out.println(".");
                        } catch (IOException e) {
                            out.println("-ERR error reading message");
                        }
                    } else {
                        out.println("-ERR no such message");
                    }
                } else {
                    out.println("-ERR bad sequence of commands");
                }
            } else if (line.equals("QUIT")) {
                out.println("+OK POP3 server signing off");
                state = State.END;
            } else {
                out.println("-ERR command not recognized");
            }
        }
    }
}
