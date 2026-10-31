package ti4.discord.interactions.commands.homebrew.lost_legacies;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.HydromorphicHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.CardEmojis;

class HydromorphicDraw extends GameStateSubcommand {
    HydromorphicDraw() {
        super("hc_draw", "Draw Hydromorphic cards", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Cards to draw, default 1, max 10")
                .setRequiredRange(1, 10));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int count = Math.clamp(event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt), 1, 10);
        int drawn = 0;
        for (int index = 0; index < count; index++) {
            if (game.drawHydromorphicCard(player) != null) {
                drawn++;
            }
        }
        if (drawn == 0) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic cards remain in the deck.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " drew " + drawn + " Hydromorphic card" + (drawn == 1 ? "." : "s."));
        HydromorphicHelper.sendHydromorphicInfo(game, player);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}

class HydromorphicDiscard extends GameStateSubcommand {
    HydromorphicDiscard() {
        super("hc_discard", "Discard a Hydromorphic card without resolving it", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.HYDROMORPHIC_CARD_ID, "Hydromorphic card ID")
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int identifier = event.getOption(Constants.HYDROMORPHIC_CARD_ID).getAsInt();
        String cardId = player.removeHydromorphicCard(identifier);
        if (cardId == null) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic card with that ID was found.");
            return;
        }
        game.returnHydromorphicCard(cardId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " discarded the Hydromorphic card _"
                        + Mapper.getHydromorphicCard(cardId).getName() + "_.");
        HydromorphicHelper.sendHydromorphicInfo(game, player);
    }
}

class HydromorphicSend extends GameStateSubcommand {
    HydromorphicSend() {
        super("hc_send", "Send a Hydromorphic card to a player", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.HYDROMORPHIC_CARD_ID, "Hydromorphic card ID")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or color to send to")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player sender = getPlayer();
        Player receiver = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (receiver == null || receiver == sender) {
            MessageHelper.sendMessageToEventChannel(event, "Choose another player to receive the Hydromorphic card.");
            return;
        }
        int identifier = event.getOption(Constants.HYDROMORPHIC_CARD_ID).getAsInt();
        String cardId = sender.removeHydromorphicCard(identifier);
        if (cardId == null) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic card with that ID was found.");
            return;
        }
        receiver.setHydromorphicCard(cardId);
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(
                    game, event, sender, receiver, CardEmojis.HydromorphicCard + " Hydromorphic card", null);
        }
        MessageHelper.sendMessageToChannel(
                sender.getCorrectChannel(),
                sender.getRepresentationNoPing() + " sent a Hydromorphic card to " + receiver.getRepresentationNoPing()
                        + ".");
        HydromorphicHelper.sendHydromorphicInfo(game, sender);
        HydromorphicHelper.sendHydromorphicInfo(game, receiver);
    }
}

class HydromorphicDrawSpecific extends GameStateSubcommand {
    HydromorphicDrawSpecific() {
        super("hc_draw_specific", "Draw a specific Hydromorphic card", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.HYDROMORPHIC_CARD, "Hydromorphic card alias")
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String cardId =
                event.getOption(Constants.HYDROMORPHIC_CARD).getAsString().toLowerCase();
        if (Mapper.getHydromorphicCard(cardId) == null) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic card with that alias exists.");
            return;
        }
        game.initializeHydromorphicDeck();
        if (!game.getHydromorphicCards().remove(cardId)) {
            MessageHelper.sendMessageToEventChannel(event, "That Hydromorphic card is not in the shared deck.");
            return;
        }
        player.setHydromorphicCard(cardId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " drew the "
                        + Mapper.getHydromorphicCard(cardId).getNameRepresentation() + " Hydromorphic card.");
        HydromorphicHelper.sendHydromorphicInfo(game, player);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
