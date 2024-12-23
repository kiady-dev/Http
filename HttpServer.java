import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;
import java.util.Date;
import java.util.Map;

public class HttpServer {
    // Port par défaut sur lequel le serveur écoutera
    private static final int DEFAULT_PORT = 1111;
    // Répertoire par défaut pour servir les fichiers
    private static final String DEFAULT_DIRECTORY = "htdocs";

    public static void main(String[] args) {
        // Chargement de la configuration à partir du fichier "server.conf"
        ServerConfig config = loadServerConfig("server.conf");
        
        // Lecture des paramètres de configuration : port, répertoire, interpréteur PHP, activation PHP
        int port = config.getInt("port", DEFAULT_PORT); // Port spécifié dans le fichier ou valeur par défaut
        String directoryPath = config.get("directory", DEFAULT_DIRECTORY); // Répertoire racine pour les fichiers
        String phpInterpreter = config.getPath("php_interpreter", "php-cgi"); // Chemin de l'interpréteur PHP
        boolean isPhpEnabled = config.getBoolean("php_enabled", false); // Activation ou désactivation de PHP

        // Initialisation du répertoire racine du serveur
        File baseDirectory = initializeBaseDirectory(directoryPath);
        if (baseDirectory == null) {
            // Si le répertoire spécifié est invalide, on tente de récupérer le chemin de l'application elle-même
            baseDirectory = getClassLocation();
            if (!baseDirectory.exists() || !baseDirectory.isDirectory()) {
                // Si aucun répertoire valide n'est trouvé, afficher une erreur et quitter
                System.err.println("Impossible de determiner le repertoire de base.");
                return;
            }
        }

        // Démarrage du serveur avec les paramètres configurés
        startServer(port, baseDirectory, phpInterpreter, isPhpEnabled);
    }

    // Méthode pour charger la configuration à partir d'un fichier donné
    private static ServerConfig loadServerConfig(String fileName) {
        try {
            // Retourne un objet `ServerConfig` initialisé avec les propriétés du fichier
            return new ServerConfig(fileName);
        } catch (IOException e) {
            // Affiche un message d'erreur si le fichier ne peut pas être chargé
            System.err.println("Erreur de chargement de la configuration : " + e.getMessage());
            // Quitte le programme en cas d'erreur critique
            System.exit(1);
            return null;
        }
    }

