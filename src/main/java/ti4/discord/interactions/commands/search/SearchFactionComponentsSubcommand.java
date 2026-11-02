package ti4.discord.interactions.commands.search;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.draft.DraftItem;
import ti4.draft.items.FactionDraftItem;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.Mapper;
import ti4.service.franken.FrankenDraftBagService;

class SearchFactionComponentsSubcommand extends Subcommand {

    SearchFactionComponentsSubcommand() {
        super(Constants.SEARCH_FACTION_COMPONENTS, "Show all of a faction's components");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String faction = event.getOption(Constants.FACTION, OptionMapping::getAsString);
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(event, "That is not a valid faction.");
            return;
        }

        FactionDraftItem item = new FactionDraftItem(faction);
        List<DraftItem> components = item.getAllComponentsIgnoringErrata();
        if (components.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "## " + item.getTitle(null) + " Components\nNone.");
            return;
        }

        List<Color> accents = FrankenDraftBagService.getAccents();
        MessageV2Builder builder = new MessageV2Builder(event.getMessageChannel());
        builder.append("## " + item.getTitle(null) + " Components");
        for (DraftItem component : components) {
            List<ContainerChildComponent> cardComponents = new ArrayList<>(component.getPlainTextDisplays(null));
            builder.append(Container.of(cardComponents).withAccentColor(accents.getFirst()));
            Collections.rotate(accents, -1);
        }
        builder.send();
    }
}
