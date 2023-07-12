package ti4.commands.game;

import java.util.ArrayList;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;

public class SetDeck extends GameSubcommandData {

    public SetDeck() {
        super(Constants.SET_DECK, "Replace a deck with a new deck");
        addOptions(new OptionData(OptionType.STRING, Constants.DECK_NAME, "Name of the Deck to set").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        String deckID = event.getOption(Constants.DECK_NAME, null, OptionMapping::getAsString);

        if (deckID == null || !Mapper.getDecks().containsKey(deckID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No valid deck was provided."); // Please see `/help list_decks` for available choices.");
            return;
        }



        DeckModel deck = Mapper.getDecks().get(deckID);
        switch (deck.getType()) {
            case "action_card" -> validateAndSetActionCardDeck(event, activeMap, deck);
            case "agenda" -> validateAndSetAgendaDeck(event, activeMap, deck);
            case "secret_objective" -> activeMap.setSecretObjectives(deck.getShuffledCardList());
            case "public_stage_1_objective" -> activeMap.setPublicObjectives1(new ArrayList<>(deck.getShuffledCardList()));
            case "public_stage_2_objective" -> activeMap.setPublicObjectives2(new ArrayList<>(deck.getShuffledCardList()));
            case "relic" -> activeMap.setRelics(new ArrayList<>(deck.getShuffledCardList()));
            case "explore" -> activeMap.setExploreDeck(new ArrayList<>(deck.getShuffledCardList()));
        }
        String message = deck.getType() + " deck has been changed to:\n`" + deck.getAlias() +"`: " + deck.getName() + "\n> " + deck.getDescription();
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
    }

    private void validateAndSetActionCardDeck(SlashCommandInteractionEvent event, Map activeMap, DeckModel deck) {
        if (activeMap.getDiscardActionCards().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change action card deck while there are action cards in the discard pile.");
            return;
        }
        for (Player player : activeMap.getPlayers().values()) {
            if (player.getActionCards().size() > 0) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change action card deck while there are action cards in player hands.");
                return;
            }
        }
        activeMap.setActionCards(deck.getShuffledCardList());
    }

    private void validateAndSetAgendaDeck(SlashCommandInteractionEvent event, Map activeMap, DeckModel deck) {
        if (activeMap.getDiscardAgendas().size() > 0) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Cannot change agenda deck while there are agendas in the discard pile.");
            return;
        }
        activeMap.setAgendas(deck.getShuffledCardList());
    }


}
