package ti4.commands.relic;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

class RelicPurgeFragments extends GameStateSubcommand {

    public RelicPurgeFragments() {
        super(Constants.PURGE_FRAGMENTS, "Purge a number of relic fragments (for example, to gain a relic; may use unknown fragments).", true, true);
        addOptions(
            new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true).setRequired(true),
            new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments to purge (default 3, use this for NRA Fabrication or Black Market Forgery)."),
            new OptionData(OptionType.BOOLEAN, Constants.ALSO_DRAW_RELIC, "'true' to also draw a relic"),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player activePlayer = getPlayer();
        String color = event.getOption(Constants.TRAIT, null, OptionMapping::getAsString);
        int count = event.getOption(Constants.COUNT, 3, OptionMapping::getAsInt);

        List<String> fragmentsToPurge = new ArrayList<>();
        List<String> unknowns = new ArrayList<>();
        List<String> playerFragments = activePlayer.getFragments();
        for (String id : playerFragments) {
            ExploreModel explore = Mapper.getExplore(id);
            if (explore.getType().equalsIgnoreCase(color)) {
                fragmentsToPurge.add(id);
            } else if (explore.getType().equalsIgnoreCase(Constants.FRONTIER)) {
                unknowns.add(id);
            }
        }

        while (fragmentsToPurge.size() > count) {
            fragmentsToPurge.removeFirst();
        }

        while (fragmentsToPurge.size() < count) {
            if (unknowns.isEmpty()) {
                MessageHelper.sendMessageToEventChannel(event, "Not enough fragments. Note that default count is 3.");
                return;
            }
            fragmentsToPurge.add(unknowns.removeFirst());
        }

        Game game = getGame();
        String message = activePlayer.getRepresentation() + " purged ";
        if (fragmentsToPurge.size() == 1) {
            String fragid = fragmentsToPurge.get(0);
            activePlayer.removeFragment(fragid);
            game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
            switch (fragid) {
                case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" -> message += "a " + ExploreEmojis.CFrag + "cultural";
                case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" -> message += "a " + ExploreEmojis.HFrag + "hazardous";
                case "irf1", "irf2", "irf3", "irf4", "irf5" -> message += "an " + ExploreEmojis.IFrag + "industrial";
                case "urf1", "urf2", "urf3" -> message += "an " + ExploreEmojis.UFrag + "unknown";
                default -> message += " " + fragid;
            }
            message += " relic fragment.";
        } else {
            for (String fragid : fragmentsToPurge) {
                activePlayer.removeFragment(fragid);
                game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                switch (fragid) {
                    case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" -> message += ExploreEmojis.CFrag;
                    case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" -> message += ExploreEmojis.HFrag;
                    case "irf1", "irf2", "irf3", "irf4", "irf5" -> message += ExploreEmojis.IFrag;
                    case "urf1", "urf2", "urf3" -> message += ExploreEmojis.UFrag;
                    default -> message += " " + fragid;
                }
            }
            message += " relic fragments.";
        }
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "lanefir");
        MessageHelper.sendMessageToEventChannel(event, message);

        if (activePlayer.hasTech("dslaner")) {
            activePlayer.setAtsCount(activePlayer.getAtsCount() + 1);
            MessageHelper.sendMessageToEventChannel(event, activePlayer.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
        }

        boolean drawRelic = event.getOption(Constants.ALSO_DRAW_RELIC, false, OptionMapping::getAsBoolean);
        if (drawRelic) {
            RelicHelper.drawRelicAndNotify(activePlayer, event, game);
        }
    }

}
