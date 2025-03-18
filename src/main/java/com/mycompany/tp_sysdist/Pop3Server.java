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
    private static final int PORT = 110;
    private static final String MAIL_DIR = "mailserver";
    private static final Map<String, List<File>> userMails = new HashMap<>();
    private static final Map<String, List<File>> markedForDeletion = new HashMap<>();
    
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server is running on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String currentUser;
        private boolean authenticated = false;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println("+OK POP3 server ready");
                
                String line;
                while ((line = in.readLine()) != null) {
                    String[] parts = line.split(" ", 2);
                    String command = parts[0].toUpperCase();
                    String argument = parts.length > 1 ? parts[1] : "";
                    
                    switch (command) {
                        case "USER":
                            handleUser(argument);
                            break;
                        case "PASS":
                            handlePass(argument);
                            break;
                        case "STAT":
                            handleStat();
                            break;
                        case "LIST":
                            handleList();
                            break;
                        case "RETR":
                            handleRetr(argument);
                            break;
                        case "DELE":
                            handleDele(argument);
                            break;
                        case "QUIT":
                            handleQuit();
                            return;
                        default:
                            out.println("-ERR Unknown command");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleUser(String user) {
            if (new File(MAIL_DIR, user).exists()) {
                currentUser = user;
                out.println("+OK User recognized");
            } else {
                out.println("-ERR User not found");
            }
        }

        private void handlePass(String pass) {
            if (currentUser != null) {
                authenticated = true;
                loadUserEmails();
                out.println("+OK Password accepted");
            } else {
                out.println("-ERR No user specified");
            }
        }

        private void handleStat() {
            if (!authenticated) {
                out.println("-ERR Not authenticated");
                return;
            }
            List<File> mails = userMails.get(currentUser);
            out.println("+OK " + mails.size() + " messages");
        }

        private void handleList() {
            if (!authenticated) {
                out.println("-ERR Not authenticated");
                return;
            }
            List<File> mails = userMails.get(currentUser);
            for (int i = 0; i < mails.size(); i++) {
                out.println("+OK " + (i + 1) + " " + mails.get(i).length());
            }
        }

        private void handleRetr(String msgNum) {
            if (!authenticated) {
                out.println("-ERR Not authenticated");
                return;
            }
            try {
                int index = Integer.parseInt(msgNum) - 1;
                List<File> mails = userMails.get(currentUser);
                if (index >= 0 && index < mails.size()) {
                    File email = mails.get(index);
                    out.println("+OK " + email.length() + " octets");
                    BufferedReader reader = new BufferedReader(new FileReader(email));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.println(line);
                    }
                    reader.close();
                } else {
                    out.println("-ERR No such message");
                }
            } catch (Exception e) {
                out.println("-ERR Invalid command");
            }
        }

        private void handleDele(String msgNum) {
            if (!authenticated) {
                out.println("-ERR Not authenticated");
                return;
            }
            try {
                int index = Integer.parseInt(msgNum) - 1;
                List<File> mails = userMails.get(currentUser);
                if (index >= 0 && index < mails.size()) {
                    File email = mails.get(index);
                    markedForDeletion.get(currentUser).add(email);
                    out.println("+OK Message marked for deletion");
                } else {
                    out.println("-ERR No such message");
                }
            } catch (Exception e) {
                out.println("-ERR Invalid command");
            }
        }

        private void handleQuit() {
            if (authenticated && markedForDeletion.containsKey(currentUser)) {
                for (File email : markedForDeletion.get(currentUser)) {
                    email.delete();
                }
                markedForDeletion.remove(currentUser);
            }
            out.println("+OK Goodbye");
        }

        private void loadUserEmails() {
            File userDir = new File(MAIL_DIR, currentUser);
            File[] emails = userDir.listFiles();
            userMails.put(currentUser, emails != null ? Arrays.asList(emails) : new ArrayList<>());
            markedForDeletion.put(currentUser, new ArrayList<>());
        }
    }
}
