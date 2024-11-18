package ti4.commands.units;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import com.amazonaws.util.CollectionUtils;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.Command;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.planet.PlanetAdd;
import ti4.commands2.CommandHelper;
import ti4.commands2.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;

abstract public class AddRemoveUnits implements Command {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        if (!GameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        }
        Game game = GameManager.getUserActiveGame(userID);
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        String color = CommandHelper.getColor(game, event);
        if (!Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "Color/Faction not valid");
            return;
        }

        OptionMapping option = event.getOption(Constants.TILE_NAME);
        String tileOption = option != null
            ? StringUtils.substringBefore(
                event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString).toLowerCase(), " ")
            : "nombox";
        String tileID = AliasHandler.resolveTile(tileOption);
        Tile tile = getTileObject(event, tileID, game);
        if (tile == null)
            return;

        unitParsingForTile(event, color, tile, game);
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            addPlanetToPlayArea(event, tile, unitHolder_.getName(), game);
        }
        new AddUnits().actionAfterAll(event, tile, color, game);

        GameSaveLoadManager.saveGame(game, event);

        boolean generateMap = !event.getOption(Constants.NO_MAPGEN, false, OptionMapping::getAsBoolean);
        if (generateMap) {
            ShowGame.simpleShowGame(game, event);
        } else {
            MessageHelper.replyToMessage(event, "Map update completed");
        }
    }

    public Tile getTileObject(SlashCommandInteractionEvent event, String tileID, Game game) {
        return TileHelper.getTile(event, tileID, game);
    }

    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString().toLowerCase();

        if (game.getPlayerFromColorOrFaction(color) == null && !game.getPlayerIDs().contains(Constants.dicecordId)) {
            game.setupNeutralPlayer(color);
        }

        unitParsing(event, color, tile, unitList, game);
    }

    public void unitParsing(SlashCommandInteractionEvent event, String color, Tile tile, String unitList, Game game) {

        if (game.getPlayerFromColorOrFaction(color) == null && !game.getPlayerIDs().contains(Constants.dicecordId)) {
            game.setupNeutralPlayer(color);
        }

        commonUnitParsing(event, color, tile, unitList, game);
        actionAfterAll((GenericInteractionCreateEvent) event, tile, color, game);
    }

    public void unitParsing(GenericInteractionCreateEvent event, String color, Tile tile, String unitList, Game game) {
        unitList = unitList.replace(", ", ",").replace("-", "").replace("'", "").toLowerCase();
        if (!Mapper.isValidColor(color)) {
            return;
        }
        if (game.getPlayerFromColorOrFaction(color) == null && !game.getPlayerIDs().contains(Constants.dicecordId)) {
            game.setupNeutralPlayer(color);
        }

        commonUnitParsing(event, color, tile, unitList, game);
        actionAfterAll(event, tile, color, game);
    }

    protected String recheckColorForUnit(String unit, String color, GenericInteractionCreateEvent event) {
        return color;
    }

    public void commonUnitParsing(GenericInteractionCreateEvent event, String color, Tile tile, String unitList,
        Game game) {
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
            boolean isValidUnit = unitID != null;
            boolean isValidUnitHolder = Constants.SPACE.equals(planetName) || tile.isSpaceHolderValid(planetName);
            if (event instanceof SlashCommandInteractionEvent
                && (!isValidCount || !isValidUnit || !isValidUnitHolder)) {

                String sb = "Could not parse this section of the command: `" + unitListToken + "`\n> " +
                    (isValidCount ? "✅" : "❌") +
                    " Count = `" + count + "`" +
                    (isValidCount ? "" : " -> Count must be a positive integer") +
                    "\n> " +
                    (isValidUnit ? "✅" : "❌") +
                    " Unit = `" + originalUnit + "`" +
                    (isValidUnit ? " -> `" + resolvedUnit + "`"
                        : " ->  UnitID or Alias not found. Try something like: `inf, mech, dn, car, cru, des, fs, ws, sd, pds`")
                    +
                    "\n> " +
                    (isValidUnitHolder ? "✅" : "❌") +
                    " Planet = ` " + originalPlanetName + "`" +
                    (isValidUnitHolder ? " -> `" + planetName + "`"
                        : " -> Planets in this system are: `"
                            + CollectionUtils.join(tile.getUnitHolders().keySet(), ", ") + "`")
                    +
                    "\n";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
                continue;
            }
            int numPlayersOld = 0;
            int numPlayersNew = 0;
            if (event instanceof SlashCommandInteractionEvent) {
                List<Player> playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
                if (!planetName.equalsIgnoreCase("space") && !game.isFowMode()) {
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, planetName);
                }
                numPlayersOld = playersForCombat.size();
            }
            unitAction(event, tile, count, planetName, unitID, color, game);
            if (event instanceof SlashCommandInteractionEvent && !game.isFowMode()) {
                List<Player> playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
                if (!planetName.equalsIgnoreCase("space")) {
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, planetName);
                }
                numPlayersNew = playersForCombat.size();
            }
            addPlanetToPlayArea(event, tile, planetName, game);
            if (numPlayersNew > numPlayersOld && numPlayersOld != 0) {
                List<Player> playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
                String combatType = "space";
                if (!planetName.equalsIgnoreCase("space")) {
                    combatType = "ground";
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, planetName);
                }

                // Try to get players in order of [activePlayer, otherPlayer, ... (discarded
                // players)]
                Player player1 = game.getActivePlayer();
                if (player1 == null)
                    player1 = playersForCombat.getFirst();
                playersForCombat.remove(player1);
                Player player2 = player1;
                for (Player p2 : playersForCombat) {
                    if (p2 != player1 && !player1.getAllianceMembers().contains(p2.getFaction())) {
                        player2 = p2;
                        break;
                    }
                }
                if (player1 != player2 && !tile.getPosition().equalsIgnoreCase("nombox") && !player1.getAllianceMembers().contains(player2.getFaction())) {
                    if ("ground".equals(combatType)) {
                        StartCombatService.startGroundCombat(player1, player2, game, event, tile.getUnitHolderFromPlanet(planetName), tile);
                    } else {
                        StartCombatService.startSpaceCombat(game, player1, player2, tile, event);
                    }
                }
            }
        }

        if (game.isFowMode()) {
            boolean pingedAlready = false;
            int countF = 0;
            String[] tileList = game.getListOfTilesPinged();
            while (countF < 10 && !pingedAlready) {
                String tilePingedAlready = tileList[countF];
                if (tilePingedAlready != null) {
                    pingedAlready = tilePingedAlready.equalsIgnoreCase(tile.getPosition());
                    countF++;
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
                FoWHelper.pingSystem(game, event, tile.getPosition(), message);
                if (countF < 10) {
                    game.setPingSystemCounter(countF);
                    game.setTileAsPinged(countF, tile.getPosition());
                }
            }
        }

        if (getActionDescription().toLowerCase().contains("add units")) {
            Player player = game.getPlayerFromColorOrFaction(color);
            if (player == null) {
                return;
            }
            ButtonHelper.checkFleetAndCapacity(player, game, tile, event);
            CommanderUnlockCheck.checkPlayer(player, "naalu", "cabal");
        }
    }

    public static void addPlanetToPlayArea(GenericInteractionCreateEvent event, Tile tile, String planetName, Game game) {
        String userID = event.getUser().getId();
        if (game == null) {
            game = GameManager.getUserActiveGame(userID);
        }
        // Map activeMap = mapManager.getUserActiveMap(userID);
        if (!Constants.SPACE.equals(planetName)) {
            UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
            if (unitHolder != null) {
                Set<UnitKey> allUnitsOnPlanet = unitHolder.getUnits().keySet();
                Set<String> unitColors = new HashSet<>();
                for (UnitKey unit_ : allUnitsOnPlanet) {
                    String unitColor = unit_.getColorID();
                    if (unit_.getUnitType() != UnitType.Fighter) {
                        unitColors.add(unitColor);
                    }
                }

                if (unitColors.size() == 1) {
                    String unitColor = unitColors.iterator().next();
                    for (Player player : game.getPlayers().values()) {
                        if (player.getFaction() != null && player.getColor() != null) {
                            String colorID = Mapper.getColorID(player.getColor());
                            if (unitColor.equals(colorID)) {
                                if (!player.getPlanetsAllianceMode().contains(planetName)) {
                                    PlanetAdd.doAction(player, planetName, game, event, false);
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
        if (tile.isSpaceHolderValid(planetName))
            return planetName;
        return tile.getUnitHolders().keySet().stream()
            .filter(id -> !Constants.SPACE.equals(planetName))
            .filter(unitHolderID -> unitHolderID.startsWith(planetName))
            .findFirst().orElse(planetName);
    }

    abstract protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName,
        UnitKey unitID, String color, Game game);

    abstract protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName,
        UnitKey unitID, String color, Game game);

    protected void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color, Game game) {
        // do nothing, overriden by child classes
    }

    protected void actionAfterAll(GenericInteractionCreateEvent event, Tile tile, String color, Game game) {
        // do nothing, overriden by child classes
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void register(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getName(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                    .setRequired(true).setAutoComplete(true))
                .addOptions(
                    new OptionData(OptionType.STRING, Constants.UNIT_NAMES,
                        "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri")
                            .setRequired(true))
                .addOptions(
                    new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                        .setAutoComplete(true))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN,
                    "'True' to not generate a map update with this command")));
    }

    abstract protected String getActionDescription();

}
