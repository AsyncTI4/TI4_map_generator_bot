package ti4.helpers;

import java.util.List;
import java.util.StringTokenizer;

import com.amazonaws.util.CollectionUtils;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.generator.PlanetHelper;
import ti4.generator.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class UnitModifier {

    public void parseAndUpdateGame(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        String unitList = event.getOption(Constants.UNIT_NAMES).getAsString().toLowerCase();

        if (game.getPlayerFromColorOrFaction(color) == null && !game.getPlayerIDs().contains(Constants.dicecordId)) {
            game.setupNeutralPlayer(color);
        }

        parseAndUpdateGame(event, color, tile, unitList, game);
    }

    public static void parseAndUpdateGame(SlashCommandInteractionEvent event, String color, Tile tile, String unitList, Game game) {
        if (!Mapper.isValidColor(color)) {
            return;
        }
        if (game.getPlayerFromColorOrFaction(color) == null && !game.getPlayerIDs().contains(Constants.dicecordId)) {
            game.setupNeutralPlayer(color);
        }

        commonUnitParsing(event, color, tile, unitList, game);
    }

    private static void commonUnitParsing(SlashCommandInteractionEvent event, String color, Tile tile, String unitList, Game game) {
        unitList = unitList.replace(", ", ",").replace("-", "").replace("'", "").toLowerCase();
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

            color = recheckColorForUnit(resolvedUnit, color, event, game);

            Units.UnitKey unitID = Mapper.getUnitKey(resolvedUnit, color);
            String unitPath = TileHelper.getUnitPath(unitID, false);

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
            planetName = PlanetHelper.getPlanet(tile, planetName);

            boolean isValidCount = count > 0;
            boolean isValidUnit = unitPath != null;
            boolean isValidUnitHolder = Constants.SPACE.equals(planetName) || tile.isSpaceHolderValid(planetName);
            if (event != null && (!isValidCount || !isValidUnit || !isValidUnitHolder)) {

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
            if (event != null) {
                List<Player> playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
                if (!planetName.equalsIgnoreCase("space") && !game.isFowMode()) {
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, planetName);
                }
                numPlayersOld = playersForCombat.size();
            }
            unitAction(event, tile, count, planetName, unitID, color, game);
            if (event != null && !game.isFowMode()) {
                List<Player> playersForCombat = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
                if (!planetName.equalsIgnoreCase("space")) {
                    playersForCombat = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, planetName);
                }
                numPlayersNew = playersForCombat.size();
            }
            PlayAreaHelper.addPlanetToPlayArea(game, event, tile, planetName);
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
                        CombatHelper.startGroundCombat(player1, player2, game, event, tile.getUnitHolderFromPlanet(planetName), tile);
                    } else {
                        CombatHelper.startSpaceCombat(game, player1, player2, tile, event);
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
                if (event != null && event.getCommandString().contains("add_units")) {
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

        if (event != null && event.getCommandString().contains("add units")) {
            Player player = game.getPlayerFromColorOrFaction(color);
            if (player == null) {
                return;
            }
            ButtonHelper.checkFleetAndCapacity(player, game, tile, event);
            CommanderUnlockCheck.checkPlayer(player, "naalu", "cabal");
        }
    }

    private static String recheckColorForUnit(String unit, String color, SlashCommandInteractionEvent event, Game game) {
        if (unit.contains("ff") || unit.contains("gf")) {
            return CommandHelper.getColor(game, event);
        }
        return color;
    }

    public enum UnitModificationType {

        ADD {
            public void modify(Tile tile, int count, String planetName, Units.UnitKey unitID) {
                tile.addUnit(planetName, unitID, count);
            }
        },
        ADD_DAMAGE {
            public void modify(Tile tile, int count, String planetName, Units.UnitKey unitID) {
                tile.addUnitDamage(planetName, unitID, count);
            }
        },
        MOVE {
            public void modify(Tile tile, int count, String planetName, Units.UnitKey unitID) {

            }
        },
        REMOVE,
        REMOVE_ALL,
        REMOVE_DAMAGE;

        abstract void modify(Tile tile, int count, String planetName, Units.UnitKey unitID);
    }
}
