import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServerConfig {
    // Objet Properties pour stocker les paires clé=valeur du fichier de configuration
    private Properties properties;

    // Constructeur qui charge les propriétés à partir d'un fichier
    public ServerConfig(String configFilePath) throws IOException {
        properties = new Properties(); // Initialise l'objet Properties
        try (FileInputStream fis = new FileInputStream(configFilePath)) {
            // Charge les propriétés depuis le fichier spécifié
            properties.load(fis);
        }
    }

    // Méthode pour récupérer une valeur sous forme de chaîne avec une valeur par défaut
    public String get(String key, String defaultValue) {
        // Retourne la valeur associée à la clé, ou la valeur par défaut si la clé est absente
        return properties.getProperty(key, defaultValue);
    }

    // Méthode pour récupérer un chemin d'accès sous forme normalisée
    public String getPath(String key, String defaultValue) {
        // Récupère la valeur associée à la clé
        String value = properties.getProperty(key);
        if (value != null) {
            // Normalise le chemin pour Windows (convertit les "/" en "\\")
            value = value.replace("/", "\\\\");
            // Ajoute un backslash final si nécessaire
            if (!value.endsWith("\\\\")) {
                value += "\\\\";
            }
            File file = new File(value); // Crée un objet File pour vérifier l'existence du chemin
            if (file.exists() && file.isFile()) {
                // Retourne le chemin absolu si le fichier existe
                return file.getAbsolutePath();
            } else {
                // Affiche un message d'erreur si le chemin est invalide
                System.err.println("Erreur : Chemin invalide pour la clé " + key + " : " + value);
            }
        }
        // Retourne la valeur par défaut si la clé est absente ou le chemin est invalide
        return defaultValue;
    }

    // Méthode pour récupérer une valeur entière avec une valeur par défaut
    public int getInt(String key, int defaultValue) {
        try {
            // Tente de convertir la valeur en entier
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            // Retourne la valeur par défaut en cas d'erreur de conversion
            return defaultValue;
        }
    }

    // Méthode pour récupérer une valeur booléenne avec une valeur par défaut
    public boolean getBoolean(String key, boolean defaultValue) {
        // Récupère la valeur associée à la clé
        String value = properties.getProperty(key);
        if (value != null) {
            // Retourne true si la valeur est "true" (insensible à la casse)
            return value.equalsIgnoreCase("true");
        }
        // Retourne la valeur par défaut si la clé est absente
        return defaultValue;
    }
}