    // Méthode pour initialiser le répertoire racine à partir d'un chemin spécifié
    private static File initializeBaseDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        // Vérifie si le répertoire existe et est valide
        if (!directory.exists() || !directory.isDirectory()) {
            // Affiche un message d'erreur si le chemin est invalide
            System.err.println("Le repertoire specifie est invalide : " + directoryPath);
            return null;
        }
        return directory;
    }

    // Méthode principale pour démarrer le serveur
    private static void startServer(int port, File baseDirectory, String phpInterpreter, boolean isPhpEnabled) {
        if (isPhpEnabled) {
            // Affiche un message si PHP est activé et vérifie la validité de l'interpréteur
            System.out.println("PHP activé. Chemin de l'interpréteur : " + phpInterpreter);
            File phpInterpreterFile = new File(phpInterpreter);
            if (!phpInterpreterFile.exists()) {
                // Si le fichier de l'interpréteur PHP n'existe pas, affiche une erreur et quitte
                System.err.println("Erreur : Le fichier php-cgi n'existe pas au chemin : " + phpInterpreter);
                System.err.println("Chemin absolu : " + phpInterpreterFile.getAbsolutePath());
                System.exit(1);
            }
        } else {
            // Affiche un message si PHP est désactivé
            System.out.println("PHP désactivé. Les fichiers PHP ne seront pas interprétés.");
        }

        // Création d'un pool de threads pour gérer les connexions des clients
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Affiche un message pour indiquer que le serveur est démarré
            System.out.println("Serveur démarré sur le port " + port);

            while (true) {
                try {
                    // Accepte une connexion client
                    Socket clientSocket = serverSocket.accept();
                    // Traite la requête client dans un thread séparé
                    threadPool.execute(() -> handleRequest(clientSocket, baseDirectory, phpInterpreter, isPhpEnabled));
                } catch (IOException e) {
                    // Affiche un message en cas d'erreur lors de l'acceptation d'une connexion
                    System.err.println("Erreur lors de l'acceptation d'une connexion : " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // Affiche un message si le serveur ne peut pas être démarré
            System.err.println("Erreur lors du démarrage du serveur : " + e.getMessage());
        } finally {
            // Arrête le pool de threads lorsque le serveur est arrêté
            threadPool.shutdown();
        }
    }
    


    private static void handleRequest(Socket clientSocket, File baseDirectory, String phpInterpreter, boolean isPhpEnabled) {
        try {
            // Lecture de la requête envoyée par le client
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            OutputStream rawOut = clientSocket.getOutputStream();
    
            // Lecture de la première ligne de la requête (ligne de commande HTTP)
            String requestLine = in.readLine();
            if (requestLine == null) {
                // Si la requête est vide, fermer la connexion
                System.out.println("Requête vide reçue, fermeture de la connexion.");
                clientSocket.close();
                return;
            }
    
            System.out.println("Ligne de requête : " + requestLine);
            // Découpage de la ligne de requête pour extraire la méthode et la ressource demandée
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                // Si le format de la requête est invalide, retourner une erreur 400
                System.out.println("Format de requête invalide.");
                sendErrorResponse(out, 400, "Requête invalide");
                clientSocket.close();
                return;
            }
    
            // Extraction de la méthode HTTP et de la ressource
            String method = requestParts[0];
            String resource = requestParts[1];
    
            // Gestion des requêtes GET et POST
            if (method.equals("GET")) {
                handleGetRequest(resource, baseDirectory, out, rawOut, phpInterpreter, isPhpEnabled);
            } else if (method.equals("POST")) {
                handlePostRequest(resource, baseDirectory, in, out, rawOut, phpInterpreter, isPhpEnabled);
            } else {
                // Si la méthode n'est pas supportée, retourner une erreur 501
                System.out.println("Méthode non supportée : " + method);
                sendErrorResponse(out, 501, "Non implémenté");
                clientSocket.close();
                return;
            }
    
            // Résolution du chemin du fichier demandé
            File requestedFile = new File(baseDirectory, resource.substring(1)).getCanonicalFile();
            System.out.println("Chemin du fichier résolu : " + requestedFile.getPath());
    
            if (requestedFile.exists()) {
                System.out.println("Le fichier existe : " + requestedFile.getPath());
                if (requestedFile.isDirectory()) {
                    // Si la ressource est un répertoire, chercher un fichier index
                    File indexPhpFile = new File(requestedFile, "index.php");
                    if (isPhpEnabled && indexPhpFile.exists() && indexPhpFile.isFile()) {
                        System.out.println("Servir index.php depuis le répertoire : " + requestedFile.getPath());
                        executePhpScript(indexPhpFile, out, rawOut, phpInterpreter);
                    } else {
                        File indexFile = new File(requestedFile, "index.html");
                        if (indexFile.exists() && indexFile.isFile()) {
                            System.out.println("Servir index.html depuis le répertoire : " + requestedFile.getPath());
                            serveFile(indexFile, out, rawOut);
                        } else {
                            // Sinon, lister le contenu du répertoire
                            System.out.println("Servir la liste des fichiers pour : " + requestedFile.getPath());
                            serveDirectoryListing(requestedFile, out);
                        }
                    }
                } else if (requestedFile.getName().endsWith(".php")) {
                    // Si le fichier est un script PHP, l'exécuter
                    if (isPhpEnabled) {
                        System.out.println("Exécution du fichier PHP : " + requestedFile.getPath());
                        executePhpScript(requestedFile, out, rawOut, phpInterpreter);
                    } else {
                        System.out.println("Exécution PHP désactivée. Retour 403 Forbidden.");
                        sendErrorResponse(out, 403, "Interdit");
                    }
                } else {
                    // Servir un fichier statique
                    System.out.println("Servir le fichier statique : " + requestedFile.getPath());
                    serveFile(requestedFile, out, rawOut);
                }
            } else {
                // Si le fichier n'existe pas, retourner une erreur 404
                System.out.println("Fichier non trouvé : " + requestedFile.getPath());
                sendErrorResponse(out, 404, "Non trouvé");
            }
    
            // Fermeture de la connexion avec le client
            clientSocket.close();
    
        } catch (IOException e) {
            // Gestion des erreurs lors du traitement de la requête
            System.err.println("Erreur lors du traitement de la requête : " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    

    private static void handlePostRequest(String resource, File baseDirectory, BufferedReader in, PrintWriter out, OutputStream rawOut, String phpInterpreter, boolean isPhpEnabled) {
        try {
            // Lire les en-têtes pour trouver la longueur du contenu et le type de contenu
            String line;
            int contentLength = 0;
            String contentType = "";
    
            // Lire tous les en-têtes
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } else if (line.toLowerCase().startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                }
            }
    
            // Lire le corps de la requête
            char[] body = new char[contentLength];
            in.read(body, 0, contentLength);
            String postData = new String(body);
    
            // Localiser le fichier demandé
            File requestedFile = new File(baseDirectory, resource.substring(1)).getCanonicalFile();
    
            if (requestedFile.exists() && requestedFile.getName().endsWith(".php")) {
                if (isPhpEnabled) {
                    // Exécuter le script PHP si l'exécution est activée
                    executePhpScript(requestedFile, out, rawOut, phpInterpreter, "POST", postData, contentType);
                } else {
                    // Renvoyer une erreur si PHP est désactivé
                    System.out.println("PHP execution is disabled. Returning 403 Forbidden for POST.");
                    sendErrorResponse(out, 403, "Forbidden");
                }
            } else {
                // Fichier non trouvé ou non valide
                System.out.println("Requested file not found or is not a PHP file: " + requestedFile.getPath());
                sendErrorResponse(out, 404, "Not Found");
            }
        } catch (IOException e) {
            System.err.println("Error handling POST request: " + e.getMessage());
            sendErrorResponse(out, 500, "Internal Server Error");
        }
    }
    
    
    private static void executePhpScript(File phpFile, PrintWriter textOut, OutputStream rawOut, 
                                       String phpInterpreter, String method, String data, String contentType) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(phpInterpreter, phpFile.getAbsolutePath());
        
        // Configuration de l'environnement CGI
        Map<String, String> env = processBuilder.environment();
        env.put("SCRIPT_FILENAME", phpFile.getAbsolutePath());
        env.put("REQUEST_METHOD", method);
        env.put("REDIRECT_STATUS", "200");
        env.put("SCRIPT_NAME", phpFile.getName());
        env.put("SERVER_NAME", "localhost");
        env.put("SERVER_SOFTWARE", "JavaHTTPServer/1.0");
        env.put("SERVER_PROTOCOL", "HTTP/1.1");
        env.put("GATEWAY_INTERFACE", "CGI/1.1");
        env.put("SERVER_PORT", "1111");
        env.put("REQUEST_URI", phpFile.getName());
    
        // Gestion spécifique selon la méthode HTTP
        if (method.equals("POST")) {
            env.put("CONTENT_LENGTH", String.valueOf(data.length()));
            env.put("CONTENT_TYPE", contentType != null && !contentType.isEmpty() ? 
                    contentType : "application/x-www-form-urlencoded");
        } else if (method.equals("GET")) {
            env.put("QUERY_STRING", data);
        }
        
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
    
        // Si c'est une requête POST, écrire les données dans le flux d'entrée du processus
        if (method.equals("POST") && data != null && !data.isEmpty()) {
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
                writer.write(data);
                writer.flush();
            }
        }
    
        try (BufferedReader processInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedWriter processOutput = new BufferedWriter(new OutputStreamWriter(rawOut))) {
            
            boolean headersWritten = false;
            String line;
            boolean hasContentType = false;
            
            while ((line = processInput.readLine()) != null) {
                if (line.isEmpty() && !headersWritten) {
                    headersWritten = true;
                    
                    // Écrire l'en-tête de réponse HTTP si aucun en-tête Content-Type n'a été trouvé
                    if (!hasContentType) {
                        textOut.println("HTTP/1.1 200 OK");
                        textOut.println("Content-Type: text/html; charset=UTF-8");
                    }
                    textOut.println();
                    textOut.flush();
                    continue;
                }
                
                if (!headersWritten) {
                    if (line.startsWith("Content-Type:")) {
                        hasContentType = true;
                        textOut.println("HTTP/1.1 200 OK");
                        textOut.println(line);
                    }
                } else {
                    processOutput.write(line);
                    processOutput.write("\n");
                }
            }
            
            processOutput.flush();
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("PHP script execution failed with exit code: " + exitCode);
                throw new IOException("PHP script execution failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("PHP execution interrupted", e);
        }
    }

    private static void handleGetRequest(String resource, File baseDirectory, PrintWriter out, OutputStream rawOut, String phpInterpreter, boolean isPhpEnabled) {
        try {
            // Séparer l'URL des paramètres GET
            String path = resource;
            String queryString = "";
            int questionMarkIndex = resource.indexOf('?');
            if (questionMarkIndex != -1) {
                path = resource.substring(0, questionMarkIndex);
                queryString = resource.substring(questionMarkIndex + 1);
            }
    
            // Résoudre le chemin du fichier demandé
            File requestedFile = new File(baseDirectory, path.substring(1)).getCanonicalFile();
            System.out.println("Resolved file path: " + requestedFile.getPath());
            System.out.println("Query string: " + queryString);
    
            if (requestedFile.exists()) {
                System.out.println("File exists: " + requestedFile.getPath());
                if (requestedFile.isDirectory()) {
                    File indexPhpFile = new File(requestedFile, "index.php");
                    if (indexPhpFile.exists() && indexPhpFile.isFile()) {
                        if (isPhpEnabled) {
                            System.out.println("Serving index.php from directory: " + requestedFile.getPath());
                            executePhpScript(indexPhpFile, out, rawOut, phpInterpreter, "GET", queryString);
                        } else {
                            System.out.println("PHP execution is disabled for directory: " + requestedFile.getPath());
                            sendErrorResponse(out, 403, "Forbidden");
                        }
                    } else {
                        File indexFile = new File(requestedFile, "index.html");
                        if (indexFile.exists() && indexFile.isFile()) {
                            System.out.println("Serving index.html from directory: " + requestedFile.getPath());
                            serveFile(indexFile, out, rawOut);
                        } else {
                            System.out.println("Serving directory listing for: " + requestedFile.getPath());
                            serveDirectoryListing(requestedFile, out);
                        }
                    }
                } else if (requestedFile.getName().endsWith(".php")) {
                    // Si le fichier est un script PHP
                    if (isPhpEnabled) {
                        System.out.println("Executing PHP file: " + requestedFile.getPath());
                        executePhpScript(requestedFile, out, rawOut, phpInterpreter, "GET", queryString);
                    } else {
                        System.out.println("PHP execution is disabled for file: " + requestedFile.getPath());
                        sendErrorResponse(out, 403, "Forbidden");
                    }
                } else {
                    // Servir un fichier statique
                    System.out.println("Serving static file: " + requestedFile.getPath());
                    serveFile(requestedFile, out, rawOut);
                }
            } else {
                System.out.println("File not found: " + requestedFile.getPath());
                sendErrorResponse(out, 404, "Not Found");
            }
        } catch (IOException e) {
            System.err.println("Error handling GET request: " + e.getMessage());
            sendErrorResponse(out, 500, "Internal Server Error");
        }
    }
    
    

    private static void executePhpScript(File phpFile, PrintWriter textOut, OutputStream rawOut, String phpInterpreter, String method, String queryString) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(phpInterpreter, phpFile.getAbsolutePath());
        
        // Configuration de l'environnement CGI
        Map<String, String> env = processBuilder.environment();
        env.put("SCRIPT_FILENAME", phpFile.getAbsolutePath());
        env.put("REQUEST_METHOD", method);
        env.put("QUERY_STRING", queryString);
        env.put("REDIRECT_STATUS", "200");
        
        // Informations supplémentaires recommandées pour CGI
        env.put("SERVER_SOFTWARE", "JavaHTTPServer/1.0");
        env.put("SERVER_PROTOCOL", "HTTP/1.1");
        env.put("GATEWAY_INTERFACE", "CGI/1.1");
        
        // Ajouter SERVER_NAME et SERVER_PORT
        env.put("SERVER_NAME", "localhost");
        env.put("SERVER_PORT", "1111");
        
        // Pour les requêtes GET, ajouter les variables d'environnement spécifiques
        if (method.equals("GET") && !queryString.isEmpty()) {
            // Construire REQUEST_URI avec query string
            env.put("REQUEST_URI", phpFile.getName() + "?" + queryString);
        } else {
            env.put("REQUEST_URI", phpFile.getName());
        }
        
        processBuilder.redirectErrorStream(true);
        
        try {
            Process process = processBuilder.start();
            
            try (BufferedReader processInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedWriter processOutput = new BufferedWriter(new OutputStreamWriter(rawOut))) {
                
                boolean headersWritten = false;
                String line;
                StringBuilder headers = new StringBuilder();
                
                while ((line = processInput.readLine()) != null) {
                    if (line.isEmpty() && !headersWritten) {
                        headersWritten = true;
                        
                        textOut.println("HTTP/1.1 200 OK");
                        textOut.println("Content-Type: text/html; charset=UTF-8");
                        textOut.println();
                        textOut.flush();
                        
                        continue;
                    }
                    
                    if (!headersWritten) {
                        headers.append(line).append("\r\n");
                        if (line.startsWith("Content-Type:")) {
                            textOut.println(line);
                        }
                    } else {
                        processOutput.write(line);
                        processOutput.write("\n");
                    }
                }
                
                processOutput.flush();
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.err.println("PHP script execution failed with exit code: " + exitCode);
                }
                
            } catch (InterruptedException e) {
                System.err.println("PHP script execution was interrupted");
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            System.err.println("Error executing PHP script: " + e.getMessage());
            sendErrorResponse(textOut, 500, "Internal Server Error");
        }
    }
    
    

    private static void executePhpScript(File phpFile, PrintWriter textOut, OutputStream rawOut, String phpInterpreter) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(phpInterpreter, phpFile.getAbsolutePath());
    
    // Configuration de l'environnement CGI
    Map<String, String> env = processBuilder.environment();
    env.put("SCRIPT_FILENAME", phpFile.getAbsolutePath());
    env.put("REQUEST_METHOD", "GET");
    env.put("QUERY_STRING", "");
    env.put("REDIRECT_STATUS", "200");
    
    // Informations supplémentaires recommandées pour CGI
    env.put("SERVER_SOFTWARE", "JavaHTTPServer/1.0");
    env.put("SERVER_PROTOCOL", "HTTP/1.1");
    env.put("GATEWAY_INTERFACE", "CGI/1.1");
    
    processBuilder.redirectErrorStream(true);
    
    try {
        Process process = processBuilder.start();
        
        try (BufferedReader processInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedWriter processOutput = new BufferedWriter(new OutputStreamWriter(rawOut))) {
            
            boolean headersWritten = false;
            String line;
            StringBuilder headers = new StringBuilder();
            StringBuilder body = new StringBuilder();
            
            while ((line = processInput.readLine()) != null) {
                // Séparer les en-têtes du corps
                if (line.isEmpty() && !headersWritten) {
                    headersWritten = true;
                    
                    // Écrire les en-têtes HTTP
                    textOut.println("HTTP/1.1 200 OK");
                    textOut.println("Content-Type: text/html; charset=UTF-8");
                    textOut.println();
                    textOut.flush();
                    
                    continue;
                }
                
                if (!headersWritten) {
                    // Collecter les en-têtes
                    headers.append(line).append("\r\n");
                } else {
                    // Écrire le corps
                    processOutput.write(line);
                    processOutput.write("\n");
                }
            }
            
            processOutput.flush();
            
            // Gestion des erreurs de processus
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("PHP script execution failed with exit code: " + exitCode);
            }
            
        } catch (InterruptedException e) {
            System.err.println("PHP script execution was interrupted");
            Thread.currentThread().interrupt();
        }
    } catch (IOException e) {
        System.err.println("Error executing PHP script: " + e.getMessage());
        sendErrorResponse(textOut, 500, "Internal Server Error");
    }
}
    
    

    private static File getClassLocation() {
        try {
            // Obtenir l'emplacement du fichier .class
            String path = HttpServer.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();
            path = URLDecoder.decode(path, "UTF-8");
            File classLocation = new File(path);
    
            // Si c'est un fichier (par ex., un JAR), aller au repertoire parent
            if (classLocation.isFile()) {
                classLocation = classLocation.getParentFile();
            }
    
            // Ajouter le sous-repertoire "htdocs"
            File htdocsDir = new File(classLocation, "htdocs");
    
            // Si "htdocs" n'existe pas, le creer
            if (!htdocsDir.exists()) {
                System.out.println("Le dossier 'htdocs' est introuvable. Creation du dossier...");
                htdocsDir.mkdir();
            }
    
            return htdocsDir;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            // Retour par defaut si une erreur se produit
            return new File("htdocs");
        }
    }
    

    private static void serveDirectoryListing(File directory, PrintWriter out) throws IOException {
        StringBuilder dirListing = new StringBuilder();
        dirListing.append("<html><head>");
        dirListing.append("<style>");
        dirListing.append("body { font-family: Arial, sans-serif; margin: 20px; }");
        dirListing.append("h1 { color: #333; }");
        dirListing.append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }");
        dirListing.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        dirListing.append("th { background-color: #f4f4f4; font-weight: bold; }");
        dirListing.append("tr:nth-child(even) { background-color: #f9f9f9; }");
        dirListing.append("tr:hover { background-color: #f1f1f1; }");
        dirListing.append("</style>");
        dirListing.append("</head><body>");
        
        dirListing.append("<h1>Index of ").append(directory.getName()).append("</h1>");
        
        // Commencer le tableau
        dirListing.append("<table>");
        dirListing.append("<tr><th>Nom</th><th>Taille</th><th>Dernière modification</th></tr>");
        
        // Formatage de la date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String linkPath = file.isDirectory() ? 
                    fileName + "/" : 
                    fileName;
                
                // Taille du fichier
                String fileSize = file.isDirectory() ? "-" : formatFileSize(file.length());
                
                // Date de dernière modification
                String lastModified = sdf.format(new Date(file.lastModified()));
    
                // Ajouter une ligne au tableau
                dirListing.append("<tr>")
                    .append("<td><a href=\"").append(linkPath).append("\">")
                    .append(fileName)
                    .append(file.isDirectory() ? "/" : "")
                    .append("</a></td>")
                    .append("<td>").append(fileSize).append("</td>")
                    .append("<td>").append(lastModified).append("</td>")
                    .append("</tr>");
            }
        }
    
        // Fermer le tableau
        dirListing.append("</table>");
        dirListing.append("</body></html>");
        
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + dirListing.length());
        out.println();
        out.print(dirListing.toString());
        out.flush();
    }
    
    // Methode pour formater la taille du fichier (par exemple, en Ko, Mo, Go)
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
    

    private static void serveFile(File file, PrintWriter textOut, OutputStream rawOut) throws IOException {
        // Déterminer le type MIME du fichier à servir
        String mimeType = getMimeType(file.getName());
    
        // Vérifier si le fichier est de type texte
        if (mimeType.startsWith("text/")) {
            // Lire et envoyer le contenu du fichier texte en réponse HTTP
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                StringBuilder responseBody = new StringBuilder();
                String line;
    
                // Lire chaque ligne du fichier et l'ajouter au corps de la réponse
                while ((line = fileReader.readLine()) != null) {
                    responseBody.append(line).append("\n");
                }
    
                // Envoyer l'en-tête HTTP indiquant le succès (200 OK)
                textOut.println("HTTP/1.1 200 OK");
                textOut.println("Content-Type: " + mimeType); // Indiquer le type de contenu
                textOut.println("Content-Length: " + responseBody.length()); // Indiquer la longueur du contenu
                textOut.println();
                
                // Envoyer le corps de la réponse
                textOut.print(responseBody.toString());
                textOut.flush();
            }
        } else {
            // Traiter les fichiers non-textes (ex: images, binaires)
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedOutputStream bos = new BufferedOutputStream(rawOut)) {
    
                // Envoyer l'en-tête HTTP pour les fichiers binaires
                textOut.println("HTTP/1.1 200 OK");
                textOut.println("Content-Type: " + mimeType); // Indiquer le type de contenu
                textOut.println("Content-Length: " + file.length()); // Indiquer la longueur du fichier
                textOut.println();
                textOut.flush();
    
                // Lire le contenu du fichier par blocs et l'envoyer au client
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
                bos.flush();
            }
        }
    }
    
    private static String getMimeType(String fileName) {
        try {
            // Utilise Files.probeContentType pour detecter le type MIME
            Path filePath = Paths.get(fileName);
            String mimeType = Files.probeContentType(filePath);
    
            // Si probeContentType retourne null, definir un type par defaut
            return (mimeType != null) ? mimeType : "application/octet-stream";
        } catch (IOException e) {
            e.printStackTrace();
            // En cas d'erreur, retourner un type generique par defaut
            return "application/octet-stream";
        }
    }
    

    private static void sendErrorResponse(PrintWriter out, int statusCode, String message) {
        String errorBody = String.format("<html><body><h1>%d %s</h1></body></html>", statusCode, message);
        out.println("HTTP/1.1 " + statusCode + " " + message);
        out.println("Content-Type: text/html; charset=UTF-8");
        out.println("Content-Length: " + errorBody.length());
        out.println();
        out.print(errorBody);
        out.flush();
    }
}