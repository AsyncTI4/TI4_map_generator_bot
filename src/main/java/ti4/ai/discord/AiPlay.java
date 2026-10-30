package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.selfplay.SelfPlaySetup;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Mapper;

class AiPlay extends Subcommand {

    private static final String OPPONENTS = "opponents";
    private static final String NAME_PREFIX = "aiplay";
    private static final int MIN_OPPONENTS = SelfPlaySetup.MIN_SEATS - 1;
    private static final int MAX_OPPONENTS = SelfPlaySetup.MAX_SEATS - 1;

    AiPlay() {
        super("play", "Developer: start a new game where you play against AI players in the other seats");
        addOptions(
                new OptionData(
                                OptionType.INTEGER,
                                OPPONENTS,
                                "Number of AI opponents, " + MIN_OPPONENTS + " to " + MAX_OPPONENTS + " (default "
                                        + MAX_OPPONENTS + ")")
                        .setRequiredRange(MIN_OPPONENTS, MAX_OPPONENTS),
                new OptionData(OptionType.STRING, Constants.FACTION, "Faction you play (default: a random one)")
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.COLOR, "Your color (default: your preferred color)")
                        .setAutoComplete(true),
                AiGameLauncher.gameNameOption(NAME_PREFIX));
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.developerRoles);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int opponents = event.getOption(OPPONENTS, MAX_OPPONENTS, OptionMapping::getAsInt);
        String requestedFaction = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        String faction = requestedFaction == null
                ? randomFaction()
                : AliasHandler.resolveFaction(requestedFaction.toLowerCase());
        String refusal = factionRefusal(faction);
        if (refusal != null) {
            MessageHelper.replyToMessage(event, refusal);
            return;
        }
        String requestedColor = event.getOption(Constants.COLOR, null, OptionMapping::getAsString);
        String color = requestedColor == null ? null : AliasHandler.resolveColor(requestedColor.toLowerCase());
        if (color != null && !Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "Color `" + requestedColor + "` is not valid.");
            return;
        }
        SelfPlaySetup.Table table = SelfPlaySetup.Table.withHuman(
                faction,
                color,
                SelfPlaySetup.pickOpponentFactions(opponents, faction, RandomHelper.current()),
                RandomHelper.current());
        AiGameLauncher.launch(
                        event,
                        NAME_PREFIX,
                        (name, owner, channels) -> SelfPlaySetup.setUp(name, owner, channels, table, false, event))
                .ifPresent(started -> MessageHelper.replyToMessage(
                        event, startedMessage(started.game(), started.actions(), started.mapUpdates(), faction)));
    }

    static String randomFaction() {
        List<String> factions = AiSettings.SUPPORTED_FACTIONS;
        return factions.get(RandomHelper.current().nextInt(factions.size()));
    }

    static String factionRefusal(String faction) {
        if (!Mapper.isValidFaction(faction)) return "`" + faction + "` is not a faction.";
        FactionModel model = Mapper.getFaction(faction);
        if (!model.getSource().isTe()) {
            return "AI games only use Base, Prophecy of Kings and Thunder's Edge factions, not " + faction + ".";
        }
        return null;
    }

    private static String startedMessage(Game game, TextChannel actions, ThreadChannel mapUpdates, String faction) {
        List<String> opponents = AiSeats.aiSeats(game).stream()
                .map(seat -> seat.getUserName() + (seat.isSpeaker() ? " (speaker)" : ""))
                .toList();
        return "Started **" + game.getName() + "**: you play `" + faction + "` against " + opponents.size()
                + " AI players: " + String.join(", ", opponents) + ". Play in " + actions.getAsMention()
                + "; the map is posted in " + mapUpdates.getAsMention() + ". Press **Deal 2 Secret Objectives** there"
                + " once you are ready, and use `/ai status` or `/ai pause` in the game's channel to check on the AIs"
                + " or stop them.";
    }
}
