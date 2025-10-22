package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.TechnologyModel;
import ti4.service.planet.EmelparService;
import ti4.service.planet.FaunusService;
import ti4.service.planet.IndustrexService;
import ti4.service.turn.StartTurnService;

public class PlanetExhaustAbility extends PlanetAddRemove {

    public PlanetExhaustAbility() {
        super(Constants.PLANET_EXHAUST_ABILITY, "Exhaust Planet Ability");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        doAction(event, player, planet, game, true);
    }

    public static void doAction(
            GenericInteractionCreateEvent event, Player player, String planet, Game game, boolean exhaust) {
        doAction(player, planet, game, exhaust);
    }

    private static void doAction(Player player, String planet, Game game, boolean exhaust) {
        if (player == null) return;
        if (exhaust) {
            player.exhaustPlanetAbility(planet);
        }
        resolveAbility(player, planet, game);
    }

    private static void resolveAbility(Player player, String planet, Game game) {
        planet = AliasHandler.resolvePlanet(planet);
        PlanetModel model = Mapper.getPlanet(planet);
        MessageChannel channel = player.getCorrectChannel();

        String exhaustMsg = player.getFactionEmoji() + " used the " + model.getName() + " ability:";
        MessageHelper.sendMessageToChannelWithEmbed(channel, exhaustMsg, model.getLegendaryEmbed());

        String output = "blank";
        List<Button> buttons = new ArrayList<>();
        // List<Button> buttons2 = new ArrayList<>();
        switch (planet) {
            // Prophecy of Kings
            case "mallice", "hexmallice" -> {
                int commCount = player.getCommodities();
                if (commCount == 0) {
                    output =
                            "Use buttons to gain 2 trade goods. You have no commodities, but you may pretend to convert them to trade goods if you wish to futilely exhaust Mallice.";
                } else {
                    output = "Use buttons to gain 2 trade goods or to convert all " + commCount
                            + " of your commodities to trade goods.";
                }
                buttons.add(Buttons.green("mallice_2_tg", "Gain 2 Trade Goods"));
                buttons.add(Buttons.green("mallice_convert_comm", "Convert Commodities"));
            }
            case "hopesend" -> {
                if (player.hasAbility("scheming")) {
                    output =
                            "Use buttons to drop 1 mech on a planet or to draw 2 action cards (**Scheming** increases this from the normal 1 action card).";
                    buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
                    buttons.add(Buttons.green("draw_2_ACDelete", "Draw 2 Action Cards"));
                } else {
                    output = "Use buttons to drop 1 mech on a planet or to draw 1 action card.";
                    buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
                    buttons.add(Buttons.green("draw_1_ACDelete", "Draw 1 Action Card"));
                }
            }
            case "primor" -> {
                output = "Use buttons to drop 2 infantry on a planet.";
                buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
            }
            case "thundersedge" -> {
                output = "Use buttons to do another action.";
                buttons.addAll(StartTurnService.getStartOfTurnButtons(player, game, true, null));
            }
            case "mrte" -> {
                channel = player.getCardsInfoThread();
                output = player.getRepresentation()
                        + " Choose a secret to discard, the bot will automatically draw a replacement:";
                buttons.addAll(SecretObjectiveHelper.getSODiscardButtonsWithSuffix(player, "redraw"));
            }
            case "ordinianc4", "oridinian" -> {
                ActionCardHelper.drawActionCards(game, player, 1, true);
                String msg = "Your current command tokens are " + player.getCCRepresentation()
                        + ". Use buttons to gain 1 command token.";
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), msg, ButtonHelper.getGainCCButtons(player));
            }
            case "faunus" -> {
                output = player.getRepresentationUnfogged() + " Select a planet to gain control of:";
                output += "\n> Non-home, non-legendary planet, with no units, and no attachments";
                if (game.isFowMode()) output += "\n> Additionally, in Fog of War, you need vision of the planet";
                buttons = FaunusService.getFaunusButtons(game, player);
            }
            case "emelpar" -> {
                output = player.getRepresentationUnfogged() + " select a component to ready:";
                buttons = EmelparService.getReadyComponentButtons(game, player);
            }

