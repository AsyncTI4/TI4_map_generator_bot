package ti4.contest.replay.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Service;
import ti4.ResourceHelper;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayLeaderboardEntryEntity;
import ti4.contest.replay.repository.CombatReplayLeaderboardEntryRepository;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.MapGenerator;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.image.FileUploadService;

@Service
@RequiredArgsConstructor
public class LazaxSeasonService {

    private static final String PUBLIC_CHANNEL_NAME = "lazax-war-archives";
    public static final String CLAIM_DELEGATION_BUTTON_ID = "lazaxSeason1ClaimDelegation";

    private final CombatContestSettings settings;
    private final CombatReplayLeaderboardEntryRepository leaderboardEntryRepository;
    private final CombatReplayHouseService houseService;
    private final CombatReplayHouseLedgerService houseLedgerService;

    public boolean postSeasonOneOpeningMessage() {
        TextChannel channel = publicChannel();
        if (channel == null) return false;

        resetSeasonOneScores();
        houseService.assignLeaderboardEntriesRandomly(JdaService.guildPrimary, leaderboardEntryRepository.findAll());
        houseLedgerService.resetSeasonOpeningBalances();

        postSeasonOneOpeningMessage(channel);
        postDelegationSummaries();
        return true;
    }

    public boolean postSeasonOnePublicOpeningMessage() {
        TextChannel channel = publicChannel();
        if (channel == null) return false;

        postSeasonOneOpeningMessage(channel);
        return true;
    }

    private void postSeasonOneOpeningMessage(TextChannel channel) {
        FileUpload banner = seasonOneBanner();
        if (banner == null) {
            postSeasonOneOpeningText(channel);
            return;
        }
        MessageHelper.sendFileUploadToChannel(channel, banner, ignored -> postSeasonOneOpeningText(channel));
    }

    private void resetSeasonOneScores() {
        LocalDateTime now = LocalDateTime.now();
        int initialIndividualPoints = settings.getHouseAbilities().getInitialIndividualPoints();
        List<CombatReplayLeaderboardEntryEntity> entries = leaderboardEntryRepository.findAll();
        for (CombatReplayLeaderboardEntryEntity entry : entries) {
            entry.setTotalPoints(initialIndividualPoints);
            entry.setPredictionCount(0);
            entry.setCorrectPredictions(0);
            entry.setUpdatedAt(now);
        }
        leaderboardEntryRepository.saveAll(entries);
    }

    public String claimDelegation(User user, Member member, Guild guild) {
        if (!settings.isHousesEnabled()) return "Delegations are not enabled.";
        if (user == null || user.isBot()) return "Could not identify your delegation claim.";

        CombatReplayHouse existingHouse = houseService.houseForUser(user.getId());
        if (existingHouse != null) {
            houseService.assignHouseIfAbsent(guild, member, user);
            return "You are already assigned to **"
                    + existingHouse.displayName()
                    + " Delegation**. Report to "
                    + delegationChannelMention(guild, existingHouse)
                    + ".";
        }

        var assignment = houseService.assignHouseIfAbsent(guild, member, user);
        if (assignment == null || assignment.getHouse() == null) {
            return "Could not assign your delegation. Please try again.";
        }

        return "You have been assigned to **"
                + assignment.getHouse().displayName()
                + " Delegation**. Report to "
                + delegationChannelMention(guild, assignment.getHouse())
                + ".";
    }

    private String delegationChannelMention(Guild guild, CombatReplayHouse house) {
        TextChannel channel = houseChannel(guild, house);
        return channel == null ? "`#" + house.channelName() + "`" : channel.getAsMention();
    }

    private TextChannel publicChannel() {
        if (JdaService.guildPrimary == null) return null;
        List<TextChannel> channels = JdaService.guildPrimary.getTextChannelsByName(PUBLIC_CHANNEL_NAME, true);
        return channels.isEmpty() ? null : channels.get(0);
    }

    private TextChannel houseChannel(CombatReplayHouse house) {
        return houseChannel(JdaService.guildPrimary, house);
    }

    private TextChannel houseChannel(Guild guild, CombatReplayHouse house) {
        if (guild == null || house == null) return null;
        List<TextChannel> channels = guild.getTextChannelsByName(house.channelName(), true);
        return channels.isEmpty() ? null : channels.get(0);
    }

    private List<MessageEmbed> seasonOneOpeningEmbeds() {
        return List.of(delegationEmbed(), rulesEmbed());
    }

