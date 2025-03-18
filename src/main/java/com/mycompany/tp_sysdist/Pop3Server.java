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
import java.nio.file.*;
import java.util.*;

public class Pop3Server {
    private static final int PORT = 110;
    private static final String MAIL_DIR = "mailserver";
    private static final Map<String, List<File>> userMails = new HashMap<>();
    private static final Map<String, Set<Integer>> deleteMarkers = new HashMap<>();

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
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String currentUser;
        private boolean authenticated = false;

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
                        case "RSET":
                            handleRset();
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

        /** ✅ Handle User Login and Load Emails **/
        private void handleUser(String user) {
            File userDir = new File(MAIL_DIR, user);
            if (userDir.exists() && userDir.isDirectory()) {
                currentUser = user;
                loadUserMails(user);
                out.println("+OK User accepted");
            } else {
                out.println("-ERR User not found");
            }
        }

        /** ✅ Load Emails from Disk **/
        private void loadUserMails(String user) {
            File userDir = new File(MAIL_DIR, user);
            List<File> emails = new ArrayList<>();

            File[] emailFiles = userDir.listFiles();
            if (emailFiles != null) {
                Arrays.sort(emailFiles, Comparator.comparing(File::getName)); // Keep order
                emails.addAll(Arrays.asList(emailFiles));
            }
            userMails.put(user, emails);
            deleteMarkers.put(user, new HashSet<>());
        }

        /** ✅ Authenticate User **/
        private void handlePass(String pass) {
            authenticated = true;
            out.println("+OK Password accepted");
        }

        /** ✅ STAT Command - Show Number of Emails **/
        private void handleStat() {
            List<File> mails = userMails.get(currentUser);
            long totalSize = mails.stream().mapToLong(File::length).sum();
            out.println("+OK " + mails.size() + " " + totalSize);
        }

        /** ✅ LIST Command - Show Emails **/
        private void handleList() {
            List<File> mails = userMails.get(currentUser);
            if (mails.isEmpty()) {
                out.println("-ERR No messages");
                return;
            }
            out.println("+OK " + mails.size() + " messages");
            for (int i = 0; i < mails.size(); i++) {
                if (!deleteMarkers.get(currentUser).contains(i)) {
                    out.println((i + 1) + " " + mails.get(i).length() + " bytes");
                }
            }
            out.println(".");
        }

        /** ✅ RETR Command - Retrieve Email **/
        private void handleRetr(String arg) {
            try {
                int index = Integer.parseInt(arg) - 1;
                List<File> mails = userMails.get(currentUser);

                if (index >= 0 && index < mails.size()) {
                    File emailFile = mails.get(index);
                    String content = new String(Files.readAllBytes(emailFile.toPath()));

                    out.println("+OK " + emailFile.length() + " bytes");
                    out.println(content);
                    out.println(".");
                } else {
                    out.println("-ERR No such message");
                }
            } catch (Exception e) {
                out.println("-ERR Invalid message number");
            }
        }

        /** ✅ DELE Command - Mark for Deletion **/
        private void handleDele(String arg) {
            try {
                int index = Integer.parseInt(arg) - 1;
                if (deleteMarkers.get(currentUser).contains(index)) {
                    out.println("-ERR Message already marked for deletion");
                } else {
                    deleteMarkers.get(currentUser).add(index);
                    out.println("+OK Message marked for deletion");
                }
            } catch (Exception e) {
                out.println("-ERR Invalid message number");
            }
        }

        /** ✅ RSET Command - Undo Deletions **/
        private void handleRset() {
            deleteMarkers.get(currentUser).clear();
            out.println("+OK All deletion marks cleared");
        }

        /** ✅ QUIT Command - Delete Marked Emails **/
        private void handleQuit() {
            List<File> mails = userMails.get(currentUser);
            Set<Integer> markers = deleteMarkers.get(currentUser);
            File userDir = new File(MAIL_DIR, currentUser);

            for (int index : markers) {
                if (index < mails.size()) {
                    File emailFile = mails.get(index);
                    if (emailFile.exists()) {
                        emailFile.delete();
                    }
                }
            }

            out.println("+OK POP3 server signing off");
        }
    }
}
