package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.planet.PlanetAdd;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.FoWHelper;
import ti4.map.*;
import ti4.message.MessageHelper;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;

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
        if (!Mapper.isColorValid(color)) {
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
        commonUnitParsing((GenericInteractionCreateEvent) event, color, tile, unitList, activeGame, Constants.SPACE);
        actionAfterAll((GenericInteractionCreateEvent) event, tile, color, activeGame);
    }

    public void unitParsing(GenericInteractionCreateEvent event, String color, Tile tile, String unitList, Game activeGame, String planetName) {
        commonUnitParsing(event, color, tile, unitList, activeGame, planetName);
        actionAfterAll(event, tile, color, activeGame);
    }

    public void unitParsing(SlashCommandInteractionEvent event, String color, Tile tile, String unitList, Game activeGame, String planetName) {
        commonUnitParsing((GenericInteractionCreateEvent) event, color, tile, unitList, activeGame, planetName);
        actionAfterAll((GenericInteractionCreateEvent) event, tile, color, activeGame);
    }

    public void unitParsing(GenericInteractionCreateEvent event, String color, Tile tile, String unitList, Game activeGame) {
        unitList = unitList.replace(", ", ",").replace("-", "").replace("'", "").toLowerCase();
        commonUnitParsing(event, color, tile, unitList, activeGame, Constants.SPACE);
        actionAfterAll(event, tile, color, activeGame);
    }

    protected String recheckColorForUnit(String unit, String color, GenericInteractionCreateEvent event) {
        return color;
    }

    public void commonUnitParsing(GenericInteractionCreateEvent event, String color, Tile tile, String unitList, Game activeGame, String planetName) {
        unitList = unitList.replace(", ", ",");
        StringTokenizer unitListTokenizer = new StringTokenizer(unitList, ",");
        while (unitListTokenizer.hasMoreTokens()) {
            String unitListToken = unitListTokenizer.nextToken();
            StringTokenizer unitInfoTokenizer = new StringTokenizer(unitListToken, " ");

            int tokenCount = unitInfoTokenizer.countTokens();
            if (tokenCount > 3) {
                String warning = "Warning: Unit list should have a maximum of 3 parts `{count} {unit} {planet}` - `" + unitListToken + "` has " + tokenCount + " parts. There may be errors.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), warning);
            }

            int count = 1;
            boolean numberIsSet = false;

            String unit = "";
            if (unitInfoTokenizer.hasMoreTokens()) {
                String ifNumber = unitInfoTokenizer.nextToken();
                try {
                    count = Integer.parseInt(ifNumber);
                    numberIsSet = true;
                } catch (Exception e) {
                    unit = AliasHandler.resolveUnit(ifNumber);
                }
            }
            if (unitInfoTokenizer.hasMoreTokens() && numberIsSet) {
                unit = AliasHandler.resolveUnit(unitInfoTokenizer.nextToken());
            }

            // !!!!!!
            color = recheckColorForUnit(unit, color, event);

            UnitKey unitID = Mapper.getUnitKey(unit, color);
            String unitPath = Tile.getUnitPath(unitID);
            if (unitPath == null) {
                String warning = "Unit: `" + unit + "` is not valid and not supported. Please redo this part: `" + unitListToken + "`";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), warning);
                continue;
            }
            if (unitInfoTokenizer.hasMoreTokens()) {
                String planetToken = unitInfoTokenizer.nextToken();
                planetName = AliasHandler.resolvePlanet(planetToken);
            } else {
                planetName = Constants.SPACE;
            }

            planetName = getPlanet(event, tile, planetName);
            unitAction(event, tile, count, planetName, unitID, color, activeGame);
            addPlanetToPlayArea(event, tile, planetName, activeGame);
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
                String colorMention = Helper.getColourAsMention(event.getGuild(), color);
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

    public void addPlanetToPlayArea(GenericInteractionCreateEvent event, Tile tile, String planetName, Game activeGame) {
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
        if (!tile.isSpaceHolderValid(planetName)) {
            Set<String> unitHolderIDs = new HashSet<>(tile.getUnitHolders().keySet());
            unitHolderIDs.remove(Constants.SPACE);
            String finalPlanetName = planetName;
            List<String> validUnitHolderIDs = unitHolderIDs.stream().filter(unitHolderID -> unitHolderID.startsWith(finalPlanetName))
                .toList();
            if (validUnitHolderIDs.size() == 1) {
                planetName = validUnitHolderIDs.get(0);
            } else if (validUnitHolderIDs.size() > 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Multiple planets found that match `" + planetName + "`: `" + validUnitHolderIDs + "`");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Planet `" + planetName + "` could not be resolved. Valid options for tile `" + tile.getRepresentationForAutoComplete() + "` are: `" + unitHolderIDs + "`");
            }
        }
        return planetName;
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
                .addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Unit name/s. Example: Dread, 2 Warsuns").setRequired(true))
                .addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")));
    }

    abstract protected String getActionDescription();

}
