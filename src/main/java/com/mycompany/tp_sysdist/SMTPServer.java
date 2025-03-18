/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.tp_sysdist;

/**
 *
 * @author USER
 */
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SMTPServer {
    private static final int PORT = 2525;
    private static final String MAIL_DIR = "mailserver/";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur SMTP démarré sur le port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private enum State {
            INIT, HELO_RECEIVED, WAIT_MAIL, WAIT_RCPT, WAIT_DATA, WAIT_MESSAGE, END
        }

        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String sender;
        private List<String> recipients;
        private StringBuilder emailData;
        private State state;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.recipients = new ArrayList<>();
            this.emailData = new StringBuilder();
            this.state = State.INIT; 
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("220 Simple SMTP Server Ready");

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Client: " + line);
                    processCommand(line.trim());
                    if (state == State.END) break;
                }
            } catch (IOException e) {
                System.err.println("Connexion interrompue !");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void processCommand(String line) {
            if (line.startsWith("HELO")) {
                if (state == State.INIT) {
                    out.println("250 Hello " + line.substring(5));
                    state = State.HELO_RECEIVED;
                } else {
                    out.println("503 Bad sequence of commands");
                }
            } else if (line.startsWith("MAIL FROM:")) {
                if (state == State.HELO_RECEIVED) {
                    sender = line.substring(10).trim();
                    recipients.clear();
                    out.println("250 OK");
                    state = State.WAIT_RCPT;
                } else {
                    out.println("503 Bad sequence of commands");
                }
            } else if (line.startsWith("RCPT TO:")) {
                if (state == State.WAIT_RCPT) {
                    String recipient = line.substring(8).trim();
                    if (checkRecipientExists(recipient)) {
                        recipients.add(recipient);
                        out.println("250 OK");
                    } else {
                        out.println("550 No such user");
                    }
                } else {
                    out.println("503 Bad sequence of commands");
                }
            } else if (line.equals("DATA")) {
                if (state == State.WAIT_RCPT && !recipients.isEmpty()) {
                    out.println("354 End data with <CR><LF>.<CR><LF>");
                    emailData.setLength(0);
                    state = State.WAIT_MESSAGE;
                } else {
                    out.println("503 Bad sequence of commands");
                }
            } else if (state == State.WAIT_MESSAGE) {
                if (line.equals(".")) {
                    saveEmail();
                    out.println("250 Message accepted for delivery");
                    state = State.HELO_RECEIVED;
                } else {
                    emailData.append(line).append("\n");
                }
            } else if (line.equals("QUIT")) {
                out.println("221 Bye");
                state = State.END;
            } else {
                out.println("502 Command not implemented");
            }
        }

        private boolean checkRecipientExists(String recipient) {
            File userDir = new File(MAIL_DIR + recipient);
            return userDir.exists() && userDir.isDirectory();
        }

        private void saveEmail() {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            for (String recipient : recipients) {
                File userDir = new File(MAIL_DIR + recipient);
                if (!userDir.exists()) {
                    userDir.mkdirs();
                }
                File emailFile = new File(userDir, timestamp + ".txt");
                try {
                    Files.write(emailFile.toPath(), emailData.toString().getBytes());
                    System.out.println("Email enregistré pour " + recipient + " : " + emailFile.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