    private void postSeasonOneOpeningText(TextChannel channel) {
        MessageHelper.sendMessageToChannel(channel, seasonOneIntroMarkdown());
        channel.sendMessageEmbeds(seasonOneOpeningEmbeds())
                .setComponents(ActionRow.of(Buttons.green(CLAIM_DELEGATION_BUTTON_ID, "Receive Your Delegation")))
                .queue(this::postFaqThread, BotLogger::catchRestError);
    }

    private void postFaqThread(net.dv8tion.jda.api.entities.Message openingPost) {
        openingPost
                .createThreadChannel("Lazax Season 1 FAQ")
                .setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS)
                .queue(
                        thread -> MessageHelper.sendMessageToChannel(thread, seasonOneFaqMarkdown()),
                        BotLogger::catchRestError);
    }

    private String seasonOneFaqMarkdown() {
        return """
        # Lazax Season 1 FAQ

        ### Does everyone still vote individually, and are personal points still tracked?
        Yes. Everyone votes individually and earns individual points. Delegation points are the primary season standings, but personal points are still tracked and can be checked with the points command.

        ### Are Delegation points public?
        Yes. Delegation points are public in the same way the current leaderboard is public.

        ### Will everyone know who belongs to each delegation?
        Yes. Delegation roles are public, and faction emojis are appended to people's messages in the relevant channels.

        ### Will everyone see everyone's votes?
        Yes. Prediction and side bet behavior is visible enough that players can infer patterns. If delegation powers create a metagame around following votes, that is part of the intended chaos.

        ### How is an active ability chosen?
        Delegates vote during that delegation's ability window. Each ability requires at least `3` distinct voters to resolve; otherwise no option wins. If the threshold is met, the highest-voted option is chosen.
        """.strip();
    }

    private String seasonOneIntroMarkdown() {
        return """
        # Season 1: The Council Wages on War

        Mecatol Rex stands quiet beneath a thousand watching eyes. The Lazax are gone, but their throne remains, and the Winnaran Custodians still keep their ancient charge.

        They do not seek the strongest warlord. The galaxy has no shortage of those. They seek one who can understand war well enough to prevent it. One who can read the shape of conflict before it unfolds and steer the fate of fleets with judgment alone.

        To find them, the Custodians have opened the **War Ledger**.

        Each battle brought before the Council is a test. Delegations place their wagers before the first shot is fired, committing their insight, their nerve, and their claim to understanding power. Victory is not enough. Many can win a battle. Few can see it coming.

        And yet, the Council is no stranger to intrigue. Will every wager be placed in honest judgment, or will quieter forces move behind the chamber doors, shaping outcomes before the galaxy ever sees the first volley?

        Those who prove they can see clearly will rise above the rest.

        From them, one will be chosen to stand above the chaos, the mind capable of holding the galaxy back from falling into war once more.
        """.strip();
    }

    private FileUpload seasonOneBanner() {
        BufferedImage banner = new BufferedImage(1400, 430, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = banner.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setPaint(new GradientPaint(0, 0, new Color(9, 13, 28), 1400, 430, new Color(77, 53, 102)));
        g.fillRect(0, 0, 1400, 430);

        drawStars(g);
        drawLazaxWatermark(g);
        drawSeasonBannerText(g);
        drawDelegationIcons(g);

        g.dispose();
        return FileUploadService.createFileUpload(banner, "lazax_season_1_war_ledger", "png")
                .setDescription("Lazax Season 1: The Council Wagers on War");
    }

    private void postDelegationSummaries() {
        for (DelegationSummary summary : delegationSummaries()) {
            TextChannel channel = houseChannel(summary.house());
            if (channel == null) continue;

            FileUpload banner = delegationBanner(summary);
            if (banner == null) {
                MessageHelper.sendMessageToChannel(channel, delegationSummaryMarkdown(summary, channel.getGuild()));
                continue;
            }
            MessageHelper.sendFileUploadToChannel(
                    channel,
                    banner,
                    ignored -> MessageHelper.sendMessageToChannel(
                            channel, delegationSummaryMarkdown(summary, channel.getGuild())));
        }
    }

    private List<DelegationSummary> delegationSummaries() {
        return List.of(
                new DelegationSummary(
                        CombatReplayHouse.NAALU,
                        "The Naalu Delegation",
                        "You have the power to see the future.",
                        "Power of Prophecy",
                        "Every correct prediction made by a Naalu delegate earns `+4` points for your delegation.",
                        "Gift of Foresight",
                        """
                        During each combat round's discussion, vote on whether to spend `Favor` to view the action cards played or first round rolls of this combat.

                        The winning vote resolves when discussion ends. Predictions and side bets remain open afterward, so the Delegation can still act on what it learns.
                        """,
                        "",
                        """
                        Use your ability to see the future on key combats that are close, or combats where future information can clarify what side bets are going to land.

                        Hacan can get you more Favor, which means more chances to use your visions in the combats that matter. Consider selling information to Hacan in exchange for Trade Convoys, but do so strategically: the more valuable your information is, the more they will want a piece of your next success.
                        """,
                        new Color(58, 151, 163),
                        "naalu.png"),
                new DelegationSummary(
                        CombatReplayHouse.MENTAK,
                        "The Mentak Delegation",
                        "You have the power of subterfuge.",
                        "Pillage",
                        "Each *incorrect* prediction made by players in opposing delegations earns you `+4` points.",
                        "False Colors",
                        """
                        Spend `Favor` to deploy decoy units into a combat. Decoy units appear as real units to all other participants, but always miss, are never assigned hits, and mysteriously disappear at the end of combat.

                        The cost of deploying decoys scales with the strength of the unit (you can deploy war suns!)
                        """,
                        "",
                        """
                        *Your best weapon is bad confidence*. Choose fights strategically in which deploying a decoy meaningfully misleads people, convincing them to bet the wrong horse and raking in large amounts of points in the process.

                        Due to the nature of your abilities, you get to deploy them less often than the other factions. The Mentak Delegation is defined by explosive turns of point gain, rather than a consistent trickle.

                        Hacan can get you more Favor, which means more chances to fly false colors when the table least wants certainty. Consider selling information or coordination to Hacan in exchange for Trade Convoys, but do so strategically: they profit when your next combat pays off.
                        """,
                        new Color(183, 103, 53),
                        "mentak.png"),
                new DelegationSummary(
                        CombatReplayHouse.HACAN,
                        "The Hacan Delegation",
                        "You are the market.",
                        "Insider Trading",
                        "Side bets are free and do not cost a point.",
                        "Market Compact and Hacan Trade Convoys",
                        """
                        **Market Compact**
                        Mark up to 2 side bets during discussion. Hacan gains `+1 point` for each non-Hacan player who takes this side bet. If a marked side bet hits, the Hacan also gain `+10 Favor`.

                        **Hacan Trade Convoys**
                        At the end of a combat, send `10`, `20`, or `30` favor to one of the other delegations. If you do so, get `5/10/15%` of their earned points in the next combat as a bonus.
                        """,
                        "",
                        """
                        Choose side bets that are likely to land, then steer others toward those same bets. You score from each non-Hacan player who follows the market, and the fact that you take a cut is not fully public knowledge. Use that ambiguity.

                        The other Delegations have abilities you want access to. Trade Convoys is not just a point engine; it is leverage. Offer Favor to fund Naalu information or Mentak disruption, then ask for information, commitments, or coordination in return.

                        Play both sides. Your power is not choosing the winning faction. Your power is making every faction worth doing business with.
                        """,
                        new Color(221, 176, 60),
                        "hacan.png"));
    }

    private FileUpload delegationBanner(DelegationSummary summary) {
        BufferedImage banner = new BufferedImage(1120, 260, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = banner.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Color accent = summary.color();
        g.setPaint(new GradientPaint(0, 0, new Color(10, 13, 25), 1120, 260, darken(accent)));
        g.fillRect(0, 0, 1120, 260);
        drawStars(g, 1120, 260);
        drawDelegationBannerText(g, summary);
        drawDelegationBannerIcon(g, summary);

        g.dispose();
        return FileUploadService.createFileUpload(
                        banner,
                        "lazax_season_1_" + summary.house().displayName().toLowerCase() + "_delegation",
                        "png")
                .setDescription(summary.title());
    }

    private void drawDelegationBannerText(Graphics2D g, DelegationSummary summary) {
        g.setFont(font(Storage.getFont50(), 50));
        DrawingUtil.superDrawString(
                g,
                summary.title().toUpperCase(),
                78,
                104,
                new Color(247, 234, 190),
                MapGenerator.HorizontalAlign.Left,
                MapGenerator.VerticalAlign.Center,
                new BasicStroke(4),
                new Color(0, 0, 0, 190));

        g.setFont(font(Storage.getFont24(), 24));
        DrawingUtil.superDrawString(
                g,
                summary.house().displayName().toUpperCase() + " EYES ONLY",
                82,
                154,
                summary.color(),
                MapGenerator.HorizontalAlign.Left,
                MapGenerator.VerticalAlign.Center,
                new BasicStroke(2),
                new Color(0, 0, 0, 170));
    }

    private void drawDelegationBannerIcon(Graphics2D g, DelegationSummary summary) {
        BufferedImage icon =
                ImageHelper.readScaled(ResourceHelper.getInstance().getFactionFile(summary.iconFile()), 130, 130);
        if (icon == null) return;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillOval(875, 47, 166, 166);
        g.setColor(summary.color());
        g.setStroke(new BasicStroke(6));
        g.drawOval(875, 47, 166, 166);
        g.drawImage(icon, 893, 65, null);
    }

    private void drawStars(Graphics2D g, int width, int height) {
        g.setColor(new Color(255, 255, 255, 45));
        for (int i = 0; i < 80; i++) {
            int x = (i * 89) % width;
            int y = (i * 47) % height;
            int size = i % 8 == 0 ? 3 : 2;
            g.fillOval(x, y, size, size);
        }
    }

    private Color darken(Color color) {
        return new Color(color.getRed() / 3, color.getGreen() / 3, color.getBlue() / 3);
    }

    private void drawStars(Graphics2D g) {
        drawStars(g, 1400, 430);
        g.setColor(new Color(214, 174, 82, 85));
        g.setStroke(new BasicStroke(2));
        g.drawLine(115, 82, 1285, 82);
        g.drawLine(115, 348, 1285, 348);
    }

    private void drawLazaxWatermark(Graphics2D g) {
        BufferedImage lazax =
                ImageHelper.readScaled(ResourceHelper.getInstance().getFactionFile("lazax.png"), 260, 260);
        if (lazax == null) return;
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.22f));
        g.drawImage(lazax, 570, 86, null);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
    }

    private void drawSeasonBannerText(Graphics2D g) {
        g.setFont(font(Storage.getFont80(), 80));
        DrawingUtil.superDrawString(
                g,
                "LAZAX SEASON 1",
                700,
                165,
                new Color(245, 230, 185),
                MapGenerator.HorizontalAlign.Center,
                MapGenerator.VerticalAlign.Center,
                new BasicStroke(5),
                new Color(0, 0, 0, 190));

        g.setFont(font(Storage.getFont50(), 50));
        DrawingUtil.superDrawString(
                g,
                "THE COUNCIL WAGERS ON WAR",
                700,
                238,
                Color.WHITE,
                MapGenerator.HorizontalAlign.Center,
                MapGenerator.VerticalAlign.Center,
                new BasicStroke(4),
                new Color(0, 0, 0, 210));

        g.setFont(font(Storage.getFont24(), 24));
        DrawingUtil.superDrawString(
                g,
                "BY CUSTODIAN DECREE OF MECATOL REX",
                700,
                282,
                new Color(213, 184, 110),
                MapGenerator.HorizontalAlign.Center,
                MapGenerator.VerticalAlign.Center,
                new BasicStroke(2),
                new Color(0, 0, 0, 180));
    }

    private void drawDelegationIcons(Graphics2D g) {
        drawFactionIcon(g, "naalu.png", 220, 356, new Color(80, 159, 168));
        drawFactionIcon(g, "mentak.png", 700, 356, new Color(191, 112, 58));
        drawFactionIcon(g, "hacan.png", 1180, 356, new Color(226, 179, 62));
    }

    private void drawFactionIcon(Graphics2D g, String filename, int x, int y, Color ringColor) {
        BufferedImage icon = ImageHelper.readScaled(ResourceHelper.getInstance().getFactionFile(filename), 82, 82);
        if (icon == null) return;
        g.setColor(new Color(0, 0, 0, 110));
        g.fillOval(x - 50, y - 50, 100, 100);
        g.setColor(ringColor);
        g.setStroke(new BasicStroke(4));
        g.drawOval(x - 50, y - 50, 100, 100);
        g.drawImage(icon, x - 41, y - 41, null);
    }

    private java.awt.Font font(java.awt.Font preferred, int fallbackSize) {
        if (preferred != null) return preferred;
        return new java.awt.Font("Serif", java.awt.Font.BOLD, fallbackSize);
    }

    private MessageEmbed delegationEmbed() {
        return new EmbedBuilder()
                .setTitle("The Delegations")
                .setDescription(
                        "Three Delegations have taken consular rooms on Mecatol Rex. Each watches the same battles, but not in the same way.")
                .addField(
                        FactionEmojis.getFactionIcon(CombatReplayHouse.NAALU.displayName()) + " Naalu Delegation",
                        "The Naalu speak little and listen closely. They place their wagers with quiet confidence, as if the shape of things is already familiar to them.",
                        false)
                .addField(
                        FactionEmojis.getFactionIcon(CombatReplayHouse.MENTAK.displayName()) + " Mentak Delegation",
                        "The Mentak arrive with sharp smiles and restless hands. Around them, certainty has a way of slipping, and even the clearest outcome can begin to feel suspect.",
                        false)
                .addField(
                        FactionEmojis.getFactionIcon(CombatReplayHouse.HACAN.displayName()) + " Hacan Delegation",
                        "The Hacan treat war like commerce. Every alliance, every rivalry, every ambition can be... negotiated.",
                        false)
                .addField(
                        "Delegations",
                        "When you join the War Ledger, you are assigned a Delegation and invited to its private delegation channel.",
                        false)
                .setColor(new Color(92, 118, 150))
                .build();
    }

    private MessageEmbed rulesEmbed() {
        return new EmbedBuilder()
                .setTitle("Season Rules")
                .setDescription("The public rules are simple. The private details are not.")
                .addField(
                        "Delegation Points",
                        "Prediction and side-bet gains work as usual, but points are pooled by Delegation for the season leaderboard.",
                        false)
                .addField(
                        "Discussion Window",
                        "Before side bets open, each combat now begins with a `15 minute` discussion window for Delegations to gather, compare notes, and decide how to approach the battle.",
                        false)
                .addField(
                        "Passive and Active Abilities",
                        "Each Delegation has one passive ability and one or two active abilities. The exact effects are hidden and should not be shared outside your Delegation.",
                        false)
                .addField(
                        "Private Voting",
                        "Each delegation votes on whether to use its active ability. The bot resolves the vote when the ability window closes.",
                        false)
                .addField(
                        "Favor",
                        "Favor is a resource granted by the Mecatol Custodians. Every Delegation gains Favor after each combat at a steady rate. Favor is spent to activate abilities for key combats.",
                        false)
                .setFooter(
                        "The Council will not remember the luckiest Delegation. It will remember the one that understood the war.")
                .setColor(new Color(120, 92, 148))
                .build();
    }

    private String delegationHeader(DelegationSummary summary) {
        String header = "## " + FactionEmojis.getFactionIcon(summary.house().displayName()) + " " + summary.title();
        if (summary.identity().isBlank()) return header;
        return header + "\n__" + summary.identity() + "__";
    }

    private String delegationSummaryMarkdown(DelegationSummary summary, Guild guild) {
        StringBuilder message = new StringBuilder(delegationRoleMention(summary.house(), guild))
                .append("\n")
                .append(delegationHeader(summary))
                .append("\n")
                .append(communicationRuleLine(summary.house()))
                .append("\n\n");
        appendDelegationSection(message, "Passive: " + summary.passiveName(), summary.passiveText());
        appendDelegationSection(message, "Active: " + summary.activeName(), summary.activeText());
        if (!summary.timingText().isBlank()) {
            appendDelegationSection(message, "Timing and Cost", timingText(summary));
        }
        appendDelegationSection(message, "Strategy", summary.strategyText());
        message.append("\n-# Keep this briefing inside your Delegation.");
        return message.toString().trim();
    }

    private String communicationRuleLine(CombatReplayHouse house) {
        return switch (house) {
            case NAALU -> "-# Communication: You may communicate with Hacan Delegation, but not Mentak Delegation.";
            case MENTAK -> "-# Communication: You may communicate with Hacan Delegation, but not Naalu Delegation.";
            case HACAN -> "-# Communication: You are the only delegation that may communicate with all parties.";
        };
    }

    private String delegationRoleMention(CombatReplayHouse house, Guild guild) {
        if (guild == null || house == null) return "";
        Role role = guild.getRolesByName(house.roleName(), true).stream()
                .findFirst()
                .orElse(null);
        return role == null ? "" : role.getAsMention();
    }

    private void appendDelegationSection(StringBuilder message, String title, String text) {
        if (text.isBlank()) return;
        message.append("### ").append(title).append("\n").append(text.strip()).append("\n\n");
    }

    private String timingText(DelegationSummary summary) {
        return summary.timingText() + "\n\nDelegation abilities require at least `"
                + settings.getHouseAbilities().getMinimumAbilityVotesToResolve()
                + "` distinct voters to resolve. If no option reaches the threshold, the ability is not used.";
    }

    private CombatContestSettings.Naalu naalu() {
        return settings.getHouseAbilities().getNaalu();
    }

    private CombatContestSettings.Mentak mentak() {
        return settings.getHouseAbilities().getMentak();
    }

    private CombatContestSettings.Hacan hacan() {
        return settings.getHouseAbilities().getHacan();
    }

    private record DelegationSummary(
            CombatReplayHouse house,
            String title,
            String identity,
            String passiveName,
            String passiveText,
            String activeName,
            String activeText,
            String timingText,
            String strategyText,
            Color color,
            String iconFile) {}
}
