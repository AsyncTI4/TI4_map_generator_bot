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

    private List<String> deckTypes;

    public SetDeck() {
        super(Constants.SET_DECK, "Change game decks");
        deckTypes = new ArrayList<>();
        addDefaultOption(Constants.AC_DECK, "AC");
        addDefaultOption(Constants.SO_DECK, "SO");
        addDefaultOption(Constants.STAGE_1_PUBLIC_DECK, "Stage 1 public");
        addDefaultOption(Constants.STAGE_2_PUBLIC_DECK, "Stage 2 public");
        addDefaultOption(Constants.RELIC_DECK, "Relic");
        addDefaultOption(Constants.AGENDA_DECK, "Agenda");
        addDefaultOption(Constants.EXPLORATION_DECKS, "Exploration");
        addDefaultOption(Constants.STRATEGY_CARD_SET, "Strategy card");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        java.util.Map<String, DeckModel> changedDecks = new HashMap<>();

        this.deckTypes.forEach(deckType -> {
            String value = event.getOption(deckType, null, OptionMapping::getAsString);
            if (Optional.ofNullable(value).isPresent()) {
                DeckModel deckModel = Mapper.getDecks().get(value);
                if(Optional.ofNullable(deckModel).isPresent()) {
                    switch (deckType) {
                        case Constants.AC_DECK -> activeMap.validateAndSetActionCardDeck(event, deckModel);
                        case Constants.SO_DECK -> activeMap.setSecretObjectives(deckModel.getShuffledCardList());
                        case Constants.STAGE_1_PUBLIC_DECK -> activeMap.setPublicObjectives1(new ArrayList<>(deckModel.getShuffledCardList()));
                        case Constants.STAGE_2_PUBLIC_DECK -> activeMap.setPublicObjectives2(new ArrayList<>(deckModel.getShuffledCardList()));
                        case Constants.RELIC_DECK -> activeMap.setRelics(new ArrayList<>(deckModel.getShuffledCardList()));
                        case Constants.AGENDA_DECK -> activeMap.validateAndSetAgendaDeck(event, deckModel);
                        case Constants.EXPLORATION_DECKS -> activeMap.setExploreDeck(new ArrayList<>(deckModel.getShuffledCardList()));
                    }
                    changedDecks.put(deckModel.getType(), deckModel);
                }
                else {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong, and the deck " + value + " could not be found, try executing the command again (without copy/pasting).");
                }
            }
        });

        if(CollectionUtils.isNotEmpty(changedDecks.keySet())) {
            List<String> changeMessage = new ArrayList<>();
            changedDecks.values().forEach(deck -> changeMessage.add(deck.getType() + " deck has been changed to:\n`" + deck.getAlias() +"`: " + deck.getName() + "\n> " + deck.getDescription()));
            MessageHelper.sendMessageToChannel(event.getChannel(), String.join("\n", changeMessage));
        }
    }

    private void addDefaultOption(String constantName, String descName) {
        addOptions(new OptionData(OptionType.STRING, constantName, descName + " deck").setRequired(false).setAutoComplete(true));
        this.deckTypes.add(constantName);
    }
}
