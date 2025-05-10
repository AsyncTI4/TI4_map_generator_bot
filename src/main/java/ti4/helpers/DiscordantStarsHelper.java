package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.GenericCardModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class DiscordantStarsHelper {

    public static void checkGardenWorlds(Game game) {
        Player player = Helper.getPlayerFromAbility(game, "garden_worlds");
        if (player == null) {
            return;
        }

        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder instanceof Planet planet) {
                    if (player.getPlanets().contains(planet.getName())) {
                        if (planet.hasGroundForces(game) && planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                            planet.removeToken(Constants.GARDEN_WORLDS_PNG);
                        } else if (!planet.hasGroundForces(game)) {
                            planet.addToken(Constants.GARDEN_WORLDS_PNG);
                        }
                    } else if (planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                        planet.removeToken(Constants.GARDEN_WORLDS_PNG);
                    }
                }
            }
        }

    }

    public static void checkSigil(Game game) { //Edyn Mech adds Sigil tokens under them
        Player player = Helper.getPlayerFromUnit(game, "edyn_mech");
        if (player == null) {
            return;
        }

        for (Tile tile : game.getTileMap().values()) {
            if (player.hasMechInSystem(tile)) {
                tile.addToken(Constants.SIGIL, Constants.SPACE);
            } else {
                tile.removeToken(Constants.SIGIL, Constants.SPACE);
            }
        }

    }

    public static void checkOlradinMech(Game activeMap) {
        for (Player player : activeMap.getPlayers().values()) {
            String tokenToAdd;
            String tokenToRemove;
            if (player.ownsUnit("olradin_mech_positive")) {
                tokenToAdd = Constants.OLRADIN_MECH_INF_PNG;
                tokenToRemove = Constants.OLRADIN_MECH_RES_PNG;
            } else if (player.ownsUnit("olradin_mech_negative")) {
                tokenToAdd = Constants.OLRADIN_MECH_RES_PNG;
                tokenToRemove = Constants.OLRADIN_MECH_INF_PNG;
            } else {
                continue;
            }

            for (Tile tile : activeMap.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder instanceof Planet planet) {
                        if (player.getPlanets().contains(planet.getName())) {
                            if (!oneMechCheck(planet.getName(), activeMap, player)
                                && ((planet.getTokenList().contains(Constants.OLRADIN_MECH_INF_PNG)) || (planet.getTokenList().contains(Constants.OLRADIN_MECH_RES_PNG)))) {
                                planet.removeToken(Constants.OLRADIN_MECH_INF_PNG);
                                planet.removeToken(Constants.OLRADIN_MECH_RES_PNG);
                            } else if (oneMechCheck(planet.getName(), activeMap, player)) {
                                planet.addToken(tokenToAdd);
                                planet.removeToken(tokenToRemove);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean oneMechCheck(String planetName, Game activeMap, Player player) {
        Tile tile = activeMap.getTile(AliasHandler.resolveTile(planetName));
        if (tile == null) return false;
        UnitHolder unitHolder = tile.getUnitHolders().get(planetName);
        int numMechs = 0;

        String colorID = Mapper.getColorID(player.getColor());
        if (unitHolder.getUnits() != null) {
            numMechs = unitHolder.getUnitCount(UnitType.Mech, colorID);
        }
        return numMechs == 1;
    }

    public static void handleOlradinPoliciesWhenExhaustingPlanets(Game game, Player player, String planet) {
        if (game == null || !"action".equalsIgnoreCase(game.getPhaseOfGame()) || player == null || !player.hasOlradinPolicies()) return;
        PlanetModel planetModel = Mapper.getPlanet(planet);
        if (planetModel == null) return;
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        Planet planetHolder = (Planet) unitHolder;
        if (planetHolder == null)
            return;

        boolean hasAbility = planetHolder.isLegendary();
        if (hasAbility) {
            resolveEnvironmentPreserveAbility(player, planetModel, game);
            resolveEconomyEmpowerAbility(player, planetModel);
            for (String type : ButtonHelper.getTypeOfPlanet(game, planet)) {
                switch (type) {
                    case "industrial" -> {
                        resolveEconomyExploitAbility(player, planetModel, game);
                    }
                }
            }
            resolvePeopleConnectAbility(player, planetModel, game);
            return;
        }

        for (String type : ButtonHelper.getTypeOfPlanet(game, planet)) {
            switch (type) {
                case "hazardous" -> resolveEnvironmentPreserveAbility(player, planetModel, game);
                case "industrial" -> {
                    resolveEconomyEmpowerAbility(player, planetModel);
                    resolveEconomyExploitAbility(player, planetModel, game);
                }
                case "cultural" -> resolvePeopleConnectAbility(player, planetModel, game);
                default -> {
                    return;
                }
            }
        }

    }

    private static void resolveEconomyExploitAbility(Player player, PlanetModel planetModel, Game game) {
        if (!player.isHasUsedEconomyExploitAbility() && player.hasAbility("policy_the_economy_exploit")) { //add a fighter with ships
            player.setHasUsedEconomyExploitAbility(true);
            String msg = player.getRepresentation() + " Due to your exhausting of " + planetModel.getAutoCompleteName()
                + " you may resolve your _Policy - The Economy: Exploit ➖_ ability."
                + " You may place 1 fighter from your reinforcements in a system that contains 1 or more of your ships.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            List<Button> buttons = new ArrayList<>(Helper.getTileWithShipsPlaceUnitButtons(player, game, "ff", "placeOneNDone_skipbuild"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Resolve ability", buttons);
        }
    }

    private static void resolvePeopleConnectAbility(Player player, PlanetModel planetModel, Game game) {
        UnitHolder uh = ButtonHelper.getUnitHolderFromPlanetName(planetModel.getId(), game);

        if (!player.isHasUsedPeopleConnectAbility() && player.hasAbility("policy_the_people_connect") && uh != null && uh.getUnitCount(UnitType.Infantry, player.getColor()) > 0) {
            String msg = player.getRepresentation() + ", due to your exhausting of " + planetModel.getAutoCompleteName()
                + ", you may resolve your _Policy - The People: Connect ➕_ ability."
                + " You may move 1 iInfantry on " + planetModel.getName() + " to another planet you control.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            List<Button> buttons = ButtonHelperAbilities.offerOlradinConnectButtons(player, game, planetModel.getId());
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Resolve Ability", buttons);
        }
    }

    private static void resolveEconomyEmpowerAbility(Player player, PlanetModel planetModel) {
        if (!player.isHasUsedEconomyEmpowerAbility() && player.hasAbility("policy_the_economy_empower")) {
            player.setHasUsedEconomyEmpowerAbility(true);
            String msg = player.getRepresentation() + ", due to your exhausting of " + planetModel.getAutoCompleteName()
                + ", you may resolve your _Policy - The Economy: Empower ➕_ ability. You gain 1 commodity.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            Button getCommButton = Buttons.blue("gain_1_comms", "Gain 1 Commodity", MiscEmojis.comm);
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), "Resolve Ability.", getCommButton);
        }
    }

    private static void resolveEnvironmentPreserveAbility(Player player, PlanetModel planetModel, Game game) {
        if (!player.isHasUsedEnvironmentPreserveAbility() && player.hasAbility("policy_the_environment_preserve")) {
            List<Button> buttons = ButtonHelperAbilities.getOlradinPreserveButtons(game, player, planetModel.getId());
            if (!buttons.isEmpty()) {
                String msg = player.getRepresentation() + ", due to your exhausting of " + planetModel.getAutoCompleteName()
                    + ", you may resolve your _Policy - The Environment: Preserve ➕_ ability."
                    + " You may reveal the top card of the planets types exploration deck; if it is a relic fragment, gain it, otherwise discard that card.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Resolve ability", buttons);
            }
        }
    }

    public static void drawRedBackTiles(GenericInteractionCreateEvent event, Game game, Player player, int count) {
        List<String> tilesToPullFrom = new ArrayList<>(List.of(
            //Source:  https://discord.com/channels/943410040369479690/1009507056606249020/1140518249088434217
            //         https://cdn.discordapp.com/attachments/1009507056606249020/1140518248794820628/Starmap_Roll_Helper.xlsx

            "39",
            "40",
            "41",
            "42",
            "43",
            "44",
            "45",
            "46",
            "47",
            "48",
            "49",
            "67",
            "68",
            "77",
            "78",
            "79",
            "80",
            "d117",
            "d118",
            "d119",
            "d120",
            "d121",
            "d122",
            "d123"));

        // if (includeAllTiles) tilesToPullFrom = TileHelper.getAllTiles().values().stream().filter(tile -> !tile.isAnomaly() && !tile.isHomeSystem() && !tile.isHyperlane()).map(TileModel::getId).toList();
        tilesToPullFrom.removeAll(game.getTileMap().values().stream().map(Tile::getTileID).toList());
        if (!game.isDiscordantStarsMode()) {
            tilesToPullFrom.removeAll(tilesToPullFrom.stream().filter(tileID -> tileID.contains("d")).toList());
        }
        List<String> tileToPullFromUnshuffled = new ArrayList<>(tilesToPullFrom);
        Collections.shuffle(tilesToPullFrom);

        if (tilesToPullFrom.size() < count) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Not enough tiles to draw from");
            return;
        }

        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String tileID = tilesToPullFrom.get(i);
            ids.add(tileID);
            TileModel tile = TileHelper.getTileById(tileID);
            tileEmbeds.add(tile.getRepresentationEmbed(false));
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " drew " + count + " red back tiles from this list:\n> " + tileToPullFromUnshuffled);

        event.getMessageChannel().sendMessageEmbeds(tileEmbeds).queue();
        if (ids.size() == 1) {
            ButtonHelper.detTileAdditionStep1(player, ids.getFirst());
        }
    }

    public static void drawBlueBackTiles(GenericInteractionCreateEvent event, Game game, Player player, int count) {
        List<MiltyDraftTile> unusedBlueTiles = new ArrayList<>(Helper.getUnusedTiles(game).stream()
            .filter(tile -> tile.getTierList().isBlue())
            .toList());

        List<MiltyDraftTile> tileToPullFromUnshuffled = new ArrayList<>(unusedBlueTiles);
        Collections.shuffle(unusedBlueTiles);

        if (unusedBlueTiles.size() < count) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Not enough tiles to draw from");
            return;
        }

        List<MessageEmbed> tileEmbeds = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Tile tile = unusedBlueTiles.get(i).getTile();
            TileModel tileModel = tile.getTileModel();
            tileEmbeds.add(tileModel.getRepresentationEmbed(false));
            ids.add(tile.getTileID());
        }
        String tileString = String.join(",", tileToPullFromUnshuffled.stream().map(t -> t.getTile().getTileID()).toList());
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " drew " + count + " blue back tiles from this list:\n> " + tileString);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Use `/map add_tile` to add it to the map.");

        event.getMessageChannel().sendMessageEmbeds(tileEmbeds).queue();
        if (ids.size() == 1) { 
            if (game.isDiscordantStarsMode()) {
                ButtonHelper.starChartStep1(game, player, ids.getFirst());
            } else {
                ButtonHelper.detTileAdditionStep1(player, ids.getFirst());
            }
        } else {
            ButtonHelper.starChartStep0(player, ids);
        }
    }

    public static void setTrapForPlanet(GenericInteractionCreateEvent event, Game game, String planetName, String trap, Player player) {
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
        String planetUnitHolderName = trapCardsPlanets.get(trap);
        if (planetUnitHolderName != null) {
            MessageHelper.replyToMessage(event, "Trap used on other planet, please use trap swap or remove first");
            return;
        }
        ButtonHelperAbilities.addATrapToken(game, planetName);
        player.setTrapCardPlanet(trap, unitHolder.getName());
        player.setTrapCard(trap);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() + " put a trap on the planet " + Helper.getPlanetRepresentation(planetName, game));
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentationUnfogged() + " set the trap " + ButtonHelperAbilities.translateNameIntoTrapIDOrReverse(trap) + " on the planet " + Helper.getPlanetRepresentation(planetName, game));
        CommanderUnlockCheckService.checkPlayer(player, "lizho");
    }

    public static void revealTrapForPlanet(GenericInteractionCreateEvent event, Game game, String planetName, String trap, Player player, boolean reveal) {
        if (!player.getTrapCardsPlanets().containsValue(planetName) && planetName != null) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " could not find a trap for the planet " + Helper.getPlanetRepresentation(planetName, game));
            return;
        }
        Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
        for (Map.Entry<String, String> entry : trapCardsPlanets.entrySet()) {
            String planet = entry.getValue();
            if (planetName.equals(planet) || planet == null) {
                ButtonHelperAbilities.removeATrapToken(game, planetName);
                player.removeTrapCardPlanet(trap);
                player.setTrapCard(trap);
                GenericCardModel trapCard = Mapper.getTrap(trap);
                Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
                String representation = planetRepresentations.get(planet);
                if (representation == null) {
                    representation = planet;
                }
                if (reveal && planet != null) {

                    String sb = trapCard.getRepresentation() + "\n" + "__**" + "Has been revealed on planet: " + representation + "**__";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
                    if ("Minefields".equalsIgnoreCase(trapCard.getName())) {
                        for (Player p2 : game.getRealPlayers()) {
                            if (p2 == player) {
                                continue;
                            }
                            RemoveUnitService.removeUnits(event, game.getTileFromPlanet(planet), game, p2.getColor(), "2 inf " + planet);
                        }
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Destroyed up to 2 enemy infantry from " + representation);
                    }
                    if ("Account Siphon".equalsIgnoreCase(trapCard.getName())) {
                        for (Player p2 : game.getRealPlayers()) {
                            if (p2 == player) {
                                continue;
                            }
                            if (p2.getPlanets().contains(planet)) {
                                List<Button> buttons = new ArrayList<>();
                                buttons.add(Buttons.green("steal2tg_" + p2.getFaction(), "Steal 2 Trade Goods From " + p2.getFactionEmojiOrColor()));
                                buttons.add(Buttons.blue("steal3comm_" + p2.getFaction(), "Steal 3 Commodities From " + p2.getFactionEmojiOrColor()));
                                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + ", use buttons to resolve.",
                                    buttons);
                            }
                        }
                    }
                } else {
                    String sb = "A trap has been removed from planet: " + representation;
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);
                }

                return;
            }
        }
    }

    public static void checkKjalengardMechs(GenericInteractionCreateEvent event, Player player, Game game) {
        if (!player.hasUnit("kjalengard_mech")) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        List<String> tilesWithGloryTokens = ButtonHelperAgents.getGloryTokenTiles(game).stream().map(Tile::getPosition).toList();
        for (String planetId : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetId);
            int mechsOnPlanet = planet.getUnitCount(UnitType.Mech, player);
            if (mechsOnPlanet == 0) {
                continue;
            }

            Tile planetTile = game.getTileFromPlanet(planetId);
            Set<String> tilesAdjacentToMechPlanet = FoWHelper.getAdjacentTiles(game, planetTile.getPosition(), player, false, true);
            boolean hasAdjacentGloryToken = tilesAdjacentToMechPlanet.stream().anyMatch(tilesWithGloryTokens::contains);

            if (hasAdjacentGloryToken) {
                AddUnitService.addUnits(event, planetTile, game, player.getColor(), mechsOnPlanet + " infantry " + planetId);
                sb.append(player.getRepresentationNoPing())
                    .append(" added ").append(mechsOnPlanet)
                    .append(UnitEmojis.infantry)
                    .append(" to ")
                    .append(planet.getPlanetModel().getName())
                    .append(" due to ").append(mechsOnPlanet).append(" Skald ")
                    .append(UnitEmojis.mech)
                    .append(" being adjacent to a **Glory** token.\n");
            }
        }

        if (!sb.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        }
    }

}
