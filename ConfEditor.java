import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfEditor extends JFrame {

    // Stocke les champs de texte liés aux clés de configuration
    private Map<String, JTextField> fields = new LinkedHashMap<>();
    private File configFile; // Référence au fichier .conf

    public ConfEditor(File configFile) {
        this.configFile = configFile; // Initialise le fichier de configuration
        setTitle("Éditeur de Configuration"); // Titre de la fenêtre
        setSize(400, 300); // Dimensions de la fenêtre
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Ferme l'application à la sortie
        setLayout(new BorderLayout()); // Utilise un layout pour organiser les composants

        // Panel pour contenir les champs de configuration (clé = valeur)
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2)); // Disposition en grille, 2 colonnes
        add(new JScrollPane(panel), BorderLayout.CENTER); // Ajoute le panel avec barre de défilement

        // Charger le contenu du fichier .conf
        try {
            BufferedReader reader = new BufferedReader(new FileReader(configFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Vérifie si la ligne est valide (format clé=valeur)
                if (!line.trim().isEmpty() && line.contains("=")) {
                    String[] parts = line.split("=", 2); // Sépare la ligne en deux parties : clé et valeur
                    String key = parts[0].trim(); // Récupère la clé (avant le '=')
                    String value = parts[1].trim(); // Récupère la valeur (après le '=')

                    // Création d'un label et d'un champ de texte pour chaque paire clé=valeur
                    JLabel label = new JLabel(key); // Affiche la clé comme étiquette
                    JTextField textField = new JTextField(value); // Champ de texte modifiable pour la valeur
                    fields.put(key, textField); // Associe la clé à son champ
                    panel.add(label); // Ajoute l'étiquette au panel
                    panel.add(textField); // Ajoute le champ de texte au panel
                }
            }
            reader.close(); // Ferme le fichier après lecture
        } catch (IOException e) {
            // Affiche un message d'erreur en cas de problème de lecture
            JOptionPane.showMessageDialog(this, "Erreur lors de la lecture du fichier : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }

        // Bouton pour sauvegarder les modifications
        JButton saveButton = new JButton("Sauvegarder"); // Bouton avec le texte "Sauvegarder"
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveConfig(); // Appelle la méthode de sauvegarde quand le bouton est cliqué
            }
        });
        add(saveButton, BorderLayout.SOUTH); // Ajoute le bouton en bas de la fenêtre
    }

    // Méthode pour sauvegarder les modifications dans le fichier .conf
    private void saveConfig() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(configFile));
            for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
                // Écrit chaque clé=valeur dans le fichier
                writer.write(entry.getKey() + "=" + entry.getValue().getText());
                writer.newLine(); // Passe à la ligne suivante
            }
            writer.close(); // Ferme le fichier après écriture
            // Affiche un message de succès
            JOptionPane.showMessageDialog(this, "Configuration sauvegardée avec succès.", "Succès", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            // Affiche un message d'erreur en cas de problème d'écriture
            JOptionPane.showMessageDialog(this, "Erreur lors de la sauvegarde : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        // Spécifiez ici le chemin du fichier .conf à éditer
        File configFile = new File("C:\\Users\\asus\\Documents\\ProjetHTTP\\server.conf");
        
        // Vérifie si le fichier .conf existe
        if (!configFile.exists()) {
            JOptionPane.showMessageDialog(null, "Fichier de configuration introuvable : " + configFile.getPath(), "Erreur", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Quitte l'application si le fichier est introuvable
        }
        
        // Lance l'interface graphique dans le thread principal
        SwingUtilities.invokeLater(() -> new ConfEditor(configFile).setVisible(true));
    }
}
