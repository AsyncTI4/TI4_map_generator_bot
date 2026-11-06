# Deployment and rollover

How a change on `master` reaches the production bot, and how the running bot hands over to
the new one without two processes acting on Discord or the games at once.

The pieces:

| Piece | File |
| --- | --- |
| Deploy workflow | [.github/workflows/deploy-to-production.yml](.github/workflows/deploy-to-production.yml) |
| Rollout script (run on the host) | [scripts/deploy_tibot_single_instance.sh](scripts/deploy_tibot_single_instance.sh) |
| Container setup (Traefik + `tibot`) | [docker-compose.production.yml](docker-compose.production.yml) |
| Image build | [Dockerfile](Dockerfile) |
| Startup order | [AsyncTI4DiscordBot.java](src/main/java/ti4/AsyncTI4DiscordBot.java) |
| Shutdown order | `JdaService.shutdown()` in [JdaService.java](src/main/java/ti4/discord/JdaService.java) |
| Active lease | [ti4.spring.service.deploy](src/main/java/ti4/spring/service/deploy) |
| Health, readiness, drain endpoints | [PingController](src/main/java/ti4/spring/api/PingController.java), [ReadyController](src/main/java/ti4/spring/api/publicstatus/ReadyController.java), [DeployController](src/main/java/ti4/spring/api/publicstatus/DeployController.java) |

## 1. The deploy workflow

`BuildAndDeploy` runs on every push to `master` and on manual dispatch. A newer push cancels a
deploy that is still running (`cancel-in-progress`).

```mermaid
flowchart TD
    push([Push to master / manual dispatch]) --> backup1[saveGamesBeforeDeploy<br/>commit and push save files on the host]
    backup1 --> build["buildImage<br/>docker build, push to GHCR<br/>tags sha-COMMIT and master"]
    build --> ssh[Wait for the host SSH port<br/>up to ~15 min]
    ssh --> remote

    subgraph remote [On the Hostinger host, over SSH]
        pull["git pull --ff-only in the host repo<br/>compose file, scripts, src/main/resources"]
        pull --> img["docker compose pull tibot<br/>IMAGE_TAG = sha-COMMIT"]
        img --> traefik[docker compose up -d traefik]
        traefik --> rollout[["deploy_tibot_single_instance.sh<br/>see section 2"]]
        rollout --> verify{Exactly one<br/>tibot container?}
        verify -->|More than one| extras["Keep the one on the new image<br/>stop the rest without draining"]
        extras --> verify2{Exactly one now?}
        verify -->|Yes| prune[Prune images and build cache older than 48h]
        verify2 -->|Yes| prune
        verify2 -->|No| fail([Job fails])
    end

    prune --> backup2[saveGamesAfterDeploy<br/>commit and push save files again]
```

Each remote step also posts a timestamped line to the deploy Discord webhook.

Two things come from the host checkout rather than from the image: the compose file and
rollout script, and `src/main/resources`, which is mounted read-only into the container at
`/opt/resources`. The image only carries the compiled jar and classpath config. Save files
live on the host too, mounted at `/opt/STORAGE`.

`RestartBot` ([restart-bot.yml](.github/workflows/restart-bot.yml)) runs the same rollout
script against the `master` image tag without building, so it is a rollover onto the same code.
`StopBot` drains and stops every `tibot` container, and `StartBot` brings one up with plain
`docker compose up`, with no rollover.

## 2. The rollout script

The script never replaces the old container first. It adds one new container next to the
old one, waits for it to be healthy, then drains and removes the old one. If the new container
never gets healthy, it is removed and the old one keeps running.

```mermaid
flowchart TD
    start([Start]) --> clean[Remove stopped tibot containers]
    clean --> scale["compose up --scale tibot=N+1 --no-recreate<br/>creates exactly one new container"]
    scale --> one{Exactly one<br/>new container?}
    one -->|No| abort([Remove the new ones, exit 1])
    one -->|Yes| wait[Poll the Docker health status<br/>up to 540 s]
    wait --> healthy{healthy?}
    healthy -->|No| rollback(["Print health + last 200 log lines<br/>remove the new container, exit 1<br/>old container untouched"])
    healthy -->|Yes| pause[Wait 2 s]
    pause --> drain["For each old container:<br/>POST /api/public/deploy/drain from inside it<br/>then poll /api/public/ready up to 15 s until it fails"]
    drain --> stop["docker stop --time 600, docker rm"]
    stop --> check{Exactly one<br/>tibot running?}
    check -->|Yes| done([Rollout complete])
    check -->|No| bad([exit 1])
```

"Healthy" is the compose healthcheck on `GET /api/public/ping`. Ping returns `200` only once
the process has reached lease participation, near the end of startup (see section 3). A new
process that is healthy is therefore fully started and waiting on standby; it is not yet
the active bot.

The drain endpoint only accepts loopback callers, which is why the script calls it with
`docker exec` inside the old container.

## 3. Rollover inside the bot: the active lease

During every rollover two bot processes are connected to Discord with the same token at the
same time, and both receive every event. One row in the Postgres `active_lease` table decides
which process acts. Each process has a random `INSTANCE_ID` (a new UUID per JVM start).

