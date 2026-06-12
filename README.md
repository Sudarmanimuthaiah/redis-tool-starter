# Redis Cluster Lifecycle Tool

A robust CLI tool designed to orchestrate the entire lifecycle of a 6-node Redis Cluster using Java, Ansible, and Docker. This tool is optimized for both Windows (PowerShell/IntelliJ) and Linux (Ubuntu/WSL2).

## 🚀 Quick Start (WSL2 / Ubuntu)

### 1. Prerequisites
Ensure you have the following installed:
*   **Docker Desktop** (with WSL2 integration enabled for your distro)
*   **Java JDK 11+**
*   **Git**

### 2. Infrastructure Setup
Bring up the 6 Ubuntu-based nodes:
```bash
cd infra
docker-compose up -d --build
cd ..
```

### 3. Provision the Cluster
Install Redis 7.0.15 and form the cluster:
```bash
chmod +x redis-tool
./redis-tool provision --version 7.0.15
```

---

## 🛠️ CLI Commands

The tool provides a unified interface for all operations:

| Command | Description | Example |
| :--- | :--- | :--- |
| `provision` | Installs Redis and forms the cluster | `./redis-tool provision --version 7.0.15` |
| `status` | Displays cluster health and topology | `./redis-tool status` |
| `data seed` | Injects test data into the cluster | `./redis-tool data seed --keys 1000` |
| `verify` | Validates data integrity across nodes | `./redis-tool verify` |
| `upgrade` | Performs a rolling version upgrade | `./redis-tool upgrade --target-version 7.2.4` |

---

## 🏗️ Project Structure

```text
redis-tool-starter/
├── redis-tool             # CLI wrapper for Linux/WSL2
├── redis-tool.bat         # CLI wrapper for Windows
├── RedisTool.java         # Core Java Orchestrator (Detects ENV & runs Ansible)
│
├── ansible/               # Automation Layer
│   ├── ansible.cfg        # Ansible global settings & role paths
│   ├── inventory/
│   │   └── hosts.ini      # Cluster topology (Static IPs 10.10.0.11-16)
│   ├── playbooks/         # Lifecycle workflows
│   │   ├── provision.yml  # Bootstraps Redis & forms cluster
│   │   ├── status.yml     # Real-time health & topology reporting
│   │   ├── upgrade.yml    # Orchestrates zero-downtime rolling upgrades
│   │   ├── data.yml       # Data operations (Seed/Verify)
│   │   └── DataOps.java   # Java utility for cluster-aware Redis ops
│   └── roles/
│       └── redis/         # Modular Redis configuration & management
│           ├── tasks/     # Logic for install, symlinking, & services
│           └── templates/ # Jinja2 templates for redis.conf
│
├── infra/                 # Infrastructure Layer
│   ├── compose.yml        # Docker Compose: Defines 6-node Redis network
│   ├── Containerfile      # Docker: Ubuntu image with SSH & build tools
│   ├── id_rsa             # Private key for SSH/Ansible automation
│   └── id_rsa.pub         # Public key injected into nodes
│
├── output/                # Directory for logs and command outputs
│   ├── provision_output.txt
│   ├── status_output.txt
│   ├── data_seed_output.txt
│   ├── verify_output.txt
│   └── upgrade_output.txt
└── README.md              # Project documentation
```

---

## 🔍 Detailed Architecture & Lifecycle

This project is a high-level orchestrator that bridges the gap between local development environments and complex distributed systems. Below is a deep dive into how it works.

### 1. The Orchestration Flow
The tool operates in three distinct layers:
1.  **Orchestrator (Java)**: The `RedisTool.java` serves as the entry point. It handles argument parsing, prerequisite checking (Docker/Ansible), and environment detection.
2.  **Automation (Ansible)**: Java invokes `ansible-playbook` commands. Ansible handles the "heavy lifting"—SSH connectivity, source code compilation, configuration templating, and service management.
3.  **Infrastructure (Docker)**: Docker provides the sandbox. We use a custom Ubuntu-based image that mimics a real server environment, complete with an SSH daemon for Ansible to connect to.

### 2. Cross-Platform Networking (The WSL2/Windows Bridge)
One of the most complex parts of this tool is ensuring it works on Windows and WSL2.
*   **The Problem**: On Windows and WSL2, the Docker network (`10.10.0.x`) is isolated from the host shell. A native `ansible-playbook` command on the host cannot "see" the containers.
*   **The Solution**: When the tool detects Windows or WSL2, it doesn't run Ansible on the host. Instead, it spins up a temporary **Ansible Runner Container** (`willhallonline/ansible`) and joins it to the `infra_redis-net` network. This "runner" then executes the playbooks from *within* the network, allowing it to reach the nodes perfectly.

### 3. Redis Cluster Formation
*   **Provisioning**: The tool downloads the Redis source code, compiles it using the `libc` allocator for stability, and installs it.
*   **Clustering**: Once the 6 nodes are running, the tool uses `redis-cli --cluster create` to link them. It automatically calculates the master/replica distribution based on your `--masters` and `--replicas-per-master` inputs.
*   **Persistence**: Data is stored in `/var/lib/redis` inside each container, and configuration is managed via `/etc/redis/redis.conf`.

### 4. Zero-Downtime Rolling Upgrades
The `upgrade` command implements a "Safe-Stop" strategy:
1.  **Replica First**: It upgrades the replicas first. Since replicas don't serve write traffic (usually), this is low risk.
2.  **Failover**: To upgrade a master, the tool can trigger a manual failover (`CLUSTER FAILOVER`) to promote its upgraded replica to master.
3.  **Re-pair**: Once the old master is upgraded, it rejoins as a replica, completing the cycle without the cluster ever going offline.

### 5. Data Integrity (DataOps)
The `DataOps.java` utility is copied onto the first node and executed there.
*   **Cluster-Aware**: It uses `redis-cli -c` to follow "MOVED" redirections, ensuring that keys are sent to the correct master based on the CRC16 hash of the key.
*   **Verification**: It uses a deterministic SHA-256 hashing algorithm to generate values based on keys, allowing the `verify` command to prove that no data was lost or corrupted during upgrades or cluster rebalancing.

---

## 💡 Key Features & Fixes

### Cross-Platform Reliability
The tool automatically detects your environment (Windows vs. WSL2 vs. Native Linux) and adjusts its execution strategy:
*   **WSL2/Windows Isolation**: Detects network isolation and automatically runs Ansible inside a Docker container joined to the `infra_redis-net` network.
*   **Permission Handling**: Automatically manages SSH key permissions (`chmod 600`) by cloning keys into temporary secure storage during execution.
*   **Binary Pathing**: Ensures `redis-cli` and `redis-server` are always accessible via standard symlinks in `/usr/bin`.

### Robust Compilation
*   **MALLOC Customization**: Uses `libc` allocator to ensure stable compilation within containerized environments.
*   **Atomic Upgrades**: Supports rolling upgrades by targeting individual nodes to maintain cluster availability.

---

## 📝 Configuration
*   **Network**: Nodes are assigned static IPs from `10.10.0.11` to `10.10.0.16`.
*   **SSH**: Root access is enabled via the bundled `id_rsa` key.
*   **Redis**: Default installation path is `/usr/local/bin` with configuration at `/etc/redis/redis.conf`.
