import javax.swing.*;
import java.awt.*;
import java.io.*;

public class MiniHttpServerApp {

    private static Process serverProcess;

    public static void main(String[] args) {
        // Creer la fenetre principale
        JFrame frame = new JFrame("Mini HTTP Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 200);

        // Ajouter un bouton pour demarrer/arreter le serveur
        JButton startButton = new JButton("Demarrer le Serveur");
        JButton stopButton = new JButton("Arreter le Serveur");
        stopButton.setEnabled(false); // Desactive au demarrage

        // Ajouter une zone pour afficher les journaux
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // Action pour demarrer le serveur
        startButton.addActionListener(e -> {
            try {
                logArea.append("Demarrage du serveur...\n");
                serverProcess = startServer();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                logArea.append("Serveur demarre avec succes !\n");
            } catch (Exception ex) {
                logArea.append("Erreur lors du demarrage du serveur : " + ex.getMessage() + "\n");
            }
        });

        // Action pour arreter le serveur
        stopButton.addActionListener(e -> {
            if (serverProcess != null && serverProcess.isAlive()) {
                try {
                    serverProcess.destroy();
                    serverProcess.waitFor(); // Attendre que le processus soit bien arrete
                    logArea.append("Serveur arrete avec succes.\n");
                } catch (InterruptedException ex) {
                    logArea.append("Erreur lors de l'arret du serveur : " + ex.getMessage() + "\n");
                }
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            } else {
                logArea.append("Aucun serveur en cours d'execution ou deja arrete.\n");
            }
        });
        
        

        // Ajouter les composants a la fenetre
        frame.setLayout(new BorderLayout());
        frame.add(startButton, BorderLayout.NORTH);
        frame.add(stopButton, BorderLayout.SOUTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Afficher la fenetre
        frame.setVisible(true);
    }

    private static Process startServer() throws IOException {
        // Chemin du fichier compile ou JAR
        String javaPath = System.getProperty("java.home") + "/bin/java";
        String serverClass = "HttpServer"; // Nom de votre classe
        String command = javaPath + " -cp . " + serverClass; // Adapter si necessaire
        return Runtime.getRuntime().exec(command);
    }
}
