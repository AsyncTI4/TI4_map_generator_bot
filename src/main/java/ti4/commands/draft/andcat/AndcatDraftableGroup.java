package ti4.commands.draft.andcat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;

public class AndcatDraftableGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new AndcatDraftableAddPackage(),
                    new AndcatDraftableModifyPackage(),
                    new AndcatDraftableRemovePackage(),
                    new AndcatDraftableSetPlayerChoices())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public AndcatDraftableGroup() {
        super(Constants.DRAFT_ANDCAT_REF_PACKAGES, "Commands for managing Andcat Reference Packages");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static AndcatReferenceCardsDraftable getDraftable(SlashCommandInteractionEvent event, Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Andcat Reference Packages aren't draftable; you may need `/draft manage add_draftable`.");
            return null;
        }
        return (AndcatReferenceCardsDraftable) draftManager.getDraftable(AndcatReferenceCardsDraftable.TYPE);
    }

    public static List<String> getFactionList(SlashCommandInteractionEvent event) {
        String faction1 = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        if (faction1 == null || Mapper.getFaction(faction1) == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid " + Constants.FACTION + ": " + faction1);
            return null;
        }
        String faction2 = event.getOption(Constants.FACTION2, null, OptionMapping::getAsString);
        if (faction2 == null || Mapper.getFaction(faction2) == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid " + Constants.FACTION2 + ": " + faction2);
            return null;
        }
        String faction3 = event.getOption(Constants.FACTION3, null, OptionMapping::getAsString);
        if (faction3 == null || Mapper.getFaction(faction3) == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid " + Constants.FACTION3 + ": " + faction3);
            return null;
        }
        return List.of(faction1, faction2, faction3);
    }
}
