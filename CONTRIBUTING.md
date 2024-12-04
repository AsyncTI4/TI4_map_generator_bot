- [Setup a Test Server](#setup-a-test-server)
- [Setup a Test Bot](#setup-a-test-bot)
  - [Run Locally](#run-locally)
    - [JAVA, IntelliJ, VSCode, or other Java IDE](#java-intellij-vscode-or-other-java-ide)
    - [Default Formatter](#default-formatter)
  - [Run Docker Container](#run-docker-container)
    - [Windows 10, VS Code, Docker Desktop](#windows-10-vs-code-docker-desktop)
- [Adding New Buttons](#adding-new-buttons)
- [Adding Homebrew Content](#adding-homebrew-content)
- [Testing your Changes](#testing-your-changes)
  - [VSCode Test](#vscode-test)
- [Helpful Tips for Debugging](#helpful-tips-for-debugging)
  
# Setup a Test Server

1. Enable developer mode on your Discord client, if you have not already
2. Create a new Discord Server
3. Record the Server's ID (right click Server Name -> "Copy Server ID")
4. Setup a Test Bot - see [Step 1 from here](https://discord.com/developers/docs/getting-started#step-1-creating-an-app)
5. Invite your Test Bot to your server
6. Create a `bot-log` channel, and `Admin`, `Developer` and `Bothelper` roles; add the role IDs to `src/main/java/ti4/AsyncTI4DiscordBot.java`

# Setup a Test Bot

See below for methods and examples of how to set up a test bot and server.
If you have a different way, please share it here!

## Run Locally

### Prerequisites
Download the latest JDK: https://adoptium.net/

Setup vscode with the default extensions for java: https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack

ctrl+shift+p: Java: Configure Java Runtime -> JavaSE-21

Create a discord server for testing
Create a discord application: https://discord.com/developers/applications
For the bot settings, go to "Guild Install" scopes -> bot
For permissions, select "Administrator"

Go to installation -> copy install link and open in your browser
Add the bot to your server

### JAVA, IntelliJ, VSCode, or other Java IDE

Ensure your launch.json file includes a configuration like this:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "AsyncTI4DiscordBot",
            "request": "launch",
            "mainClass": "ti4.AsyncTI4DiscordBot",
            "projectName": "TI4_map_generator_discord_bot",
            "args": [
              "{DISCORD_BOT_TOKEN}", // Discord Developer Portal -> Applications -> Bot -> Token
              "{DISCORD USER ID}", //Discord User Settings -> Click 3-dot menu next to username -> "Copy USER ID"
              "{DISCORD SERVER ID}" // Right-Click Discord Server Name -> "Copy Server ID"
            ],
            "env": {
                "DB_PATH": "${workspaceFolder}/storage", // Like: "C:/user/repos/TI4_map_generator_bot/storage" - you may need to create this folder
                "RESOURCE_PATH": "${workspaceFolder}/src/main/resources" // Like: "C:/user/repos/TI4_map_generator_bot/src/main/resources"
            },
        }
    ]
}
```
For vscode, this is in `.vscode/launch.json`.

Set the 5 {VARIABLES} to match your bot, user, server, and system.

### Default Formatter

Ensure your default formatter is set to use the `eclipse-formatter.xml` configuration file.

For VSCode you can set it within User/Workspace settings:

![image](https://github.com/AsyncTI4/TI4_map_generator_bot/assets/39609802/9a86b828-f16a-49c9-b223-af9624bd9ffe)

In VSCode, to check your current formatter, you can use prompt `Java: Open Java Formatter Settings` and it should open the `eclipse-formatter.xml` file if set correctly.

![image](https://github.com/AsyncTI4/TI4_map_generator_bot/assets/39609802/f036b3ff-a1b1-40ba-8d64-e3fed493ae76)

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

# Adding New Buttons

Don't add anything to ButtonListener.java! The if/elseif startsWith chain and select case method are deprecated. Use the @ButtonHandler annotation on your resolver method, ideally nearby where you sent/created the button!

# Adding Homebrew Content

For the most part, all raw data files are in `TI4_map_generator_bot/src/main/resources/data/`

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

# Helpful Tips for Debugging

- You can use `/game swap` to switch seats with another player (a bot) in the game
- You can use `/bothelper list_buttons` to find the buttonIDs in that message, which you can use to search the repo for the code that created that button
- You can spoof a button with a specific buttonID with `/button spoof_id:{spoofedID}`