            case "industrex" -> {
                output = "Choose a unit type to place:";
                buttons.addAll(IndustrexService.getIndustrexButtonsPart1(game, player));
            }
            case "tempesta" -> {
                PlanetModel tempesta = Mapper.getPlanet("tempesta");
                output = player.getFactionEmojiOrColor() + " is using _" + tempesta.getLegendaryAbilityName()
                        + "_ to apply +1 movement to a single ship.";
                game.setStoredValue("tempestaUsed", player.getFaction());
            }

            case "uikos" -> {
                int comms = player.getHarvestCounter();
                player.setHarvestCounter(0);
                player.setCommodities(player.getCommodities() + comms);
                ButtonHelperAgents.toldarAgentInitiation(game, player, comms);
                MessageHelper.sendMessageToChannel(
                        channel,
                        player.getRepresentation() + " now has " + player.getCommodities() + " commodit"
                                + (player.getCommodities() == 1 ? "y" : "ies") + " (from the " + comms
                                + " that were on the card).");
            }
            case "mirage", "illusion", "phantasm" -> {
                output = "Use buttons to put 2 fighters with your ships.";
                buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "2ff", "placeOneNDone_skipbuild"));
            }
            // Homebrew
            // case "mr" -> {
            //     output = "Use buttons to destroy a ground force on a legendary or a planet adjacent to Mecatol Rex.";
            //     buttons.addAll(ButtonHelper.customRexLegendary(player, game));
            // }
            case "avernus" -> {
                output = "Select the tile you would like to starforge in:";
                List<Tile> tiles = ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Warsun);
                for (Tile tile : tiles) {
                    buttons.add(Buttons.green(
                            "starforgeTileFree_" + tile.getPosition(), tile.getRepresentationForButtons(game, player)));
                }
            }

            case "silence" -> {
                output = "Use buttons to put 1 cruiser with your ships.";
                buttons.addAll(
                        Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
            }
            case "tarrock" -> {
                String riderName = "Tarrock Ability";
                List<Button> riderButtons =
                        AgendaHelper.getAgendaButtons(riderName, game, player.getFinsFactionCheckerPrefix());
                // List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                MessageHelper.sendMessageToChannelWithFactionReact(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", please choose your target.",
                        game,
                        player,
                        riderButtons);
                // MessageHelper.sendMessageToChannelWithPersistentReacts(game.getActionsChannel(), "Please indicate
                // \"no afters\" again.", game, afterButtons, GameMessageType.AGENDA_AFTER);
            }
            case "prism" -> {
                output = player.getFactionEmoji() + ", please choose a technology to return.";
                buttons.addAll(getNewPrismLoseTechOptions(player));
            }
            case "echo" -> {
                output =
                        "Use buttons to place a frontier token in a system with no planets.\n-# Cannot yet place a double frontier token in a system, sorry.";
                buttons.addAll(ButtonHelper.getEchoAvailableSystems(game, player));
            }
            case "domna" -> {
                output = "Please choose the system that the ship you wish to move is in.";
                buttons.addAll(ButtonHelper.getDomnaStepOneTiles(player, game));
            }
            case "eko" -> output = "blank";
            default -> {
                if (ButtonHelper.getUnitHolderFromPlanetName(planet, game) != null
                        && game.isAbsolMode()
                        && ButtonHelper.getUnitHolderFromPlanetName(planet, game)
                                .getTokenList()
                                .contains("attachment_nanoforge.png")) {
                    player.refreshPlanet(planet);
                }
            }
        }

        if (!buttons.isEmpty()) buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
        if (!"blank".equalsIgnoreCase(output)) {
            MessageHelper.sendMessageToChannelWithButtons(channel, output, buttons);
        }
    }

    private static List<Button> getNewPrismLoseTechOptions(Player player) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!techM.isUnitUpgrade()
                    && (techM.getFaction().isEmpty()
                            || techM.getFaction().orElse("").isEmpty())) {
                buttons.add(Buttons.gray(finChecker + "newPrism@" + tech, techM.getName()));
            }
        }
        return buttons;
    }
}
