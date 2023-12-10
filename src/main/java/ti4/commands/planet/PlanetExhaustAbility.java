package ti4.commands.planet;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.agenda.DrawAgenda;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlanetExhaustAbility extends PlanetAddRemove {
    public PlanetExhaustAbility() {
        super(Constants.PLANET_EXHAUST_ABILITY, "Exhaust Planet Ability");
    }
    @Override
    public void doAction(Player player, String planet, Game activeGame) {
        doAction(player, planet, activeGame, true);
    }
    
    public void doAction(Player player, String planet, Game activeGame, boolean exhaust) {
        if(exhaust){
            player.exhaustPlanetAbility(planet);
        }
        MessageChannel channel = activeGame.getMainGameChannel();
        if (activeGame.isFoWMode()) {
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
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeGame, "mech", "placeOneNDone_skipbuild"));
            if (player.hasAbility("scheming")) {
                buttons.add(Button.success("draw_2_ACDelete", "Draw 2 AC (With Scheming)"));
            } else {
                buttons.add(Button.success("draw_1_ACDelete", "Draw 1 AC"));
            }
        }
        if ("primor".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Primor's Ability");
            buttons.addAll(Helper.getPlanetPlaceUnitButtons(player, activeGame, "2gf", "placeOneNDone_skipbuild"));
            message = "Use buttons to drop 2 infantry on a planet";
        }
        if ("eko".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Eko's Ability and ignore the effects of anomalies");
        }
        if ("mr".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Mecatol Rex's Ability");
            buttons.addAll(ButtonHelper.customRexLegendary(player, activeGame));
            message = "Use buttons to destroy a ground force on a legendary or planet adjacent to rex";
        }
        if ("mirage".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Mirage's Ability");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "2ff", "placeOneNDone_skipbuild"));
            message = "Use buttons to put 2 fighters with your ships";
        }
        if ("silence".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Silence's Ability");
            buttons.addAll(Helper.getTileWithShipsPlaceUnitButtons(player, activeGame, "cruiser", "placeOneNDone_skipbuild"));
            message = "Use buttons to put 1 cruiser with your ships";
        }
        if ("tarrock".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Tarrock's Ability to draw 1 agenda and bottom/top it");
            new DrawAgenda().drawAgenda(1, activeGame, player);
        }
        if ("prism".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Prism's Ability to Force Another Player to give either an AC or PN");
            resolvePrismStep1(player, activeGame);
        }
        if ("echo".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Echo's Ability");
            buttons.addAll(ButtonHelper.getEchoAvailableSystems(activeGame, player));
            message = "Use buttons to place a frontier token in a system with no planets (cannot yet place a double frontier token in a system, sorry)";
        }
        if ("domna".equalsIgnoreCase(AliasHandler.resolvePlanet(planet))) {
            MessageHelper.sendMessageToChannel(channel, ButtonHelper.getIdent(player) + " Chose to Exhaust Domna's Ability");
            buttons.addAll(ButtonHelper.getDomnaStepOneTiles(player, activeGame));
            message = "Use buttons to select which system the ship you want to move is in";
        }
        buttons.add(Button.danger("deleteButtons", "Delete these buttons"));

        if (!"blank".equalsIgnoreCase(message)) {

            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);

        }

    }

    public void resolvePrismStep1(Player player, Game activeGame) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (activeGame.isFoWMode()) {
                buttons.add(Button.secondary("prismStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Button.secondary("prismStep2_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
            player.getRepresentation(true, true) + " tell the bot who you want to force into giving you a PN or AC", buttons);
    }

    public void resolvePrismStep2(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
       
            buttons.add(Button.secondary("prismStep3_" + player.getFaction()+"_AC", "Send AC"));
            buttons.add(Button.secondary("prismStep3_" + player.getFaction()+"_PN", "Send PN"));
        
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ButtonHelper.getIdent(player) + " chose " + ButtonHelper.getIdentOrColor(p2, activeGame) + " as the target of the prism ability. The target has been sent buttons to resolve.");
        MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(),
            p2.getRepresentation(true, true) + " you have had the Prism ability hit you. Please tell the bot if you wish to send an AC or a PN", buttons);
    }

    public void resolvePrismStep3(Player player, Game activeGame, ButtonInteractionEvent event, String buttonID) {
        Player p2 = activeGame.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons;
        String pnOrAC = buttonID.split("_")[2];
        event.getMessage().delete().queue();
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),ButtonHelper.getIdent(player)+" chose to send a "+pnOrAC);
        if("pn".equalsIgnoreCase(pnOrAC)){
            buttons =  ButtonHelper.getForcedPNSendButtons(activeGame, p2, player);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), player.getRepresentation(true, true) + " resolve", buttons);

        } else {
            String buttonID2 = "transact_ACs_" + p2.getFaction();
            ButtonHelper.resolveSpecificTransButtons(activeGame, player, buttonID2, event);
        }

    }
}
