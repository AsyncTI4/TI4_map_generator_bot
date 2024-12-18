package ti4.commands2.planet;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.TechnologyModel;

public class PlanetExhaustAbility extends PlanetAddRemove {

    public PlanetExhaustAbility() {
        super(Constants.PLANET_EXHAUST_ABILITY, "Exhaust Planet Ability");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        doAction(player, planet, game, true);
    }

    public static void doAction(Player player, String planet, Game game, boolean exhaust) {
        if (player == null) return;
        if (exhaust) {
            player.exhaustPlanetAbility(planet);
        }
        resolveAbility(player, planet, game);
    }

    public static void resolveAbility(Player player, String planet, Game game) {
        planet = AliasHandler.resolvePlanet(planet);
        PlanetModel model = Mapper.getPlanet(planet);
        MessageChannel channel = player.getCorrectChannel();

        String exhaustMsg = player.getFactionEmoji() + " used the " + model.getName() + " ability:";
        MessageHelper.sendMessageToChannelWithEmbed(channel, exhaustMsg, model.getLegendaryEmbed());

        String output = "blank";
        List<Button> buttons = new ArrayList<>();
        List<Button> buttons2 = new ArrayList<>();
        switch (planet) {
            // Prophecy of Kings
            case "mallice" -> {
                output = "Use buttons to gain 2TGs or wash your commodities";
                buttons.add(Buttons.green("mallice_2_tg", "Gain 2TGs"));
                buttons.add(Buttons.green("mallice_convert_comm", "Convert Commodities"));
            }
            case "hopesend" -> {
                output = "Use buttons to drop 1 mech on a planet or draw 1 AC";
                buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
                if (player.hasAbility("scheming")) {
                    buttons.add(Buttons.green("draw_2_ACDelete", "Draw 2 ACs (With Scheming)"));
                } else {
                    buttons.add(Buttons.green("draw_1_ACDelete", "Draw 1 AC"));
                }
            }
            case "primor" -> {
                output = "Use buttons to drop 2 infantry on a planet";
                buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
            }
            case "mirage" -> {
                output = "Use buttons to put 2 fighters with your ships";
                buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "2ff", "placeOneNDone_skipbuild"));
            }
            // Homebrew
            case "mr" -> {
                output = "Use buttons to destroy a ground force on a legendary or planet adjacent to Mecatol Rex.";
                buttons.addAll(ButtonHelper.customRexLegendary(player, game));
            }
            case "silence" -> {
                output = "Use buttons to put 1 cruiser with your ships";
                buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
            }
            case "tarrock" -> {
                if (!game.isFowMode() && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1705824000011L)) < 0) {
                    String riderName = "Tarrock Ability";
                    List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, player.getFinsFactionCheckerPrefix());
                    List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                    MessageHelper.sendMessageToChannelWithFactionReact(player.getCorrectChannel(), "Please select your target", game, player, riderButtons);
                    MessageHelper.sendMessageToChannelWithPersistentReacts(game.getActionsChannel(), "Please indicate no afters again.", game, afterButtons, "after");
                } else {
                    AgendaHelper.drawAgenda(1, game, player);
                }
            }
            case "prism" -> {
                if (!game.isFowMode() && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1705824000011L)) > 0) {
                    resolvePrismStep1(player, game);
                } else {
                    output = player.getFactionEmoji() + " choose a tech to return";
                    buttons.addAll(getNewPrismLoseTechOptions(player));
                }
            }
            case "echo" -> {
                output = "Use buttons to place a frontier token in a system with no planets (cannot yet place a double frontier token in a system, sorry)";
                buttons.addAll(ButtonHelper.getEchoAvailableSystems(game, player));
            }
            case "domna" -> {
                output = "Use buttons to select which system the ship you want to move is in";
                buttons.addAll(ButtonHelper.getDomnaStepOneTiles(player, game));
            }
            case "eko" -> output = "blank";
            default -> {
                if (ButtonHelper.getUnitHolderFromPlanetName(planet, game) != null && game.isAbsolMode() && ButtonHelper.getUnitHolderFromPlanetName(planet, game).getTokenList().contains("attachment_nanoforge.png")) {
                    player.refreshPlanet(planet);
                }

            }
        }

        if (!buttons.isEmpty()) buttons.add(Buttons.red("deleteButtons", "Delete these buttons"));
        if (!"blank".equalsIgnoreCase(output)) {
            MessageHelper.sendMessageToChannelWithButtons(channel, output, buttons);
        }
    }

    public static List<Button> getNewPrismLoseTechOptions(Player player) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!techM.isUnitUpgrade() && (techM.getFaction().isEmpty() || techM.getFaction().orElse("").isEmpty())) {
                buttons.add(Buttons.gray(finChecker + "newPrism@" + tech, techM.getName()));
            }
        }
        return buttons;
    }

    public static void resolvePrismStep1(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("prismStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray("prismStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentationUnfogged() + " tell the bot who you want to force into giving you a PN or AC", buttons);
    }
}
