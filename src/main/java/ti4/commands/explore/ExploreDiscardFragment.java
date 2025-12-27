package ti4.commands.explore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

class ExploreDiscardFragment extends GameStateSubcommand {

    private static final Map<String, String> TRAIT_TO_FRAGMENT_PREFIX = Map.of(
            "cultural", "crf",
            "industrial", "irf",
            "hazardous", "hrf",
            "frontier", "urf");

    ExploreDiscardFragment() {
        super("discard_fragment", "Discard a relic fragment.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TRAIT, "Type of fragment")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments to discard (default 1)")
                .setMinValue(1));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color (default you)")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String trait = event.getOption(Constants.TRAIT).getAsString();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        Player player = getPlayer();
        List<String> playerFragments = player.getFragments();

        var fragmentsToDiscard = new ArrayList<String>();
        for (int i = 0; i < playerFragments.size() && fragmentsToDiscard.size() < count; i++) {
            String fragment = playerFragments.get(i);
            if (fragment.startsWith(TRAIT_TO_FRAGMENT_PREFIX.get(trait))) {
                fragmentsToDiscard.add(fragment);
            }
        }

        if (fragmentsToDiscard.size() < count) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Could not find " + count + " fragments to discard from " + player.getFaction() + ".");
            return;
        }

        for (String fragmentToDiscard : fragmentsToDiscard) {
            player.removeFragment(fragmentToDiscard);
            getGame().discardExplore(fragmentToDiscard);
        }

        ExploreModel fragmentModel = Mapper.getExplore(fragmentsToDiscard.getFirst());
        String message = player.getRepresentation() + " discarded " + count + " " + fragmentModel.getName() + ".";
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
