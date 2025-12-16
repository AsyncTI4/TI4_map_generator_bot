package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class SowingReapingService {

    private String sowingRep() {
        return Mapper.getBreakthrough("firmamentbt").getNameRepresentation();
    }

    private String reapingRep() {
        return Mapper.getBreakthrough("obsidianbt").getNameRepresentation();
    }

    public void sendTheSowingButtons(Game game) {
        Player firmament = Helper.getPlayerFromUnlockedBreakthrough(game, "firmamentbt");
        if (firmament == null) return;
        if (firmament.getTg() <= 0) return;

        // At the start of the status phase, you may place up to 3 of your trade goods on this card.
        List<Button> buttons = new ArrayList<>();
        for (int i = 1; i <= Math.min(3, firmament.getTg()); i++) {
            buttons.add(Buttons.green(
                    firmament.finChecker() + "theSowingAddTg_" + i, i + " tg", MiscEmojis.getTGorNomadCoinEmoji(game)));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No thanks"));

        String message =
                firmament.getRepresentation() + " you may place up to 3 of your trade goods on your breakthrough "
                        + FactionEmojis.Firmament + " **The Sowing**.";
        int btTG = firmament.getBreakthroughTGs("firmamentbt");
        int tg = firmament.getTg();
        message += "\n-# There are " + btTG + " trade goods on **The Sowing**, and you currently have "
                + MiscEmojis.tg(tg) + " " + tg + " trade goods.";
        MessageHelper.sendMessageToChannelWithButtons(firmament.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("theSowingAddTg_")
    private void addTgsToTheSowing(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "theSowingAddTg_" + RegexHelper.intRegex("amt");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            int amt = Integer.parseInt(matcher.group("amt"));
            player.setBreakthroughTGs("firmamentbt", player.getBreakthroughTGs("firmamentbt") + amt);
            player.setTg(player.getTg() - amt);

            String message = player.getRepresentation(false, false) + " added " + amt
                    + " trade goods to their breakthrough, " + sowingRep() + ".";
            message += "\n-# **The Sowing** now has " + player.getBreakthroughTGs() + " trade goods.";
            message += "\n-# You now have " + player.getTg() + " trade goods.";

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    public void resolveTheReaping(Game game) {
        Player obsidian = Helper.getPlayerFromUnlockedBreakthrough(game, "obsidianbt");
        if (obsidian == null) return;
        if (obsidian.getBreakthroughTGs("obsidianbt") <= 0) {
            obsidian.setBreakthroughTGs("obsidianbt", 0);
            return;
        }

        // Place 1 trade good from the supply onto this card each time you win a combat against a puppeted player.
        // At the start of the status phase, gain all trade goods on this card, then gain an equal number of trade goods
        // from the supply.
        int tgs = obsidian.getBreakthroughTGs("obsidianbt");
        obsidian.setBreakthroughTGs("obsidianbt", 0);

        String message = obsidian.getRepresentation() + " gained the " + tgs + " on their breakthrough "
                + FactionEmojis.Obsidian + " **The Reaping** as well as " + tgs + " more from the supply.";
        message += "\n-# " + MiscEmojis.getTGorNomadCoinEmoji(game) + " TGs went from " + obsidian.gainTG(tgs * 2);
        MessageHelper.sendMessageToChannel(obsidian.getCorrectChannel(), message);

        ButtonHelperAgents.resolveArtunoCheck(obsidian, tgs);
        ButtonHelperAbilities.pillageCheck(obsidian);
        ButtonHelperAbilities.pillageCheck(obsidian);
    }

    @ButtonHandler("theReapingAddTg")
    private void addTgToTheReaping(ButtonInteractionEvent event, Game game, Player player) {
        String message = player.getRepresentation(false, false)
                + " won combat against a puppeted player and added 1 trade good to their breakthrough, " + reapingRep()
                + ".";
        player.setBreakthroughTGs("obsidianbt", player.getBreakthroughTGs("obsidianbt") + 1);
        message += "\n-# **The Reaping** now has " + player.getBreakthroughTGs() + " trade goods.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }
}
