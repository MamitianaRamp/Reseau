import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Proxy {
    private int proxyPort; 
    private String serverIp; 
    private int serverPort; 
    private long expiration; 
    private long maxSize; 


    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Proxy(int proxyPort, String serverIp, int serverPort, long expiration, long maxSize) {
        this.proxyPort = proxyPort;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.expiration = expiration;
        this.maxSize = maxSize;
    }

    public void start() {
        // Thread 1 : Nettoyage des entrées expirées du cache
        new Thread(this::cleanExpiredCache).start();

        // Thread 2 : Gestion des commandes via le terminal
        new Thread(this::handleTerminalCommands).start();

        try (ServerSocket serverSocket = new ServerSocket(proxyPort)) {
            System.out.println("Proxy HTTP démarré sur le port " + proxyPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du démarrage du Proxy : " + e.getMessage());
        }
    }
    private void cleanExpiredCache() {
        while (true) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > expiration);
            try {
                Thread.sleep(60 * 1000); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Erreur dans le thread de nettoyage du cache.");
            }
        }
    }

    private void handleTerminalCommands() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("Tapez 'clear-cache', 'show-cache', 'remove [fichier]', 'set-expiration [temps_ms]', 'set-expiration-file [fichier] [temps_ms]', 'run [fichier]', ou 'exit' :");
                String command = scanner.nextLine();
    
                if ("clear-cache".equalsIgnoreCase(command)) {
                    cache.clear();
                    System.out.println("Cache vidé !");
                } else if ("show-cache".equalsIgnoreCase(command)) {
                    showCache();
                } else if (command.startsWith("remove ")) {
                    String fileName = command.substring(7).trim();
                    if (!fileName.isEmpty()) {
                        removeFile(fileName);
                    } else {
                        System.out.println("Veuillez spécifier le nom du fichier à supprimer.");
                    }
                } else if (command.startsWith("set-expiration ")) {
                    try {
                        long newExpiration = Long.parseLong(command.substring(15).trim());
                        updateExpirationTime(newExpiration);
                    } catch (NumberFormatException e) {
                        System.out.println("Veuillez entrer un nombre valide pour le temps d'expiration.");
                    }
                } else if (command.startsWith("set-expiration-file ")) {
                    try {
                        String[] parts = command.substring(20).trim().split(" ");
                        if (parts.length == 2) {
                            String fileName = parts[0];
                            long newExpiration = Long.parseLong(parts[1]);
                            updateExpiration(fileName, newExpiration);
                        } else {
                            System.out.println("Veuillez fournir le nom du fichier et le temps d'expiration.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Veuillez entrer un nombre valide pour le temps d'expiration.");
                    }
                } else if (command.startsWith("run ")) {
                    String fileName = command.substring(4).trim();
                    if (!fileName.isEmpty()) {
                        byte[] response = run(fileName, true);
                        if (response != null) {
                            System.out.println("Fichier traité avec succès via le terminal.");
                        }
                    } else {
                        System.out.println("Veuillez fournir un nom de fichier.");
                    }
                } else if ("exit".equalsIgnoreCase(command)) {
                    System.out.println("Arrêt du proxy...");
                    System.exit(0);
                } else {
                    System.out.println("Commande inconnue : " + command);
                }
            }
        }
    }
    
    public boolean removeFile(String fileName) {
        if (cache.containsKey(fileName)) {
            cache.remove(fileName);
            System.out.println("Le fichier '" + fileName + "' a été supprimé du cache.");
            return true;
        } else {
            System.out.println("Erreur : Le fichier '" + fileName + "' n'existe pas dans le cache.");
            return false;
        }
    }
    
 public byte[] run(String fileName, boolean isTerminal) {
        try {
            if (cache.containsKey(fileName)) {
                if (isTerminal) {
                    System.out.println("Le fichier '" + fileName + "' a été trouvé dans le cache.");
                }
                return cache.get(fileName).data;
            } else {
                if (isTerminal) {
                    System.out.println("Le fichier '" + fileName + "' n'est pas dans le cache. Requête en cours vers le serveur...");
                }
                byte[] serverResponse = fetchFromServer(fileName);

                if (serverResponse != null) {
                    long currentSize = getCurrentCacheSize();
                    long newSize = currentSize + serverResponse.length;

                    if (serverResponse.length > maxSize) {
                        System.out.println("Le fichier '" + fileName + "' dépasse la taille maximale autorisée pour le cache et sera refusé.");
                        return null;
                    }

                    if (newSize > maxSize) {
                        System.out.println("Le fichier '" + fileName + "' ne peut pas être ajouté, car il dépasse la capacité actuelle du cache.");
                        return null;
                    }

                    cache.put(fileName, new CacheEntry(serverResponse));
                    if (isTerminal) {
                        System.out.println("Le fichier '" + fileName + "' a été récupéré depuis le serveur et stocké dans le cache.");
                    }
                    return serverResponse;
                } else {
                    if (isTerminal) {
                        System.out.println("Erreur : le fichier '" + fileName + "' est introuvable sur le serveur.");
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            if (isTerminal) {
                System.err.println("Erreur lors de l'exécution de 'run' : " + e.getMessage());
            }
            return null;
        }
    }

    private byte[] fetchFromServer(String fileName) {
        try (Socket serverSocket = new Socket(serverIp, serverPort);
             OutputStream serverOutput = serverSocket.getOutputStream();
             InputStream serverInput = serverSocket.getInputStream()) {

            PrintWriter out = new PrintWriter(new OutputStreamWriter(serverOutput, "UTF-8"), true);
            out.print("GET /" + fileName + " HTTP/1.1\r\n");
            out.print("Host: " + serverIp + "\r\n");
            out.print("User-Agent: ProxyClient/1.0\r\n");
            out.print("Accept: */*\r\n");
            out.print("Connection: close\r\n\r\n");
            out.flush();

            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = serverInput.read(buffer)) != -1) {
                responseBuffer.write(buffer, 0, bytesRead);
            }

            return responseBuffer.toByteArray();
        } catch (IOException e) {
            System.err.println("Erreur lors de la requête vers le serveur : " + e.getMessage());
            return null;
        }
    }

    private void showCache() {
        if (cache.isEmpty()) {
            System.out.println("Le cache est vide.");
        } else {
            System.out.println("Contenu du cache :");
            cache.forEach((key, value) -> {
                System.out.println(" - Fichier : " + key + " (taille : " + value.data.length + " octets, timestamp : " + value.timestamp + ")");
            });
        }
    }

    public void updateExpiration(String fileName, long newexpiration) {
        CacheEntry entry = cache.get(fileName);
        if (entry != null) {
            entry.timestamp = System.currentTimeMillis();
            System.out.println("Le temps d'expiration du fichier '" + fileName + "' a été mis à jour.");
        } else {
            System.out.println("Le fichier '" + fileName + "' n'existe pas dans le cache.");
        }
    }
    private long getCurrentCacheSize() {
        return cache.values().stream().mapToLong(entry -> entry.data.length).sum();
    }
    public void updateExpirationTime(long newExpirationTimeMs) {
        this.expiration = newExpirationTimeMs;
        long now = System.currentTimeMillis();
    
    
        cache.forEach((key, value) -> {
            value.timestamp = now;  
        });
    
        System.out.println("Le temps d'expiration global a été mis à jour à " + newExpirationTimeMs + " ms.");
    }
    
    static class CacheEntry {
        byte[] data;
        long timestamp;

        CacheEntry(byte[] data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream clientOutput = clientSocket.getOutputStream()) {

                String requestLine = in.readLine();
                System.out.println("Requête reçue : " + requestLine);

                if (requestLine != null && requestLine.startsWith("GET")) {
                    String fileName = requestLine.split(" ")[1];
                    if (fileName.startsWith("/")) fileName = fileName.substring(1);

                    byte[] response = Proxy.this.run(fileName, false);
                    if (response != null) {
                        clientOutput.write(response);
                    } else {
                        String errorResponse = "HTTP/1.1 404 Not Found\r\n\r\nErreur 404 : fichier introuvable.";
                        clientOutput.write(errorResponse.getBytes());
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur dans ClientHandler : " + e.getMessage());
            }
        }
    }

}
