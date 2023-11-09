- [Setup a Test Server](#setup-a-test-server)
- [Setup a Test Bot](#setup-a-test-bot)
  - [Run Locally](#run-locally)
    - [JAVA, IntelliJ, VSCode, or other Java IDE](#java-intellij-vscode-or-other-java-ide)
  - [Run Docker Container](#run-docker-container)
    - [Windows 10, VS Code, Docker Desktop](#windows-10-vs-code-docker-desktop)
- [Adding Homebrew Content](#adding-homebrew-content)
- [Testing your Changes](#testing-your-changes)
  - [VSCode Test](#vscode-test)
  
# Setup a Test Server

1. Enable developer mode on your Discord client, if you have not already
2. Create a new Discord Server
3. Record the Server's ID (right click Server Name -> "Copy Server ID")
4. Setup a Test Bot (see instructions below)
5. Invite your Test Bot to your server

# Setup a Test Bot

See below for methods and examples of how to set up a test bot and server.
If you have a different way, please share it here!

## Run Locally

### JAVA, IntelliJ, VSCode, or other Java IDE

Ensure your launch.json file includes a configuration like this:

```json
{
    "type": "java",
    "name": "Launch MapGenerator",
    "request": "launch",
    "mainClass": "ti4.AsyncTI4DiscordBot",
    "projectName": "TI4_map_generator_discord_bot",
    "args": [
        "{DISCORD_BOT_TOKEN}", // Discord Developer Portal -> Applications -> Bot -> Token
        "{DISCORD USER ID}", //Discord User Settings -> Click 3-dot menu next to username -> "Copy USER ID"
        "{DISCORD SERVER ID}" // Right-Click Discord Server Name -> "Copy Server ID"
    ]
    ,
    "env": {
        "DB_PATH": "{FULL_PATH_TO_STORAGE_FOLDER}", // Like: "C:/user/repos/TI4_map_generator_bot/storage" - you may need to create this folder
        "RESOURCE_PATH": "{FULL_PATH_TO_RESOURCE_FOLDER}" // Like: "C:/user/repos/TI4_map_generator_bot/src/main/resources"
        }
}
```

Set the 5 {VARIABLES} to match your bot, user, server, and system.

## Run Docker Container

### Windows 10, VS Code, Docker Desktop

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

# Adding Homebrew Content

For the most part, all raw data files are [here](src\main\resources\data).

# Testing your Changes

## VSCode Test

To run Java tests in VSCode - make sure you add a test configuration your .vscode/settings.json file to pass the enviroment variables in. Paths below are just examples.

```json
"java.test.config": [
    {
        "name": "tests",
        "workingDirectory": "${workspaceFolder}",
        "env": {
            "DB_PATH": "C:/Users/USERNAME/Documents/GitHub/TI4_map_generator_bot/storage",
            "RESOURCE_PATH": "C:/Users/USERNAME/Documents/GitHub/TI4_map_generator_bot/src/main/resources"
        }
    }
]
```

