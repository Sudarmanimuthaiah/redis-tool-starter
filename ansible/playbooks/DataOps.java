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
        System.out.println("Seeding " + keyCount + " keys using cluster mode...");
        int success = 0;
        for (int i = 1; i <= keyCount; i++) {
            String key = String.format("key:%04d", i);
            String value = getExpectedValue(key);
            try {
                // Using -c for cluster mode redirection
                Process p = new ProcessBuilder("redis-cli", "-c", "-h", host, "-p", String.valueOf(port), "set", key, value).start();
                if (p.waitFor() == 0) {
                    success++;
                }
            } catch (Exception e) {
                // Silent fail for individual keys to not bloat logs
            }
        }
        System.out.println("Summary: total keys processed: " + keyCount + ", success: " + success);
    }

    private static void verify(int keyCount, String host, int port) throws Exception {
        System.out.println("Verifying " + keyCount + " keys using cluster mode...");
        int missing = 0;
        int mismatched = 0;
        int success = 0;
        for (int i = 1; i <= keyCount; i++) {
            String key = String.format("key:%04d", i);
            String expected = getExpectedValue(key);
            try {
                // Using -c for cluster mode redirection
                Process p = new ProcessBuilder("redis-cli", "-c", "-h", host, "-p", String.valueOf(port), "get", key).start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String actual = reader.readLine();
                p.waitFor();

                if (actual == null || actual.trim().isEmpty() || actual.trim().equals("(nil)")) {
                    missing++;
                } else if (!actual.trim().equals(expected)) {
                    mismatched++;
                } else {
                    success++;
                }
            } catch (Exception e) {
                missing++;
            }
        }

        System.out.println("Verification Summary:");
        System.out.println(" - Success: " + success);
        System.out.println(" - Missing: " + missing);
        System.out.println(" - Mismatched: " + mismatched);

        if (missing == 0 && mismatched == 0) {
            System.out.println("PASS — All keys verified");
        } else {
            System.out.println("FAIL — Integrity check failed");
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