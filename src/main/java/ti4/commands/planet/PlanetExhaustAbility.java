package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.agenda.DrawAgenda;
import ti4.commands.player.TurnStart;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

public class PlanetExhaustAbility extends PlanetAddRemove {
    public PlanetExhaustAbility() {
        super(Constants.PLANET_EXHAUST_ABILITY, "Exhaust Planet Ability");
    }

    @Override
    public void doAction(Player player, String planet, Game game) {
        doAction(player, planet, game, true);
    }

    public void doAction(Player player, String planet, Game game, boolean exhaust) {
        if (player == null) return;
        if (exhaust) {
            player.exhaustPlanetAbility(planet);
        }
        MessageChannel channel = game.getMainGameChannel();
        if (game.isFoWMode()) {
            channel = player.getPrivateChannel();
        }
        List<Button> buttons = new ArrayList<>();
        String message = "blank";
        if ("mallice".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Mallice Ability");
            message = "Use buttons to gain 2 tg or wash your commodities";
            buttons.add(Button.success("mallice_2_tg", "Gain 2tg"));
            buttons.add(Button.success("mallice_convert_comm", "Convert Commodities"));

        }

        if ("hopesend".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Hope's End Ability");
            message = "Use buttons to drop a mech on a planet or draw an AC";
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "mech", "placeOneNDone_skipbuild"));
            if (player.hasAbility("scheming")) {
                buttons.add(Button.success("draw_2_ACDelete", "Draw 2 AC (With Scheming)"));
            } else {
                buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
            }
        }
        if ("primor".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Primor's Ability");
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
            message = "Use buttons to drop 2 infantry on a planet";
        }
        if ("eko".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Eko's Ability and ignore the effects of anomalies");
        }
        if ("mr".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Mecatol Rex's Ability");
            buttons.addAll(ButtonHelper.customRexLegendary(player, game));
            message = "Use buttons to destroy a ground force on a legendary or planet adjacent to rex";
        }
        if ("mirage".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Mirage's Ability");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "2ff", "placeOneNDone_skipbuild"));
            message = "Use buttons to put 2 fighters with your ships";
        }
        if ("silence".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Silence's Ability");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, game, "cruiser", "placeOneNDone_skipbuild"));
            message = "Use buttons to put 1 cruiser with your ships";
        }
        if (ButtonHelper.getUnitHolderFromPlanetName(planet, game) != null && game.isAbsolMode() && ButtonHelper.getUnitHolderFromPlanetName(planet, game).getTokenList().contains("attachment_nanoforge.png")) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Nanoforge Ability to ready the planet (technically done right after it was exhausted)");
            player.refreshPlanet(planet);
        }
        if ("tarrock".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            if (!game.isFoWMode() && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1705824000011L)) < 0) {
                String riderName = "Tarrock Ability";
                List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, player.getFinsFactionCheckerPrefix());
                List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
                MessageHelper.sendMessageToChannelWithFactionReact(ButtonHelper.getCorrectChannel(player, game), "Please select your target", game, player, riderButtons);
                MessageHelper.sendMessageToChannelWithPersistentReacts(game.getActionsChannel(), "Please indicate no afters again.", game, afterButtons, "after");
            } else {
                MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Tarrock's Ability to draw 1 agenda and bottom/top it");
                new DrawAgenda().drawAgenda(1, game, player);
            }
        }
        if ("prism".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            if (!game.isFoWMode() && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1705824000011L)) > 0) {
                MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Prism's Ability to Force Another Player to give either an AC or PN");
                resolvePrismStep1(player, game);
            } else {
                MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Prism's Ability to Return a Non-Faction, Non-Unit Upgrade tech and get one with the same number of prereqs");
                MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Choose a tech to return", getNewPrismLoseTechOptions(player, game));
            }
        }
        if ("echo".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Echo's Ability");
            buttons.addAll(ButtonHelper.getEchoAvailableSystems(game, player));
            message = "Use buttons to place a frontier token in a system with no planets (cannot yet place a double frontier token in a system, sorry)";
        }
        if ("domna".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Domna's Ability");
            buttons.addAll(ButtonHelper.getDomnaStepOneTiles(player, game));
            message = "Use buttons to select which system the ship you want to move is in";
        }
        buttons.add(Button.danger("deleteButtons", "Delete these buttons"));

        if (!"blank".equalsIgnoreCase(message)) {

            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);

        }

    }

    public static List<Button> getNewPrismLoseTechOptions(Player player, Game game) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : player.getTechs()) {
            TechnologyModel techM = Mapper.getTech(tech);
            if (!"unitupgrade".equalsIgnoreCase(techM.getType().toString()) && (techM.getFaction().isEmpty() || techM.getFaction().orElse("").length() < 1)) {
                buttons.add(Button.secondary(finChecker + "newPrism@" + tech, techM.getName()));
            }
        }
        return buttons;
    }

    public static void newPrismPart2(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String techOut = buttonID.split("@")[1];
        player.removeTech(techOut);
        TechnologyModel techM1 = Mapper.getTech(techOut);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game), ButtonHelper.getIdent(player) + " removed the tech " + techM1.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), player.getRepresentation() + " Use the button to get a tech with the same number of pre-requisites", Buttons.GET_A_FREE_TECH);
        event.getMessage().delete().queue();
        String message2 = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = TurnStart.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message2, systemButtons);
    }

    public void resolvePrismStep1(Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (game.isFoWMode()) {
                buttons.add(Button.secondary("prismStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("prismStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, game),
            player.getRepresentation(true, true) + " tell the bot who you want to force into giving you a PN or AC", buttons);
    }

    public void resolvePrismStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();

        buttons.add(Button.secondary("prismStep3_" + player.getFaction() + "_AC", "Send AC"));
        buttons.add(Button.secondary("prismStep3_" + player.getFaction() + "_PN", "Send PN"));

        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " chose " + ButtonHelper.getIdentOrColor(p2, game) + " as the target of the prism ability. The target has been sent buttons to resolve.");
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
            p2.getRepresentation(true, true) + " you have had the Prism ability hit you. Please tell the bot if you wish to send an AC or a PN", buttons);
    }

    public void resolvePrismStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons;
        String pnOrAC = buttonID.split("_")[2];
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, game), ButtonHelper.getIdent(player) + " chose to send a " + pnOrAC);
        if ("pn".equalsIgnoreCase(pnOrAC)) {
            buttons = ButtonHelper.getForcedPNSendButtons(game, p2, player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " resolve", buttons);

        } else {
            String buttonID2 = "transact_ACs_" + p2.getFaction();
            ButtonHelper.resolveSpecificTransButtons(game, player, buttonID2, event);
        }

    }
}
