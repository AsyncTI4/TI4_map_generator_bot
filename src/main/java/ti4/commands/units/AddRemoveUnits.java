package ti4.commands.units;

import com.amazonaws.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.Command;
import ti4.commands.combat.StartCombat;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

abstract public class AddRemoveUnits implements Command {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        }
        Game activeGame = gameManager.getUserActiveGame(userID);
        Player player = activeGame.getPlayer(userID);
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        String color = Helper.getColor(activeGame, event);
        if (!Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "Color/Faction not valid");
            return;
        }

        OptionMapping option = event.getOption(Constants.TILE_NAME);
        String tileOption = option != null ? StringUtils.substringBefore(event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString).toLowerCase(), " ") : "nombox";
        String tileID = AliasHandler.resolveTile(tileOption);
        Tile tile = getTileObject(event, tileID, activeGame);
        if (tile == null) return;

        unitParsingForTile(event, color, tile, activeGame);
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder_.getName(), activeGame);
        }

        GameSaveLoadManager.saveMap(activeGame, event);

        boolean generateMap = !event.getOption(Constants.NO_MAPGEN, false, OptionMapping::getAsBoolean);
        if (generateMap) {
            ShowGame.simpleShowGame(activeGame, event);
        } else {
            MessageHelper.replyToMessage(event, "Map update completed");
        }
    }

    public Tile getTileObject(SlashCommandInteractionEvent event, String tileID, Game activeGame) {
        return getTile(event, tileID, activeGame);
    }

    public static Tile getTile(SlashCommandInteractionEvent event, String tileID, Game activeGame) {
        if (activeGame.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return null;
        }
        Tile tile = activeGame.getTile(tileID);
        if (tile == null) {
            tile = activeGame.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Tile in map not found");
            return null;
        }
        return tile;
    }

    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game activeGame) {
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString().toLowerCase();
        unitParsing(event, color, tile, unitList, activeGame);
    }

    public void unitParsing(SlashCommandInteractionEvent event, String color, Tile tile, String unitList, Game activeGame) {
        
        commonUnitParsing(event, color, tile, unitList, activeGame);
        actionAfterAll((GenericInteractionCreateEvent) event, tile, color, activeGame);
    }


    public void unitParsing(GenericInteractionCreateEvent event, String color, Tile tile, String unitList, Game activeGame) {
        unitList = unitList.replace(", ", ",").replace("-", "").replace("'", "").toLowerCase();
        commonUnitParsing(event, color, tile, unitList, activeGame);
        actionAfterAll(event, tile, color, activeGame);
    }

    protected String recheckColorForUnit(String unit, String color, GenericInteractionCreateEvent event) {
        return color;
    }

    public void commonUnitParsing(GenericInteractionCreateEvent event, String color, Tile tile, String unitList, Game activeGame) {
        unitList = unitList.replace(", ", ",");
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");

        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");

            int count = 1;
            boolean numberIsSet = false;

            String originalUnit = "";
            String resolvedUnit;
            if (unitInfoTokenizer.hasMoreTokens()) {
                String ifNumber = unitInfoTokenizer.nextToken();
                try {
                    count = Integer.parseInt(ifNumber);
                    numberIsSet = true;
                } catch (Exception e) {
                    originalUnit = ifNumber;
                }
            }
            if (unitInfoTokenizer.hasMoreTokens() && numberIsSet) {
                originalUnit = unitInfoTokenizer.nextToken();
            }
            resolvedUnit = AliasHandler.resolveUnit(originalUnit);

            // !!!!!!
            color = recheckColorForUnit(resolvedUnit, color, event);

            UnitKey unitID = Mapper.getUnitKey(resolvedUnit, color);
            String unitPath = Tile.getUnitPath(unitID);

            // RESOLVE PLANET NAME
            String originalPlanetName = "";
            String planetName;
            if (unitInfoTokenizer.hasMoreTokens()) {
                String planetToken = unitInfoTokenizer.nextToken();
                if (unitInfoTokenizer.hasMoreTokens()) {
                    planetToken = planetToken + unitInfoTokenizer.nextToken();
                }
                originalPlanetName = planetToken;
                planetName = AliasHandler.resolvePlanet(planetToken);
            } else {
                planetName = Constants.SPACE;
            }
            planetName = getPlanet(event, tile, planetName);

            boolean isValidCount = count > 0;
            boolean isValidUnit = unitPath != null;
            boolean isValidUnitHolder = Constants.SPACE.equals(planetName) || tile.isSpaceHolderValid(planetName);
            if (event instanceof SlashCommandInteractionEvent && (!isValidCount || !isValidUnit || !isValidUnitHolder)) {

                String sb = "Could not parse this section of the command: `" + unitListToken + "`\n> " +
                    (isValidCount ? "✅" : "❌") +
                    " Count = `" + count + "`" +
                    (isValidCount ? "" : " -> Count must be a positive integer") +
                    "\n> " +
                    (isValidUnit ? "✅" : "❌") +
                    " Unit = `" + originalUnit + "`" +
                    (isValidUnit ? " -> `" + resolvedUnit + "`" : " ->  UnitID or Alias not found. Try something like: `inf, mech, dn, car, cru, des, fs, ws, sd, pds`") +
                    "\n> " +
                    (isValidUnitHolder ? "✅" : "❌") +
                    " Planet = ` " + originalPlanetName + "`" +
                    (isValidUnitHolder ? " -> `" + planetName + "`" : " -> Planets in this system are: `" + CollectionUtils.join(tile.getUnitHolders().keySet(), ", ") + "`") +
                    "\n";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
                continue;
            }
            int numPlayersOld = 0;
            int numPlayersNew = 0;
            if(event instanceof SlashCommandInteractionEvent){
                List<Player>  playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(activeGame, tile);
                if(!planetName.equalsIgnoreCase("space")&& !activeGame.isFoWMode()){
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(activeGame, tile, planetName);
                }
                numPlayersOld = playersForCombat.size();
            }
            unitAction(event, tile, count, planetName, unitID, color, activeGame);
            if(event instanceof SlashCommandInteractionEvent && !activeGame.isFoWMode()){
                List<Player>  playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(activeGame, tile);
                if(!planetName.equalsIgnoreCase("space")){
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(activeGame, tile, planetName);
                }
                numPlayersNew = playersForCombat.size();
            }
            addPlanetToPlayArea(event, tile, planetName, activeGame);
            if(numPlayersNew == 2 && numPlayersOld == 1){
                List<Player>  playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(activeGame, tile);
                String combatType = "space";
                if(!planetName.equalsIgnoreCase("space")){
                    combatType = "ground";
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(activeGame, tile, planetName);
                }

                // Try to get players in order of [activePlayer, otherPlayer, ... (discarded players)]
                Player player1 = activeGame.getActivePlayer();
                if (player1 == null) player1 = playersForCombat.get(0);
                playersForCombat.remove(player1);
                Player player2 = playersForCombat.get(0);
                StartCombat.findOrCreateCombatThread(activeGame, event.getMessageChannel(), player1, player2, tile, event, combatType);
            }
        }

        if (activeGame.isFoWMode()) {
            boolean pingedAlready = false;
            int count = 0;
            String[] tileList = activeGame.getListOfTilesPinged();
            while (count < 10 && !pingedAlready) {
                String tilePingedAlready = tileList[count];
                if (tilePingedAlready != null) {
                    pingedAlready = tilePingedAlready.equalsIgnoreCase(tile.getPosition());
                    count++;
                } else {
                    break;
                }
            }
            if (!pingedAlready) {
                String colorMention = Emojis.getColorEmojiWithName(color);
                String message = colorMention + " has modified units in the system. ";
                if (getActionDescription().contains("add_units")) {
                    message = message + " Specific units modified include: " + unitList;
                }
                message = message + "Refresh map to see what changed ";
                FoWHelper.pingSystem(activeGame, event, tile.getPosition(), message);
                if (count < 10) {
                    activeGame.setPingSystemCounter(count);
                    activeGame.setTileAsPinged(count, tile.getPosition());
                }
            }
        }

        if (getActionDescription().toLowerCase().contains("add units")) {
            Player player = activeGame.getPlayerFromColorOrFaction(color);
            if (player == null) {
                return;
            }
            ButtonHelper.checkFleetAndCapacity(player, activeGame, tile, event);
            if (player.getLeaderIDs().contains("naalucommander") && !player.hasLeaderUnlocked("naalucommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "naalu", event);
            }
            if (player.getLeaderIDs().contains("cabalcommander") && !player.hasLeaderUnlocked("cabalcommander")) {
                ButtonHelper.commanderUnlockCheck(player, activeGame, "cabal", event);
            }
        }
    }

    public static void addPlanetToPlayArea(GenericInteractionCreateEvent event, Tile tile, String planetName, Game activeGame) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        if (activeGame == null) {
            activeGame = gameManager.getUserActiveGame(userID);
        }
        // Map activeMap = mapManager.getUserActiveMap(userID);
        if (!Constants.SPACE.equals(planetName)) {
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
            if (unitHolder != null) {
                Set<UnitKey> allUnitsOnPlanet = unitHolder.getUnits().keySet();
                Set<String> unitColors = new HashSet<>();
                for (UnitKey unit_ : allUnitsOnPlanet) {
                    String unitColor = unit_.getColorID();
                    unitColors.add(unitColor);
                }

                if (unitColors.size() == 1) {
                    String unitColor = unitColors.iterator().next();
                    for (Player player : activeGame.getPlayers().values()) {
                        if (player.getFaction() != null && player.getColor() != null) {
                            String colorID = Mapper.getColorID(player.getColor());
                            if (unitColor.equals(colorID)) {
                                if (!player.getPlanetsAllianceMode().contains(planetName)) {
                                    new PlanetAdd().doAction(player, planetName, activeGame, event);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public static String getPlanet(GenericInteractionCreateEvent event, Tile tile, String planetName) {
        if (tile.isSpaceHolderValid(planetName)) return planetName;
        return tile.getUnitHolders().keySet().stream()
            .filter(id -> !Constants.SPACE.equals(planetName))
            .filter(unitHolderID -> unitHolderID.startsWith(planetName))
            .findFirst().orElse(planetName);
    }

    abstract protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game activeGame);

    abstract protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName, UnitKey unitID, String color, Game activeGame);

    protected void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color, Game activeGame) {
        //do nothing, overriden by child classes
    }

    protected void actionAfterAll(GenericInteractionCreateEvent event, Tile tile, String color, Game activeGame) {
        //do nothing, overriden by child classes
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true))
                .addOptions(
                    new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri").setRequired(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")));
    }

    abstract protected String getActionDescription();

}
