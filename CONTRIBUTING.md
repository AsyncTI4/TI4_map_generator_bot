- [Getting the Code](#getting-the-code)
- [Setup a Test Server](#setup-a-test-server)
- [Setup a Test Bot](#setup-a-test-bot)
  - [Run Locally](#run-locally)
    - [Prerequisites](#prerequisites)
    - [JAVA, IntelliJ, VSCode, or other Java IDE](#java-intellij-vscode-or-other-java-ide)
      - [Default Formatter](#default-formatter)
    - [Running from Terminal](#running-from-terminal)
  - [Run Container](#run-container)
    - [Windows 10, VS Code, Docker/Podman Desktop](#windows-10-vs-code-dockerpodman-desktop)
- [Adding New Buttons](#adding-new-buttons)
- [Adding Homebrew Content](#adding-homebrew-content)
- [Testing your Changes](#testing-your-changes)
  - [VSCode Test](#vscode-test)
- [Helpful Tips for Debugging](#helpful-tips-for-debugging)

# Getting the Code
Get in touch with us at the TI4-Async discord and let us know you'd like the developer role.
Branch permissions can be granted, but for now you may fork the repository and clone it to a suitable location.

# Setup a Test Server

1. Enable developer mode on your Discord client, if you have not already
2. Create a new Discord Server
3. Record the Server's ID (right click Server Name -> "Copy Server ID")
4. Setup a Test Bot - see [Step 1 from here](https://discord.com/developers/docs/getting-started#step-1-creating-an-app). The main steps are:
* Note down the bot's credentials.
* Enable "Privileged Gateway Intents".
* Tick both Installation Contexts' Methods.
* Ensure Default Install Settings are correct.
* Create an install link, paste it into the discord server you want your bot in.
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

The first time you attempt to Run/Debug in VSCode, it will ask you if you want it to create a launch configuration. Say yes, it should appear in the .vscode folder. Within the configurations section, add the "args" and "env" sections shown below. For VSCode, the file should look very similar to this:

```json
{
    "configurations": [
        {
            "type": "java",
            "name": "AsyncTI4DiscordBot",
            "request": "launch",
            "mainClass": "ti4.AsyncTI4DiscordBot",
            "projectName": "TI4_map_generator_discord_bot",
            ////----vvvvvv COPY THIS INTO YOUR LAUNCH.JSON
            "args": [
              "{DISCORD_BOT_TOKEN}", // Discord Developer Portal -> Applications -> Bot -> Token
              "{DISCORD USER ID}", // Discord User Settings -> Click 3-dot menu next to username -> "Copy USER ID"
              "{DISCORD SERVER ID}" // Right-Click Discord Server Name -> "Copy Server ID"
            ],
            "env": {
                "DB_PATH": "${workspaceFolder}/storage", // Like: "C:/user/repos/TI4_map_generator_bot/storage" - you may need to create this folder
                "RESOURCE_PATH": "${workspaceFolder}/src/main/resources" // Like: "C:/user/repos/TI4_map_generator_bot/src/main/resources"
            }
            ////----^^^^^^ COPY THIS INTO YOUR LAUNCH.JSON
        }
    ]
}
```
For vscode, this is in `.vscode/launch.json`.

Set the 5 {VARIABLES} to match your Discord App/Bot's Token, UserID, ServerID, and system.

#### Default Formatter

Ensure your default formatter is set to use the `eclipse-formatter.xml` configuration file.

For VSCode you can set it within User/Workspace settings:

![image](https://github.com/AsyncTI4/TI4_map_generator_bot/assets/39609802/9a86b828-f16a-49c9-b223-af9624bd9ffe)

In VSCode, to check your current formatter, you can use prompt `Java: Open Java Formatter Settings` and it should open the `eclipse-formatter.xml` file if set correctly.

![image](https://github.com/AsyncTI4/TI4_map_generator_bot/assets/39609802/f036b3ff-a1b1-40ba-8d64-e3fed493ae76)

### Running from Terminal

NB: This was done using Linux Ubuntu.

Ensure the pre-requisites are met, you have cloned the GitHub code in a suitable place, and you've set your Bot App up in your discord server.
[You may use](https://www.baeldung.com/linux/java-choose-default-version) `update-alternatives --config java` to ensure that Java is using the same SDK that you installed earlier.
Next is to install [Maven](https://maven.apache.org/what-is-maven.html), which will create a JAR file for us to compile and run the bot.
Ensure it is the latest version and you add the installed binary to your `$PATH`, which is covered by this [StackOverflow answer](https://stackoverflow.com/questions/67985216/install-latest-maven-package-in-ubuntu-via-apt-package-manager-or-other-ways).
Now, at the root of your project:
```
mvn clean install
```
This will build and package everything into a JAR file for you, watch the output!
You will be told about where the JAR file has been placed.
For example:
```
[INFO] Installing <PROJECT_DIR>/TI4_map_generator_bot/target/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar to /home/<USERNAME>/.m2/repository/me/terterro/TI4_map_generator_discord_bot/1.0-SNAPSHOT/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar
```
Note down the directory and file name of the jar with dependencies.

To run the bot, we now need to run `jar-with-dependencies.jar` with `java` and provide it with 3 arguments:
* `discordBotKey`: Your bot's API token (never reveal this to anyone).
* `discordUserID`: The User ID of the bot that's in your server.
* `discordServerID`: The ID of the server your bot is in.
It would be tedious to do this every time, so I created a bash script to automate this for me, `run_locally.sh` in the same directory as the TI4 bot.
This bash script should also provide _environment variables_ that tell Java where resources such as emojis or images are and where our local database should be.

Similarly to the docker script described below, you could adapt this to be a PowerShell script.
Do not include the angle braces:
```bash
#!/bin/bash
export DB_PATH="./storage"
export RESOURCE_PATH="./src/main/resources"
jar_with_deps="<full_file_path_of_jar_with_dependencies.jar>"
discordBotKey="<BOT API KEY HERE>"
discordUserID="<BOT USER ID HERE>"
discordServerID="<SERVER ID HERE>"

java -jar $jar_with_deps $discordBotKey $discordUserID $discordServerID
```
If using bash, make it executable: `chmod +x run_locally.sh`.
Now run it: `./run_locally.sh`, you should see your `bot-log` channel fill up with some logs.
Your bot is now running!

To enable debugging, the final line should be changed to:
```bash
java -jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005 $jar_with_deps $discordBotKey $discordUserID $discordServerID
```
Which will wait (`supsend=y`) on the java debugger, `jdb`, to run on port 5005.
In a seperate terminal, run:
```
jdb -attach localhost:5005
```

On Windows, you'll need Powershell, and you'll create a file called run_locally.ps1. Your file should look something like this. 

```bash
$env:DB_PATH="./storage"
$env:RESOURCE_PATH="./src/main/resources"
$jar_with_deps="<YOUR USER PATH?>/.m2/repository/me/terterro/TI4_map_generator_discord_bot/1.0-SNAPSHOT/TI4_map_generator_discord_bot-1.0-SNAPSHOT-jar-with-dependencies.jar"
$discordBotKey="YOUR BOT KEY HERE"
$discordUserID="YOUR USER ID HERE"
$discordServerID="YOUR SERVER ID HERE"

java -jar $jar_with_deps $discordBotKey $discordUserID $discordServerID
```

## Run Container

### Windows 10, VS Code, Docker/Podman Desktop

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

You can also use Podman instead of Docker in which case replace previous docker commands with
```powershell
$exitedContainers = podman ps --filter status=exited -q
if ($exitedContainers) {
    podman rm $exitedContainers
}

$danglingImages = podman images --filter "dangling=true" -q
if ($danglingImages) {
    podman rmi $danglingImages
}

podman build -t tibot .
podman run -v ${PWD}/storage:/opt/STORAGE tibot $discordBotKey $discordUserID $discordServerID
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
