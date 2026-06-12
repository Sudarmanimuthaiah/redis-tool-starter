import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedisTool {

    // ANSI Color Constants
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println(ANSI_YELLOW + "Usage: redis-tool <command> [options]" + ANSI_RESET);
            System.err.println("Commands: provision, data, status, upgrade, verify");
            System.err.println("Global Options: --use-docker (force execution inside container)");
            System.exit(1);
        }

        checkPrerequisites();

        String command = args[0];
        try {
            switch (command) {
                case "provision":
                    cmdProvision(args);
                    break;
                case "data":
                    cmdData(args);
                    break;
                case "status":
                    cmdStatus(args);
                    break;
                case "upgrade":
                    cmdUpgrade(args);
                    break;
                case "verify":
                    cmdVerify(args);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String runtime = null;

    private static void checkPrerequisites() {
        System.out.println("Checking prerequisites...");
        
        if (isCommandAvailable("docker")) {
            runtime = "docker";
        } else if (isCommandAvailable("podman")) {
            runtime = "podman";
        }
        
        if (runtime != null) {
            try {
                String version = runCommandOutput(runtime, "--version").trim();
                System.out.println("✓ " + version + " found");
            } catch (Exception e) {
                System.out.println("✗ Failed to get " + runtime + " version: " + e.getMessage());
                System.exit(1);
            }
        } else {
            System.out.println("✗ Container runtime not found (Docker or Podman)");
            System.out.println("Install Podman: https://podman.io/docs/installation");
            System.out.println("Install Docker: https://docs.docker.com/engine/install/");
            System.exit(1);
        }

        if (isCommandAvailable("ansible-playbook")) {
            try {
                String versionOutput = runCommandOutput("ansible-playbook", "--version").split("\n")[0];
                Pattern pattern = Pattern.compile("core (\\d+\\.\\d+)");
                Matcher matcher = pattern.matcher(versionOutput);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    String[] parts = version.split("\\.");
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    if (major > 2 || (major == 2 && minor >= 14)) {
                        System.out.println("✓ " + versionOutput + " found");
                    } else {
                        System.out.println("! Ansible version should be 2.14+. Found: " + version + ". Will use Docker.");
                    }
                } else {
                    System.out.println("✓ " + versionOutput + " found");
                }
            } catch (Exception e) {
                System.out.println("! Failed to check Ansible version: " + e.getMessage() + ". Will use Docker.");
            }
        } else {
            if (runtime != null) {
                System.out.println("! Ansible not found on host. Will use Docker container.");
            } else {
                System.out.println("✗ Ansible not found and no container runtime available.");
                System.exit(1);
            }
        }
        System.out.println("Proceeding...\n");
    }

    private static void runPlaybook(String playbook, Map<String, String> extraVars, String[] fullArgs) throws Exception {
        boolean isWsl = System.getenv("WSL_DISTRO_NAME") != null || System.getenv("WSL_INTEROP") != null;
        boolean forceDocker = hasFlag(fullArgs, "--use-docker");
        boolean useDocker = !isCommandAvailable("ansible-playbook") 
            || System.getProperty("os.name").toLowerCase().contains("win")
            || isWsl
            || forceDocker
            || "podman".equals(runtime);
        
        if (forceDocker) {
            System.out.println("! Forced Docker execution requested.");
        } else if (isWsl) {
            System.out.println("! WSL2 detected. Using containerized runner to ensure network connectivity.");
        } else if ("podman".equals(runtime) && isCommandAvailable("ansible-playbook")) {
            System.out.println("! Podman detected. Using containerized runner to ensure network connectivity.");
        }
        
        List<String> cmd = new ArrayList<>();
        if (useDocker) {
            cmd.add("docker");
            cmd.add("run");
            cmd.add("--rm");
            cmd.add("-v");
            cmd.add(new File(".").getAbsolutePath() + ":/work");
            cmd.add("-w");
            cmd.add("/work");
            cmd.add("--network");
            cmd.add("infra_redis-net");
            cmd.add("-e");
            cmd.add("ANSIBLE_CONFIG=/work/ansible/ansible.cfg");
            cmd.add("willhallonline/ansible");
            cmd.add("sh");
            cmd.add("-c");
            
            StringBuilder innerCmd = new StringBuilder();
            innerCmd.append("cp infra/id_rsa /tmp/id_rsa && chmod 600 /tmp/id_rsa && ");
            innerCmd.append("ansible-playbook ansible/playbooks/").append(playbook);
            innerCmd.append(" -e ansible_ssh_private_key_file=/tmp/id_rsa");
            
            if (extraVars != null && !extraVars.isEmpty()) {
                for (Map.Entry<String, String> entry : extraVars.entrySet()) {
                    innerCmd.append(" -e ").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
            
            cmd.add(innerCmd.toString());
        } else {
            cmd.add("ansible-playbook");
            cmd.add("ansible/playbooks/" + playbook);
            
            if (extraVars != null && !extraVars.isEmpty()) {
                StringBuilder varsStr = new StringBuilder();
                for (Map.Entry<String, String> entry : extraVars.entrySet()) {
                    if (varsStr.length() > 0) varsStr.append(",");
                    varsStr.append(entry.getKey()).append("=").append(entry.getValue());
                }
                cmd.add("--extra-vars");
                cmd.add(varsStr.toString());
            }
            
            File inventory = new File("ansible/inventory/hosts.ini");
            if (inventory.exists()) {
                cmd.add("-i");
                cmd.add(inventory.getPath());
            }
        }
        
        System.out.println("Executing: " + String.join(" ", cmd));
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (!useDocker) {
            pb.environment().put("ANSIBLE_CONFIG", new File("ansible.cfg").getAbsolutePath());
        }
        pb.inheritIO();
        
        Process p = pb.start();
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            System.err.println("Error executing playbook");
            System.exit(exitCode);
        }
    }

    private static String getArg(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static void cmdProvision(String[] args) throws Exception {
        String version = getArg(args, "--version");
        if (version == null) {
            System.err.println("--version is required");
            System.exit(1);
        }
        String masters = getArg(args, "--masters");
        if (masters == null) masters = "3";
        String replicas = getArg(args, "--replicas-per-master");
        if (replicas == null) replicas = "1";
        
        System.out.println("Provisioning Redis Cluster v" + version + " with " + masters + " masters and " + replicas + " replicas per master");
        Map<String, String> vars = new HashMap<>();
        vars.put("redis_version", version);
        vars.put("masters", masters);
        vars.put("replicas_per_master", replicas);
        runPlaybook("provision.yml", vars, args);
    }

    private static void cmdData(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Missing data subcommand (seed or verify)");
            System.exit(1);
        }
        String subcmd = args[1];
        if (subcmd.equals("seed")) {
            String keys = getArg(args, "--keys");
            if (keys == null) keys = "1000";
            System.out.println("Seeding " + keys + " keys into the cluster");
            Map<String, String> vars = new HashMap<>();
            vars.put("action", "seed");
            vars.put("key_count", keys);
            runPlaybook("data.yml", vars, args);
        } else if (subcmd.equals("verify")) {
            System.out.println("Verifying data integrity");
            Map<String, String> vars = new HashMap<>();
            vars.put("action", "verify");
            runPlaybook("data.yml", vars, args);
        } else {
            System.err.println("Unknown data subcommand: " + subcmd);
            System.exit(1);
        }
    }

    private static void cmdStatus(String[] args) throws Exception {
        System.out.println("Checking cluster status");
        runPlaybook("status.yml", null, args);
    }

    private static void cmdUpgrade(String[] args) throws Exception {
        String targetVersion = getArg(args, "--target-version");
        if (targetVersion == null) {
            System.err.println("--target-version is required");
            System.exit(1);
        }
        String strategy = getArg(args, "--strategy");
        if (strategy == null) strategy = "rolling";
        
        System.out.println("Performing rolling upgrade to v" + targetVersion + " using strategy: " + strategy);
        Map<String, String> vars = new HashMap<>();
        vars.put("target_version", targetVersion);
        vars.put("strategy", strategy);
        runPlaybook("upgrade.yml", vars, args);
    }

    private static void cmdVerify(String[] args) throws Exception {
        if (hasFlag(args, "--full")) {
            System.out.println("Performing full verification");
            Map<String, String> vars = new HashMap<>();
            vars.put("full", "True");
            runPlaybook("verify.yml", vars, args);
        } else {
            System.out.println("Verifying data integrity (standard)");
            Map<String, String> vars = new HashMap<>();
            vars.put("action", "verify");
            runPlaybook("data.yml", vars, args);
        }
    }

    private static boolean isCommandAvailable(String cmd) {
        try {
            Process p;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                p = new ProcessBuilder("where", cmd).start();
            } else {
                p = new ProcessBuilder("which", cmd).start();
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String runCommandOutput(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        p.waitFor();
        return sb.toString();
    }
}
