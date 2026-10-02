package ti4.discord.interactions.commands.game;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class PlaceDrawableCard extends GameStateSubcommand {

    private static final String DECK_TYPE = "deck_type";
    private static final String CARD_ID = "card_id";
    private static final String POSITION = "position";
    private static final String TOP = "top";
    private static final String BOTTOM = "bottom";
    private static final String ACTION_CARD = "action_card";
    private static final String SECRET_OBJECTIVE = "secret_objective";
    private static final String RELIC = "relic";
    private static final String EXPLORE = "explore";
    private static final String AGENDA = "agenda";
    private static final String EVENT = "event";

    PlaceDrawableCard() {
        super("place_drawable_card", "Place a drawable card on the top or bottom of its deck", true, true);
        addOptions(new OptionData(OptionType.STRING, DECK_TYPE, "Deck containing the card")
                .addChoice("Action card", ACTION_CARD)
                .addChoice("Secret objective", SECRET_OBJECTIVE)
                .addChoice("Relic", RELIC)
                .addChoice("Explore", EXPLORE)
                .addChoice("Agenda", AGENDA)
                .addChoice("Event", EVENT)
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, CARD_ID, "Card ID").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, POSITION, "Where to place the card")
                .addChoice("Top", TOP)
                .addChoice("Bottom", BOTTOM)
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color holding the card")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String deckType = event.getOption(DECK_TYPE, null, OptionMapping::getAsString);
        String cardId = event.getOption(CARD_ID, null, OptionMapping::getAsString);
        String position = event.getOption(POSITION, null, OptionMapping::getAsString);
        if (deckType == null || cardId == null || position == null) {
            return;
        }

        Game game = getGame();
        Player player = getPlayer();
        List<String> deck = getDeck(game, deckType);
        if (deck == null || !isCardTypeValid(deckType, cardId)) {
            MessageHelper.sendMessageToEventChannel(event, "That card ID is not valid for the selected deck: `" + cardId + "`.");
            return;
        }

        removeCardFromCurrentLocation(game, player, deckType, cardId);
        if (TOP.equals(position)) {
            deck.addFirst(cardId);
        } else {
            deck.addLast(cardId);
        }
        MessageHelper.sendMessageToEventChannel(event, "Placed a card on the " + position + " of the "
                + deckType.replace('_', ' ') + " deck.");
    }

    private List<String> getDeck(Game game, String deckType) {
        return switch (deckType) {
            case ACTION_CARD -> game.getActionCards();
            case SECRET_OBJECTIVE -> game.getSecretObjectives();
            case RELIC -> game.getAllRelics();
            case EXPLORE -> game.getAllExplores();
            case AGENDA -> game.getAgendas();
            case EVENT -> game.getEvents();
            default -> null;
        };
    }

    private boolean isCardTypeValid(String deckType, String cardId) {
        return switch (deckType) {
            case ACTION_CARD -> Mapper.getActionCard(normalizeCardId(cardId)) != null;
            case SECRET_OBJECTIVE -> Mapper.getSecretObjective(normalizeCardId(cardId)) != null;
            case RELIC -> Mapper.getRelic(cardId) != null;
            case EXPLORE -> Mapper.getExplore(cardId) != null;
            case AGENDA -> Mapper.getAgenda(cardId) != null;
            case EVENT -> Mapper.getEvent(cardId) != null;
            default -> false;
        };
    }

    private void removeCardFromCurrentLocation(Game game, Player player, String deckType, String cardId) {
        switch (deckType) {
            case ACTION_CARD -> removeActionCard(game, player, cardId);
            case SECRET_OBJECTIVE -> removeSecretObjective(game, player, cardId);
            case RELIC -> removeRelic(game, player, cardId);
            case EXPLORE -> removeExplore(game, cardId);
            case AGENDA -> removeAgenda(game, cardId);
            case EVENT -> removeEvent(game, player, cardId);
            default -> {
            }
        }
    }

    private void removeActionCard(Game game, Player player, String cardId) {
        if (game.getActionCards().remove(cardId)) {
            return;
        }
        if (game.getDiscardActionCards().remove(cardId) != null) {
            game.getDiscardACStatus().remove(cardId);
            game.getPlayedActionCards().remove(cardId);
            return;
        }
        player.getActionCards().remove(cardId);
    }

    private void removeSecretObjective(Game game, Player player, String cardId) {
        if (game.getSecretObjectives().remove(cardId)) {
            return;
        }
        player.getSecrets().remove(normalizeCardId(cardId));
    }

    private void removeRelic(Game game, Player player, String cardId) {
        if (game.getAllRelics().remove(cardId)) {
            return;
        }
        if (player.getRelics().contains(cardId)) {
            player.removeRelic(cardId);
            player.removeExhaustedRelic(cardId);
        }
    }

    private void removeExplore(Game game, String cardId) {
        if (!game.getAllExplores().remove(cardId)) {
            game.getAllExploreDiscard().remove(cardId);
        }
    }

    private void removeAgenda(Game game, String cardId) {
        if (game.getAgendas().remove(cardId)) {
            return;
        }
        if (game.getDiscardAgendas().remove(cardId) != null) {
            return;
        }
        game.getSentAgendas().remove(cardId);
    }

    private void removeEvent(Game game, Player player, String cardId) {
        if (game.getEvents().remove(cardId)) {
            return;
        }
        if (game.getDiscardedEvents().remove(cardId) != null) {
            return;
        }
        player.removeEvent(cardId);
    }

    private String normalizeCardId(String cardId) {
        return cardId.replace("extra1", "").replace("extra2", "");
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
