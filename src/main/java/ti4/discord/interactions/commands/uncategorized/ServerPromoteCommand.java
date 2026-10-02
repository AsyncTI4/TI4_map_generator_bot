package ti4.discord.interactions.commands.uncategorized;

import static java.util.Map.entry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ServerPromoteCommand implements ParentCommand {

    private static final String DEV_CHANNEL = "947520255826198549";
    public static final Map<String, String> servers = buildServers();

    private static Map<String, String> buildServers() {
        Map<String, String> servers = new HashMap<>(Constants.EMOJI_FARM_SERVERS);
        servers.putAll(Map.ofEntries(
                entry(Constants.ASYNCTI4_HUB_SERVER_ID, "Async Hub"),
                entry("1176104225932058694", "War Sun Tzu"),
                entry("1145823841227112598", "Dread Not!"),
                entry("1250131684393881610", "Tommer Hawk"),
                entry("1090910555327434774", "Stroter's Paradise"),
                entry("1209956332380229672", "Fighter Club")));
        return Map.copyOf(servers);
    }

    public static final Map<String, String> ranks = Map.of(
            "943596173896323072", "Admin",
            "947648366056185897", "Developer",
            "1166011604488425482", "Bothelper",
            "", "");

    @Override
    public String getName() {
        return Constants.SERVERPROMOTE;
    }

    @Override
    public String getDescription() {
        return "Server promotion";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!DEV_CHANNEL.equals(event.getChannelId())) {
            MessageHelper.replyToMessage(event, "This command can only be run in the `#development` channel.");
            return false;
        }

        OptionMapping targetOpt = event.getOption(Constants.PROMOTE_TARGET);
        if (targetOpt == null || !servers.containsKey(targetOpt.getAsString())) {
            MessageHelper.replyToMessage(event, "Server does not exist.");
            return false;
        }

        OptionMapping rankOpt = event.getOption(Constants.PROMOTE_RANK);
        if (rankOpt != null && !ranks.containsKey(rankOpt.getAsString())) {
            MessageHelper.replyToMessage(event, "Rank does not exist.");
            return false;
        }

        if (!servers.get(targetOpt.getAsString()).startsWith("Emoji Farm")) {
            if (rankOpt == null || rankOpt.getAsString().isEmpty()) {
                MessageHelper.replyToMessage(event, "Rank required (for non Emoji Farm servers).");
                return false;
            }
            Member member = event.getMember();
            boolean allowed = false;
            if ("admin".equalsIgnoreCase(ranks.get(rankOpt.getAsString()))) {
                for (Role r : member.getRoles()) {
                    if ("943596173896323072".equals(r.getId())) {
                        allowed = true;
                    }
                }
            } else if ("developer".equalsIgnoreCase(ranks.get(rankOpt.getAsString()))) {
                for (Role r : member.getRoles()) {
                    if ("943596173896323072".equals(r.getId()) || "947648366056185897".equals(r.getId())) {
                        allowed = true;
                    }
                }
            } else if ("bothelper".equalsIgnoreCase(ranks.get(rankOpt.getAsString()))) {
                for (Role r : member.getRoles()) {
                    if ("943596173896323072".equals(r.getId())
                            || "947648366056185897".equals(r.getId())
                            || "1166011604488425482".equals(r.getId())) {
                        allowed = true;
                    }
                }
            }

            if (!allowed) {
                MessageHelper.replyToMessage(event, "You cannot promote yourself above a rank you already have.");
                return false;
            }
        }

        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping targetOpt = event.getOption(Constants.PROMOTE_TARGET);
        OptionMapping rankOpt = event.getOption(Constants.PROMOTE_RANK);
        OptionMapping demoteOpt = event.getOption(Constants.PROMOTE_DEMOTE);

        String target = targetOpt.getAsString();
        String rank = rankOpt == null ? "" : rankOpt.getAsString();
        boolean demote = demoteOpt != null && demoteOpt.getAsBoolean();
        User user = event.getUser();

        MessageHelper.replyToMessage(
                event,
                (demote ? "Demoting" : "Promoting") + " " + user.getEffectiveName() + "; rank " + ranks.get(rank));

        if (target.startsWith("Emoji Farm")) {
            Guild guild = event.getJDA().getGuildById(Long.parseLong(target));
            guild.getRoles().forEach(r -> {
                if ("admin".equalsIgnoreCase(r.getName())) {
                    if (demote) {
                        guild.removeRoleFromMember(user, r);
                    } else {
                        guild.addRoleToMember(user, r);
                    }
                }
            });
        } else {
            Guild guild = event.getJDA().getGuildById(Long.parseLong(target));
            guild.getRoles().forEach(r -> {
                if (r.getName().equalsIgnoreCase(ranks.get(rank))) {
                    if (demote) {
                        guild.removeRoleFromMember(user, r);
                    } else {
                        guild.addRoleToMember(user, r);
                    }
                }
            });
        }
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.PROMOTE_TARGET, "Target Server")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.PROMOTE_RANK, "Rank").setAutoComplete(true),
                new OptionData(OptionType.BOOLEAN, Constants.PROMOTE_DEMOTE, "Demote").setAutoComplete(true));
    }
}
