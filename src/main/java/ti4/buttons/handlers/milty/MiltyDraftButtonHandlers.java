package ti4.buttons.handlers.milty;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.service.milty.DraftDisplayService;
import ti4.service.milty.MiltyDraftManager;

@UtilityClass
public class MiltyDraftButtonHandlers {

    @ButtonHandler("showMiltyDraft")
    private void postDraftInfo(ButtonInteractionEvent event, Game game) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        DraftDisplayService.repostDraftInformation(event, manager, game);
    }

    @ButtonHandler("milty_")
    private void doMiltyDraftPick(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        MiltyDraftManager manager = game.getMiltyDraftManager();
        manager.doMiltyPick(event, game, buttonID, player);
    }

    @ButtonHandler("miltyFactionInfo_")
    private void sendAvailableFactionInfo(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null) return;
        String whichOnes = buttonID.replace("miltyFactionInfo_", "");
        List<FactionModel> displayFactions = new ArrayList<>();
        switch (whichOnes) {
            case "all" -> displayFactions.addAll(game.getMiltyDraftManager().allFactions());
            case "picked" -> displayFactions.addAll(game.getMiltyDraftManager().pickedFactions());
            case "remaining" -> displayFactions.addAll(game.getMiltyDraftManager().remainingFactions());
        }

        boolean first = true;
        List<MessageEmbed> embeds = displayFactions.stream().map(FactionModel::fancyEmbed).toList();
        for (MessageEmbed e : embeds) {
            String message = "";
            if (first)
                message = player.getRepresentationUnfogged() + " Here's an overview of the factions:";
            MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
            first = false;
        }
    }
}
