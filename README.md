# Getting Started

Hello Async TI Developers!

Please test your changes before making a PR. See below for methods and examples of how to test:

---

## 1.0 - Using Docker

### 1.1 - Windows 10, VS Code, Docker Desktop

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

Bot should now be running on the server for testing!

---

## 2.0 - Softnum's VPS

Do steps 1 & 2 from here: https://support.hostway.com/hc/en-us/articles/115001509884-How-To-Use-SSH-Keys-on-Windows-Clients-with-PuTTY-

Tell Softnum the **public** key

Load the **private** key into pageant

On vps, use these commands to try your code:
```bash
cd /opt/TI4_map_generator_bot
git status
git config --global --add safe.directory /opt/TI4_map_generator_bot
git status
git checkout (yourbranch)
./build
cd /opt/ti4bot
runbot.sh
```
Ask Softnum for invite to test Discord server.
