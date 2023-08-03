package ti4.commands.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.collections4.CollectionUtils;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;

public class SetDeck extends GameSubcommandData {

    public SetDeck() {
        super(Constants.SET_DECK, "Replace a deck with a new deck");
        addOptions(new OptionData(OptionType.STRING, Constants.AC_DECK, "Name of the action card deck to set").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SO_DECK, "Name of the secret objective deck to set").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.STAGE_1_PUBLIC_DECK, "Name of the stage 1 public deck to set").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.STAGE_2_PUBLIC_DECK, "Name of the stage 2 public deck to set").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC_DECK, "Name of the relic deck to set").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.AGENDA_DECK, "Name of the agenda deck to set").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.EXPLORATION_DECKS, "Name of the exploration deck to set").setRequired(false).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.STRATEGY_CARD_SET, "Name of the strategy card selection to set").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        java.util.Map<String, String> deckValues = new HashMap<>();
        deckValues.put("acDeck", event.getOption(Constants.AC_DECK, null, OptionMapping::getAsString));
        deckValues.put("soDeck", event.getOption(Constants.SO_DECK, null, OptionMapping::getAsString));
        deckValues.put("stage1Publics", event.getOption(Constants.STAGE_1_PUBLIC_DECK, null, OptionMapping::getAsString));
        deckValues.put("stage2Publics", event.getOption(Constants.STAGE_2_PUBLIC_DECK, null, OptionMapping::getAsString));
        deckValues.put("relicDeck", event.getOption(Constants.RELIC_DECK, null, OptionMapping::getAsString));
        deckValues.put("agendaDeck", event.getOption(Constants.AGENDA_DECK, null, OptionMapping::getAsString));
        deckValues.put("exploreDecks", event.getOption(Constants.EXPLORATION_DECKS, null, OptionMapping::getAsString));
        deckValues.put("stratCards", event.getOption(Constants.STRATEGY_CARD_SET, null, OptionMapping::getAsString));

        java.util.Map<String, DeckModel> changedDecks = new HashMap<>();

        deckValues.forEach((key, value) -> {
            if (Optional.ofNullable(value).isPresent()) {
                DeckModel deckModel = Mapper.getDecks().get(value);
                switch (key){
                    case "acDeck" -> validateAndSetActionCardDeck(event, activeMap, deckModel);
                    case ""
                }
                validateAndSetActionCardDeck(event, activeMap, deckModel);
                changedDecks.put(deckModel.getType(), deckModel);
            }
        });
        if(Optional.ofNullable(acDeck).isPresent()) {
            DeckModel deck = Mapper.getDecks().get(acDeck);
            validateAndSetActionCardDeck(event, activeMap, deck);
            changedDecks.put(deck.getType(), deck);
        }
        if(Optional.ofNullable(soDeck).isPresent()) {
            DeckModel deck = Mapper.getDecks().get(soDeck);
            activeMap.setSecretObjectives(deck.getShuffledCardList());
            changedDecks.put(deck.getType(), deck);
        }
        if(Optional.ofNullable(stage1Publics).isPresent()) {
            DeckModel deck = Mapper.getDecks().get(stage1Publics);
            activeMap.setPublicObjectives1(new ArrayList<>(deck.getShuffledCardList()));
            changedDecks.put(deck.getType(), deck);
        }
        if(Optional.ofNullable(stage2Publics).isPresent()) {
            DeckModel deck = Mapper.getDecks().get(stage2Publics);
            activeMap.setPublicObjectives2(new ArrayList<>(deck.getShuffledCardList()));
            changedDecks.put(deck.getType(), deck);
        }
        if(Optional.ofNullable(relicDeck).isPresent()) {
            DeckModel deck = Mapper.getDecks().get(relicDeck);
            activeMap.setRelics(new ArrayList<>(deck.getShuffledCardList()));
            changedDecks.put(deck.getType(), deck);
        }
        if(Optional.ofNullable(agendaDeck).isPresent()) {
            DeckModel deck = Mapper.getDecks().get(agendaDeck);
            validateAndSetAgendaDeck(event, activeMap, deck);
            changedDecks.put(deck.getType(), deck);
        }
        if(Optional.ofNullable(exploreDecks).isPresent()) {
            DeckModel deck = Mapper.getDecks().get(exploreDecks);
            activeMap.setExploreDeck(new ArrayList<>(deck.getShuffledCardList()));
            changedDecks.put(deck.getType(), deck);
        }
        //if(Optional.ofNullable(stratCards).isPresent()) {}

        if(CollectionUtils.isNotEmpty(changedDecks.keySet())) {
            List<String> changeMessage = new ArrayList<>();
            changedDecks.values().forEach(deck -> changeMessage.add(deck.getType() + " deck has been changed to:\n`" + deck.getAlias() +"`: " + deck.getName() + "\n> " + deck.getDescription()));
            MessageHelper.sendMessageToChannel(event.getChannel(), String.join("\n", changeMessage));
        }
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
