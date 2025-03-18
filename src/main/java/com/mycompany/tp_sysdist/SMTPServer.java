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
import java.text.SimpleDateFormat;
import java.util.Date;

public class SMTPServer {
    private static final int PORT = 2525;
    private static final String MAIL_DIR = "mailserver/";

    public static void main(String[] args) {
        File mailDir = new File(MAIL_DIR);
        if (!mailDir.exists()) {
            mailDir.mkdirs(); // Créer le dossier principal s'il n'existe pas
        }

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
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String sender;
        private String recipient;
        private StringBuilder emailData;
        private boolean emailInProgress = false;
        private boolean senderExists = false;
        private boolean recipientExists = false;
        private boolean heloReceived = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.emailData = new StringBuilder();
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("220 SMTP Server Ready");

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("Client: " + line);

                    if (line.startsWith("HELO ")) {
                        String domain = line.substring(5).trim();
                        if (!domain.isEmpty()) {
                            heloReceived = true;
                            out.println("250 Hello " + domain);
                        } else {
                            out.println("501 Syntax error in parameters");
                        }
                    } else if (!heloReceived) {
                        out.println("503 HELO first");
                    } else if (line.startsWith("MAIL FROM:")) {
                        sender = extractAddress(line);
                        senderExists = checkUserExists(sender);
                        if (senderExists) {
                            out.println("250 OK");
                        } else {
                            out.println("550 Sender not found");
                        }
                    } else if (line.startsWith("RCPT TO:")) {
                        recipient = extractAddress(line);
                        recipientExists = checkUserExists(recipient);
                        if (recipientExists) {
                            out.println("250 OK");
                        } else {
                            out.println("550 Recipient not found");
                        }
                    } else if (line.equals("DATA")) {
                        if (senderExists && recipientExists) {
                            out.println("354 End data with <CRLF>.<CRLF>");
                            emailInProgress = true;
                            emailData.setLength(0);
                        } else {
                            out.println("503 Need valid sender and recipient first");
                        }
                    } else if (emailInProgress && line.equals(".")) {
                        saveEmail();
                        out.println("250 Message accepted for delivery");
                        emailInProgress = false;
                    } else if (line.equals("QUIT")) {
                        out.println("221 Bye");
                        break;
                    } else {
                        if (emailInProgress) {
                            emailData.append(line).append("\n");
                        } else {
                            out.println("502 Command not implemented");
                        }
                    }
                }
                socket.close();
            } catch (IOException e) {
                System.err.println("Erreur de connexion !");
            }
        }

        private String extractAddress(String line) {
            return line.substring(line.indexOf(":") + 1).trim();
        }

        private boolean checkUserExists(String user) {
            File userDir = new File(MAIL_DIR + user);
            return userDir.exists() && userDir.isDirectory();
        }

        private void saveEmail() {
            if (recipient == null || recipient.isEmpty()) {
                return; // Pas de destinataire, pas d'enregistrement
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File userDir = new File(MAIL_DIR + recipient);
            if (!userDir.exists()) {
                userDir.mkdirs();
            }

            File emailFile = new File(userDir, timestamp + ".txt");

            try (FileWriter writer = new FileWriter(emailFile)) {
                writer.write(emailData.toString());
                System.out.println("Email stocké: " + emailFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
