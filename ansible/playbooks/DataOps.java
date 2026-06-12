import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;

public class DataOps {
    
    private static String getExpectedValue(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static void seed(int keyCount, String host, int port) throws Exception {
        System.out.println("Seeding " + keyCount + " keys...");
        int success = 0;
        for (int i = 1; i <= keyCount; i++) {
            String key = String.format("key:%04d", i);
            String value = getExpectedValue(key);
            try {
                if (p.waitFor() == 0) {
                    success++;
                } else {
                    System.out.println("Failed to set " + key);
                }
            } catch (Exception e) {
                System.out.println("Failed to set " + key + ": " + e.getMessage());
            }
        }
        System.out.println("Summary: total keys inserted: " + success);
    }

    private static void verify(int keyCount, String host, int port) throws Exception {
        System.out.println("Verifying " + keyCount + " keys...");
        int missing = 0;
        int mismatched = 0;
        for (int i = 1; i <= keyCount; i++) {
            String key = String.format("key:%04d", i);
            String expected = getExpectedValue(key);
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String actual = reader.readLine();
                p.waitFor();
                
                if (actual == null || actual.trim().isEmpty()) {
                    missing++;
                } else if (!actual.trim().equals(expected)) {
                    mismatched++;
                }
            } catch (Exception e) {
                missing++;
            }
        }
        
        if (missing == 0 && mismatched == 0) {
            System.out.println("PASS — " + keyCount + "/" + keyCount + " keys verified");
        } else {
            System.out.println("FAIL — " + missing + " keys missing, " + mismatched + " values mismatched");
            System.exit(1);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Action required (seed or verify)");
            System.exit(1);
        }
        String action = args[0];
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        
        if ("seed".equals(action)) {
            seed(count, "10.10.0.11", 6379);
        } else if ("verify".equals(action)) {
            verify(count, "10.10.0.11", 6379);
        }
    }
}
