package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.StrategyCardModel;
import ti4.service.SendPromissoryService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class MindsieveService {
    private String id = "naalubt";

    public String mindsieve() {
        return Mapper.getBreakthrough(id).getNameRepresentation();
    }

    public boolean canUseMindsieve(Player naalu, Player primary, StrategyCardModel scModel) {
        if (scModel == null || scModel.usesAutomationForSCID("pok1leadership")) return false;
        if (!naalu.hasUnlockedBreakthrough(id)) return false;
        for (String pn : naalu.getPromissoryNotes().keySet()) {
            PromissoryNoteModel model = Mapper.getPromissoryNote(pn);
            if (naalu.getPromissoryNotesInPlayArea().contains(pn)) continue;
            if (model.getName().equals("Alliance") && primary.hasAbility("hubris")) continue;
            return true;
        }
        return false;
    }

    public void serveMindsieveButtons(Game game, Player naalu, int sc) {
        Player primary = game.getPlayerFromSC(sc);
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        if (!canUseMindsieve(naalu, primary, scModel)) return;

        String msg = naalu.getRepresentation() + " since you have " + mindsieve()
                + " you are able to send a promissory note to " + primary.getRepresentationNoPing();
        msg +=
                " instead of spending a command token. If you would like to do so, choose which promissory note to send by clicking one of the buttons:";

        List<Button> buttons = new ArrayList<>();
        for (String pn : naalu.getPromissoryNotes().keySet()) {
            PromissoryNoteModel model = Mapper.getPromissoryNote(pn);
            if (naalu.getPromissoryNotesInPlayArea().contains(pn)) continue;
            if (model.getName().equals("Alliance") && primary.hasAbility("hubris")) {
                String fmt = "\n-# > - Since they %s, you cannot send the _Alliance_ promissory note.";
                msg += String.format(fmt, game.isFrankenGame() ? "have the **Hubris** ability" : "are playing Mahact");
                continue;
            }
            Player owner = game.getPNOwner(pn);
            buttons.add(Buttons.green("mindsieveFollow_" + sc + "_" + pn, model.getName(), owner.fogSafeEmoji()));
        }
        buttons.add(
                Buttons.DONE_DELETE_BUTTONS.withLabel("Decline Mindsieve").withEmoji(FactionEmojis.Naalu.asEmoji()));
        MessageHelper.sendMessageToChannelWithButtons(naalu.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("mindsieveFollow_")
    private void followWithMindsieve(ButtonInteractionEvent event, Game game, Player naalu, String buttonID) {
        String regex = "mindsieveFollow_" + RegexHelper.intRegex("sc") + "_" + RegexHelper.pnRegex(game, naalu);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            int sc = Integer.parseInt(matcher.group("sc"));
            String pn = matcher.group("pn");

            Player primary = game.getPlayerFromSC(sc);
            StrategyCardModel scModel =
                    game.getStrategyCardModelByInitiative(sc).orElse(null);
            SendPromissoryService.sendPromissoryToPlayer(event, game, naalu, primary, pn);

            if (!naalu.getFollowedSCs().contains(sc))
                ButtonHelperFactionSpecific.resolveVadenSCDebt(naalu, sc, game, event);
            naalu.addFollowedSC(sc, event);

            String messageID = game.getStoredValue("scPlayMsgID" + sc);
            ReactionService.addReaction(naalu, false, null, null, messageID, game);

            MessageChannel scChannel = ButtonHelper.getSCFollowChannel(game, naalu, sc);
            String msg = naalu.getRepresentationUnfogged() + " sent a promissory note to the " + scModel.getName()
                    + " holder ";
            msg += "to follow the strategy card without spending a command token.";
            MessageHelper.sendMessageToChannel(scChannel, msg);
            ButtonHelper.deleteMessage(event);
        });
    }
}