| Setting | Default | Property |
| --- | --- | --- |
| Lease key | `discord-bot` | `ti4.deploy.lease-key` |
| Lease duration | 3600 s | `ti4.deploy.lease-duration-seconds` |
| Heartbeat / renew interval | 5 s | `ti4.deploy.heartbeat-interval-seconds` |

The active process renews the lease every heartbeat, so it stays about an hour ahead of
expiry. A standby process can only claim the lease when the row is empty, already its own,
or expired.

```mermaid
sequenceDiagram
    autonumber
    participant Script as Rollout script
    participant New as New tibot (standby)
    participant DB as Postgres active_lease
    participant Old as Old tibot (active)
    participant Traefik

    Note over Old,DB: Old renews the lease every 5 s
    Script->>New: compose up --scale (start new container)
    New->>New: Spring up, start managed game preload,<br/>JDA login, guilds, static data, warm handlers
    New->>DB: tryAcquireLease
    DB-->>New: refused (Old holds an unexpired lease)
    New->>DB: LISTEN active_lease_released
    New->>New: lease participation on, register crons,<br/>markProcessReady
    Script->>New: Docker healthcheck GET /api/public/ping
    New-->>Script: 200 pong (healthy)
    Script->>Old: POST /api/public/deploy/drain
    Old->>Old: draining = true, ready = false,<br/>stop participating, close Spring context
    Traefik->>Old: GET /api/public/ready
    Old-->>Traefik: 503 (Traefik stops routing to Old)
    Old->>Old: Shutdown steps: executors, crons, pipelines,<br/>flush active hours
    Old->>DB: releaseLease (expire it, pg_notify)
    DB-->>New: NOTIFY active_lease_released
    New->>DB: tryAcquireLease
    DB-->>New: granted
    New->>New: active = true: lease start jobs,<br/>GameManager.warmup, data migrations, ready = true
    Traefik->>New: GET /api/public/ready
    New-->>Traefik: 200 (Traefik routes to New)
    Old->>Old: send buffered logs, shut down JDA, exit
    Script->>Old: docker stop --time 600, docker rm
```

If the `LISTEN` connection is down, the 5 s heartbeat poll on the standby (`maintainLease`)
picks up the released lease instead, so the notification only makes the handover faster.

### What each process is allowed to do

`ActiveLeaseService` keeps three flags per process: `active` (holds the lease), `draining`
and `ready`. The gates built from them:

| Work | Allowed when | Where |
| --- | --- | --- |
| Discord interactions (buttons, commands, modals, selects, messages, …) | `active` and not `draining` | each listener in `ti4.discord.interactions.listeners` |
| Scheduled crons | `active`, lease still owned in the DB, and not `draining` | first line of every cron, see [cron README](src/main/java/ti4/cron/README.md) |
| HTTP API (website) | `ready`, lease owned and not `draining` | [CircuitBreakerFilter](src/main/java/ti4/spring/resilience/CircuitBreakerFilter.java) |
| Saving a game from a web mutation | lease owned | [GameLockAndRequestContextInterceptor](src/main/java/ti4/spring/context/GameLockAndRequestContextInterceptor.java) |
| Routed HTTP traffic from Traefik | `GET /api/public/ready` returns 200 | Traefik load balancer healthcheck in the compose file |

A standby process silently ignores Discord events, so they are only handled once. An active
process that is still starting up (`ready` false) answers buttons with "the bot is rebooting".

### Process states

```mermaid
stateDiagram-v2
    [*] --> Starting: JVM start
    Starting --> Standby: lease held by another process<br/>ping 200, ready, not active
    Starting --> Active: lease free at startup
    Standby --> Active: release notification or 5 s poll<br/>claims the lease
    Active --> Standby: renew failed (lease lost)
    Active --> Draining: POST /deploy/drain<br/>or /admin disable_bot
    Draining --> Released: shutdown steps finish,<br/>lease expired + pg_notify
    Released --> [*]: JDA shut down, JVM exits
```

Log lines carry the state as a prefix: `[STANDBY xxxxxxxx]`, `[WARMING xxxxxxxx]` (active but
not ready) and `[DRAINING xxxxxxxx]`, where `xxxxxxxx` is the short instance id. The active,
ready process has no prefix.

### Startup work that waits for the lease

Some startup work must only run on the process that will own the games, so it runs from the
lease-acquired callback rather than in `main`: `CronManager.runLeaseStartJobs()`,
`GameManager.warmup()` and `DataMigrationManager.runMigrations()`. The managed game
preload, by contrast, starts right away on the standby, so the warmup after takeover can build
most games from the preload instead of from scratch.

## Known gaps

- **Interactions in the handover window go unanswered.** The old process stops handling
  Discord events the moment it starts draining, and the new one only starts once it holds the
  lease, after the old one's shutdown steps finish. Interactions in between get no reply.
- **A crashed active process blocks takeover for up to an hour.** Only a clean shutdown
  releases the lease. If the active JVM dies without running its shutdown steps, the lease
  stays valid until it expires (up to 3600 s after the last renewal). A restarted container
  gets a new `INSTANCE_ID`, so it cannot reclaim the old lease either.
- **A container that fails to become healthy in 540 s is rolled back**, even if it would
  have finished starting later. The old container keeps running, and the deploy job fails.
