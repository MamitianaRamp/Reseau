import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        try {
            Map<String, String> config = readConfig("config.txt");

            int proxyPort = Integer.parseInt(config.get("proxyPort"));
            String serverIp = config.get("serverIp");
            int serverPort = Integer.parseInt(config.get("serverPort"));
            long cacheExpirationTimeMs = Long.parseLong(config.get("cacheExpirationTimeMs"));
            long maxSize= Long.parseLong(config.get("maxFileSize"));
            Proxy proxy = new Proxy(proxyPort, serverIp, serverPort, cacheExpirationTimeMs,maxSize);
            proxy.start();
        } catch (Exception e) {
            System.err.println("Erreur : " + e.getMessage());
        }
    }

    private static Map<String, String> readConfig(String fileName) throws IOException {
        Map<String, String> config = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    config.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return config;
    }
}
