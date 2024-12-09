package ti4.commands2.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.collections4.CollectionUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;
import ti4.service.game.SetDeckService;

class SetDeck extends GameStateSubcommand {

    private final List<String> deckTypes;

    public SetDeck() {
        super(Constants.SET_DECK, "Change game card decks", true, false);
        deckTypes = new ArrayList<>();
        addDefaultOption(Constants.AC_DECK, "AC");
        addDefaultOption(Constants.SO_DECK, "SO");
        addDefaultOption(Constants.STAGE_1_PUBLIC_DECK, "Stage 1 public");
        addDefaultOption(Constants.STAGE_2_PUBLIC_DECK, "Stage 2 public");
        addDefaultOption(Constants.RELIC_DECK, "Relic");
        addDefaultOption(Constants.AGENDA_DECK, "Agenda");
        addDefaultOption(Constants.EVENT_DECK, "Event");
        addDefaultOption(Constants.EXPLORATION_DECKS, "Exploration");
        addDefaultOption(Constants.STRATEGY_CARD_SET, "Strategy card");
        addDefaultOption(Constants.TECHNOLOGY_DECK, "Technology");
        addOptions(new OptionData(OptionType.BOOLEAN, "reset_deck", "True to completely reset deck"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Map<DeckModel.DeckType, DeckModel> changedDecks = new HashMap<>();

        deckTypes.forEach(deckType -> {
            String value = event.getOption(deckType, null, OptionMapping::getAsString);
            if (Optional.ofNullable(value).isPresent()) {
                if (deckType.equals(Constants.STRATEGY_CARD_SET)) {
                    game.setStrategyCardSet(value);
                } else {
                    DeckModel deckModel = Mapper.getDecks().get(value);
                    if (SetDeckService.setDeck(event, game, deckType, deckModel)) {
                        changedDecks.put(deckModel.getType(), deckModel);
                    } else {
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "Something went wrong and the deck ***" + value + "*** could not be set, please see error above or try executing the command again (without copy/pasting).");
                    }
                }
                if (deckType.equals(Constants.TECHNOLOGY_DECK)) {
                    game.swapOutVariantTechs();
                    game.swapInVariantTechs();
                }
            }
        });

        if (CollectionUtils.isNotEmpty(changedDecks.keySet())) {
            List<String> changeMessage = new ArrayList<>();
            changedDecks.values().forEach(deck -> changeMessage.add(deck.getType() + " deck has been changed to:\n`" + deck.getAlias() + "`: " + deck.getName() + "\n>>> " + deck.getDescription()));
            MessageHelper.sendMessageToChannel(event.getChannel(), String.join("\n", changeMessage));
        }
    }

    private void addDefaultOption(String constantName, String descName) {
        addOptions(new OptionData(OptionType.STRING, constantName, descName + " deck").setAutoComplete(true));
        deckTypes.add(constantName);
    }
}
