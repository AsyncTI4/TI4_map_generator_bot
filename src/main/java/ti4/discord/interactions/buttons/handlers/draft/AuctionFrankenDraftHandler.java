package ti4.discord.interactions.buttons.handlers.draft;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.draft.DraftCategory;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.franken.AuctionFrankenDraftService;

@UtilityClass
public class AuctionFrankenDraftHandler {

    @ButtonHandler(AuctionFrankenDraftService.ACTION_PREFIX)
    public void resolve(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.split(";");
        if (parts.length < 2) return;
        if ("showDrafted".equals(parts[1])) {
            AuctionFrankenDraftService.showDraftedComponents(game, player, event);
            return;
        }
        if (parts.length < 3) return;
        int nomination;
        try {
            nomination = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        switch (parts[1]) {
            case "nominate" -> {
                if (parts.length != 4) return;
                DraftCategory category;
                try {
                    category = DraftCategory.valueOf(parts[3]);
                } catch (IllegalArgumentException ignored) {
                    return;
                }
                AuctionFrankenDraftService.nominate(game, player, event, nomination, category);
            }
            case "review" -> {
                if (parts.length >= 4) {
                    AuctionFrankenDraftService.review(
                            game, player, event, nomination, parts[3], parts.length == 5 ? parts[4] : "");
                }
            }
            case "bid" -> AuctionFrankenDraftService.showBidModal(game, player, event, nomination);
            case "zeroBid" -> AuctionFrankenDraftService.submitZeroBid(game, player, event, nomination);
            case "auto" -> AuctionFrankenDraftService.toggleAutoPick(game, player, event, nomination);
            case "rank" -> {
                if (parts.length == 4) AuctionFrankenDraftService.rank(game, player, event, nomination, parts[3]);
            }
            case "resetRankings" -> AuctionFrankenDraftService.resetRankings(game, player, event, nomination);
            case "pick" -> {
                if (parts.length == 4) AuctionFrankenDraftService.pick(game, player, event, nomination, parts[3]);
            }
            default -> {}
        }
    }

    @ModalHandler(AuctionFrankenDraftService.BID_MODAL)
    public void submitBid(Game game, Player player, ModalInteractionEvent event, String modalID) {
        String[] parts = modalID.split(";");
        if (parts.length != 2) return;
        try {
            AuctionFrankenDraftService.submitBid(game, player, event, Integer.parseInt(parts[1]));
        } catch (NumberFormatException ignored) {
        }
    }
}
