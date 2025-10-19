package ti4.commands.draft.mantistile;

import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.draftables.MantisTileDraftable;

class MantisTileDraftableConfigure extends GameStateSubcommand {
    protected MantisTileDraftableConfigure() {
        super(Constants.DRAFT_MANTIS_TILE_CONFIGURE, "Configure the mantis tile draftable", true, false);
        addOption(OptionType.INTEGER, "mulligans", "Number of mulligans during map build", false);
        addOption(OptionType.INTEGER, "extra_blues", "Number of extra blue tiles during map build", false);
        addOption(OptionType.INTEGER, "extra_reds", "Number of extra red tiles during map build", false);
        addOption(OptionType.STRING, "mulliganed_tiles", "Which tiles have been mulliganed (comma-separated)", false);
        addOption(OptionType.STRING, "discarded_tiles", "Which tiles have been discarded (comma-separated)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MantisTileDraftable draftable = MantisTileDraftableGroup.getDraftable(getGame());
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Mantis tile isn't draftable; you may need `/draft manage add_draftable MantisTile`.");
            return;
        }
        if (event.getOption("mulligans") != null) {
            draftable.setMulligans(event.getOption("mulligans").getAsInt());
        }
        if (event.getOption("extra_blues") != null) {
            draftable.setExtraBlues(event.getOption("extra_blues").getAsInt());
        }
        if (event.getOption("extra_reds") != null) {
            draftable.setExtraReds(event.getOption("extra_reds").getAsInt());
        }
        if (event.getOption("mulliganed_tiles") != null) {
            String commaSeparatedTiles = event.getOption("mulliganed_tiles").getAsString();
            draftable.getMulliganTileIDs().clear();
            if (commaSeparatedTiles != null && commaSeparatedTiles.trim().isEmpty()) {
                draftable
                        .getMulliganTileIDs()
                        .addAll(List.of(commaSeparatedTiles.split(",")).stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList()));
            }
        }
        if (event.getOption("discarded_tiles") != null) {
            String commaSeparatedTiles = event.getOption("discarded_tiles").getAsString();
            draftable.getDiscardedTileIDs().clear();
            if (commaSeparatedTiles != null && !commaSeparatedTiles.trim().isEmpty()) {
                draftable
                        .getDiscardedTileIDs()
                        .addAll(List.of(commaSeparatedTiles.split(",")).stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList()));
            }
        }
    }
}
