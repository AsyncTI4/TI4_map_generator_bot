# Getting Started

Hello Async TI Developers!

Please test your changes before making a PR.
See below for methods and examples of how to test.
If you have a different way, please share!

## 1.0 - Locally

### 1.1 - Windows 10, VS Code

Ensure your launch.json file includes a configuration like this:

```json
{
    "type": "java",
    "name": "Launch MapGenerator",
    "request": "launch",
    "mainClass": "ti4.MapGenerator",
    "projectName": "TI4_map_generator_discord_bot",
    "args": [
        "{DISCORD_BOT_KEY}", // Discord Developer Portal
        "{DISCORD USER ID}", //User Settings, 3 Hash marks next to username, Copy ID
        "{DISCORD SERVER ID}" // Right-Click Discord Server Name and Copy ID
    ]
    ,
    "env": {
        "DB_PATH": "C:/{FULL_PATH_TO_PROJECT}/TI4_map_generator_bot/storage",
        "RESOURCE_PATH": "C:/{FULL_PATH_TO_PROJECT}/TI4_map_generator_bot/src/main/resources"
        }
}
```

Set the 5 {variables} to match your bot, user, server, and system.

## 2.0 - Using Docker

### 2.1 - Windows 10, VS Code, Docker Desktop

Run the following commands in the root project folder: `.\TI4_map_generator_bot`

```powershell
docker build -t tibot .
docker run -v ${PWD}/storage:/opt/STORAGE tibot $discordBotKey $discordUserID $discordServerID
```

where:

- `$discordBotKey` = "botkey" # Discord Developer Portal
- `$discordUserID` = "12345" # User Settings, 3 Hash marks next to username, Copy ID
- `$discordServerID` = "12345" # Right-Click Discord Server Name and Copy ID

You can create a `docker_run.ps1` file in `.\TI4_map_generator_bot` to do cleanup, build, and run with PowerShell

```powershell
$discordBotKey = "" # Discord Developer Portal
$discordUserID = "" # User Settings, 3 Hash marks next to username, Copy ID
$discordServerID = "" # Right-Click Discord Server Name and Copy ID

docker rm $(docker ps --filter status=exited -q)
docker rmi $(docker images --filter "dangling=true" -q)
docker build -t tibot .
docker run -v ${PWD}/storage:/opt/STORAGE tibot $discordBotKey $discordUserID $discordServerID
```

Bot should now be running and able to receive commands on your test server!

