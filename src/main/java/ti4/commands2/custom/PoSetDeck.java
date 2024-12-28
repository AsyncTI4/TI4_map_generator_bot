package ti4.commands2.custom;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class PoSetDeck extends GameStateSubcommand {

    public PoSetDeck() {
        super(Constants.SET_PO_DECK, "Create fixed order public objective deck from specific public objectives", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.SET_PO_DECK_STAGE1_LIST, "ID list separated by comma"));
        addOptions(new OptionData(OptionType.STRING, Constants.SET_PO_DECK_STAGE2_LIST, "ID list separated by comma"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SET_PO_DECK_SHUFFLE, "Shuffle the deck after creation (default: false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String stage1Ids = event.getOption(Constants.SET_PO_DECK_STAGE1_LIST, null, OptionMapping::getAsString);
        String stage2Ids = event.getOption(Constants.SET_PO_DECK_STAGE2_LIST, null, OptionMapping::getAsString);
        boolean shuffle = event.getOption(Constants.SET_PO_DECK_SHUFFLE, false, OptionMapping::getAsBoolean);

        List<String> stage1IdList = parseIds(stage1Ids);
        List<String> stage2IdList = parseIds(stage2Ids);

        if (!validateIds(stage1IdList, event) || !validateIds(stage2IdList, event)) {
            return;
        }

        if (!stage1IdList.isEmpty()) {
            if (shuffle) {
                Collections.shuffle(stage1IdList);
            }
            getGame().setPublicObjectives1(stage1IdList);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective stage 1 deck created.");
        } 

        if (!stage2IdList.isEmpty()) {
            if (shuffle) {
                Collections.shuffle(stage2IdList);
            }
            getGame().setPublicObjectives2(stage2IdList);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective stage 2 deck created.");
        } 

        if (stage1IdList.isEmpty() && stage2IdList.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), 
                "Public objective stage 1 deck: " + getGame().getPublicObjectives1() + "\n"
              + "Public objective stage 2 deck: " + getGame().getPublicObjectives2());  
        }
    }

    private List<String> parseIds(String idList) {
        return idList == null ? 
            Collections.emptyList() : 
            Arrays.stream(idList.split(","))
                  .map(String::trim)
                  .collect(Collectors.toList());
    }

    private boolean validateIds(List<String> ids, SlashCommandInteractionEvent event) {
        for (String id : ids) {
            if (!Mapper.getPublicObjectives().containsKey(id)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid public objective id: " + id);
                return false;
            }
        }
        return true;
    }
}
