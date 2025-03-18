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
    private static final String MAILDIR = "mailserver";
    private static final Map<String, List<String>> userMails = new HashMap<>();
    private static final Map<String, Boolean[]> deleteFlags = new HashMap<>();
    
    private enum State {AUTHORIZATION, TRANSACTION, UPDATE}
    
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("POP3 Server is running on port " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private State state = State.AUTHORIZATION;
        private String currentUser = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println("+OK POP3 server ready");

                String line;
                while ((line = in.readLine()) != null) {
                    String[] parts = line.split(" ", 2);
                    String command = parts[0].toUpperCase();
                    String argument = (parts.length > 1) ? parts[1] : "";

                    switch (command) {
                        case "USER": handleUSER(argument); break;
                        case "PASS": handlePASS(argument); break;
                        case "STAT": handleSTAT(); break;
                        case "LIST": handleLIST(); break;
                        case "RETR": handleRETR(argument); break;
                        case "DELE": handleDELE(argument); break;
                        case "QUIT": handleQUIT(); return;
                        default: out.println("-ERR Unknown command");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleUSER(String user) {
            if (state != State.AUTHORIZATION) {
                out.println("-ERR Invalid state");
                return;
            }
            if (new File(MAILDIR + "/" + user).exists()) {
                currentUser = user;
                out.println("+OK User accepted");
            } else {
                out.println("-ERR User not found");
            }
        }

        private void handlePASS(String pass) {
            if (state != State.AUTHORIZATION || currentUser == null) {
                out.println("-ERR Invalid state");
                return;
            }
            state = State.TRANSACTION;
            loadMails(currentUser);
            out.println("+OK Password accepted");
        }

        private void handleSTAT() {
            if (state != State.TRANSACTION) {
                out.println("-ERR Invalid state");
                return;
            }
            int count = userMails.get(currentUser).size();
            int size = userMails.get(currentUser).stream().mapToInt(String::length).sum();
            out.println("+OK " + count + " " + size);
        }

        private void handleLIST() {
            if (state != State.TRANSACTION) {
                out.println("-ERR Invalid state");
                return;
            }
            List<String> mails = userMails.get(currentUser);
            Boolean[] flags = deleteFlags.get(currentUser);
            for (int i = 0; i < mails.size(); i++) {
                if (!flags[i]) {
                    out.println("+OK " + (i + 1) + " " + mails.get(i).length());
                }
            }
            out.println(".");
        }

        private void handleRETR(String arg) {
            if (state != State.TRANSACTION) {
                out.println("-ERR Invalid state");
                return;
            }
            int index = Integer.parseInt(arg) - 1;
            List<String> mails = userMails.get(currentUser);
            if (index < 0 || index >= mails.size() || deleteFlags.get(currentUser)[index]) {
                out.println("-ERR No such message");
                return;
            }
            out.println("+OK " + mails.get(index).length() + " octets");
            out.println(mails.get(index));
            out.println(".");
        }

        private void handleDELE(String arg) {
            if (state != State.TRANSACTION) {
                out.println("-ERR Invalid state");
                return;
            }
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= userMails.get(currentUser).size() || deleteFlags.get(currentUser)[index]) {
                out.println("-ERR No such message");
                return;
            }
            deleteFlags.get(currentUser)[index] = true;
            out.println("+OK Message marked for deletion");
        }

        private void handleQUIT() {
            if (state == State.TRANSACTION) {
                state = State.UPDATE;
                List<String> mails = userMails.get(currentUser);
                Boolean[] flags = deleteFlags.get(currentUser);
                for (int i = mails.size() - 1; i >= 0; i--) {
                    if (flags[i]) {
                        mails.remove(i);
                    }
                }
                saveMails(currentUser);
            }
            out.println("+OK POP3 server signing off");
        }

        private void loadMails(String user) {
            File folder = new File(MAILDIR + "/" + user);
            List<String> mails = new ArrayList<>();
            for (File file : Objects.requireNonNull(folder.listFiles())) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    mails.add(content.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            userMails.put(user, mails);
            deleteFlags.put(user, new Boolean[mails.size()]);
            Arrays.fill(deleteFlags.get(user), false);
        }

        private void saveMails(String user) {
            // Implement file-based saving mechanism
        }
    }
}