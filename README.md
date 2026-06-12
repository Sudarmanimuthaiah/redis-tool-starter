# Redis Cluster Lifecycle Tool

This project provides a CLI tool (`redis-tool`) that orchestrates Ansible to provision, operate, and perform zero-downtime rolling upgrades of a 6-node Redis Cluster running in local containers.

## Prerequisites

- **Container Runtime**: Podman (recommended) or Docker Engine.
- **Ansible**: Version 2.14 or higher.
- **Java**: Version 11 or higher (to run the CLI and data operations).

## Infrastructure Setup

1. Navigate to the `infra/` directory.
2. Build and start the containers:
   ```bash
   # Using Podman
   podman-compose up -d --build
   
   # Using Docker
   docker-compose up -d --build
   ```
   *Note: This setup automatically handles SSH key-based authentication between your host and the containers.*

## Usage

The `redis-tool` CLI supports the following phases:

### Phase 1: Provision
Installs Redis and forms the cluster.
```bash
./redis-tool provision --version 7.0.15 --masters 3 --replicas-per-master 1
```

### Phase 2: Data Operations
Seed and verify deterministic data.
```bash
./redis-tool data seed --keys 1000
./redis-tool data verify
```

### Phase 3: Status
Check the current state of the cluster.
```bash
./redis-tool status
```

### Phase 4: Rolling Upgrade
Perform a zero-downtime rolling upgrade.
```bash
./redis-tool upgrade --target-version 7.2.6 --strategy rolling
```

### Phase 5: Full Verification
Comprehensive post-upgrade health check.
```bash
./redis-tool verify --full
```

## Rolling Upgrade Strategy

The tool implements a strictly sequential upgrade strategy to ensure zero downtime:
1. **Pre-flight checks**: Verifies cluster health and data integrity.
2. **Upgrade Replicas**: Replicas are upgraded one-by-one. Each replica is stopped, upgraded, and verified to be back in sync before moving to the next.
3. **Upgrade Masters (with Failover)**: For each master, a `CLUSTER FAILOVER` is triggered on its replica. The replica becomes the new master, and the old master (now a replica) is then upgraded.
4. **Post-upgrade verification**: Final data integrity and version checks.

## Assumptions & Limitations
- Assumes a 6-node setup as defined in `infra/compose.yml`.
- Redis is installed from source to ensure exact version control.
- SSH access to containers is established via the `id_rsa` key generated during setup.
egrity and version checks.

## Assumptions & Limitations
- Assumes a 6-node setup as defined in `infra/compose.yml`.
- Redis is installed from source to ensure exact version control.
- SSH access to containers is established via the `id_rsa` key generated during setup.
