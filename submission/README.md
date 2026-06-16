# Redis Cluster Lifecycle Tool

A robust CLI tool designed to orchestrate the entire lifecycle of a 6-node Redis Cluster using Java, Ansible, and Docker. This tool is optimized for both Windows (PowerShell/IntelliJ) and Linux (Ubuntu/WSL2).

## 🚀 Quick Start

### 1. Prerequisites
Before running the tool, ensure your environment meets these requirements:
*   **Container Runtime**: Docker Engine or **Podman** (Recommended). The tool prefers Podman for its open-source nature.
*   **Java**: JDK 11+ must be installed to run the orchestrator.
*   **Ansible**: `ansible-playbook` version **2.14+** is required.
*   **WSL2/Linux**: Recommended for the best experience.

### 2. Infrastructure Setup
Bring up the 6-node Ubuntu infrastructure using Docker Compose:
```bash
cd infra
docker compose up -d --build
cd ..
```

### 3. Provision the Cluster
Install Redis and form the 6-node cluster (3 Masters, 3 Replicas):
```bash
chmod +x redis-tool
./redis-tool provision --version 7.0.15
```

---

## 🛠 CLI Commands

The tool provides a unified interface for all operations:

| Command             | Description                                     | Example                                       |
|:--------------------|:------------------------------------------------|:----------------------------------------------|
| `provision`         | Installs Redis and forms the cluster            | `./redis-tool provision --version 7.0.15`     |
| `status`            | Displays cluster health and topology            | `./redis-tool status`                         |
| `data seed`         | Injects test data into the cluster              | `./redis-tool data seed --keys 1000`          |
| `upgrade`           | Performs a zero-downtime rolling upgrade         | `./redis-tool upgrade --target-version 7.2.4` |
| `verify --full`     | Comprehensive Phase 5 health & integrity check  | `./redis-tool verify --full`                  |

---

## 📁 Project Structure

```text
submission/
├── redis-tool           # CLI wrapper (Bash)
├── RedisTool.java       # Core Java Orchestrator (Prerequisite check & Task Runner)
├── README.md            # Project documentation
│
├── ansible/             # Automation Layer
│   ├── ansible.cfg      # Ansible configuration
│   ├── inventory/       # Cluster topology (Static IPs 10.10.0.11-16)
│   ├── playbooks/       # Lifecycle workflows (Provision, Upgrade, Status, Verify)
│   └── roles/           # Modular Redis configuration (Tasks, Templates, Handlers)
│
├── infra/               # Infrastructure Layer
│   ├── compose.yml      # Docker Compose: Defines 6-node Redis network
│   ├── Containerfile    # Ubuntu base image with SSH & build tools
│   └── id_rsa           # SSH Private key for automation
│
└── output/              # Command Logs & Reports
    ├── provision_output.txt
    ├── status_output.txt
    ├── upgrade_output.txt
    └── verify_output.txt
```

---

## 🔍 Phase 5: Full Verification
Running `./redis-tool verify --full` performs a comprehensive health check:
1.  **Data Integrity**: Validates that all seeded data is retrievable and correct.
2.  **Version Consistency**: Confirms all nodes are running the exact target version.
3.  **Topology Health**: Verifies all 16,384 slots are covered and replicas are correctly assigned.
4.  **Cluster State**: Ensures the cluster reports a global `ok` status.
5.  **Replication Health**: Confirms `master_link_status: up` for all replicas.

---

## 🔄 Rolling Upgrade Strategy
The tool implements a safe rolling upgrade path:
1.  **Replica Upgrade**: Upgrades all replica nodes first to maintain availability.
2.  **Orchestrated Failover**: For each master, a failover is triggered to its upgraded replica.
3.  **Old Master Upgrade**: The former master is upgraded and rejoins the cluster as a replica.
4.  **Verification**: A full health check is performed to ensure the cluster is stable.

---

## 💡 Maintenance Tips

### Persistence across Reboots
To ensure containers start automatically after a system reboot:
```bash
cd infra
docker compose up -d
```
If Redis processes are not running after a reboot, run:
```bash
./redis-tool provision --version 7.0.15
```
This will safely restart the Redis services without data loss.

### Graceful Shutdown
To prevent data corruption, stop the nodes gracefully before shutting down your host:
```bash
cd infra
docker compose stop
```
