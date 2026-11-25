package ti4.spring.service.tournamentwinner;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ti4.service.emoji.FactionEmojis;
import ti4.spring.service.persistence.StandaloneTitleEntity;
import ti4.spring.service.persistence.StandaloneTitleEntityRepository;
import ti4.spring.service.persistence.UserEntity;
import ti4.spring.service.persistence.UserEntityRepository;

@Service
@RequiredArgsConstructor
class TournamentWinnerTitleService {

    private static final Pattern EMOJI = Pattern.compile("<a?:(\\w+):\\d+>|:(\\w+):");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final StandaloneTitleEntityRepository standaloneTitleRepository;
    private final UserEntityRepository userRepository;

    boolean grant(String userId, String userName, String tourneyName) {
        if (StringUtils.isBlank(tourneyName)) return false;
        Optional<StandaloneTitleEntity> existing = findWinnerTitle(userId, tourneyName);
        if (existing.isPresent()) return moveInlineEmojiToColumn(existing.get());
        StandaloneTitleEntity title = new StandaloneTitleEntity();
        title.setUser(findOrCreateUser(userId, userName));
        title.setTitle(winnerText(tourneyName));
        title.setSource(tourneyName);
        title.setEmoji(emojiFor(tourneyName));
        standaloneTitleRepository.save(title);
        return true;
    }

    void revoke(String userId, String tourneyName) {
        List<StandaloneTitleEntity> winnerTitles = standaloneTitleRepository.findByUserId(userId).stream()
                .filter(title -> isWinnerTitleFor(title.getTitle(), tourneyName))
                .toList();
        standaloneTitleRepository.deleteAll(winnerTitles);
    }

    static String emojiFor(String tourneyName) {
        if (tourneyName.toLowerCase(Locale.ROOT).contains("lazax")) return FactionEmojis.Lazax.name();
        return StandaloneTitleEntity.DEFAULT_EMOJI;
    }

    private Optional<StandaloneTitleEntity> findWinnerTitle(String userId, String tourneyName) {
        return standaloneTitleRepository.findByUserId(userId).stream()
                .filter(title -> isWinnerTitleFor(title.getTitle(), tourneyName))
                .findFirst();
    }

    private boolean moveInlineEmojiToColumn(StandaloneTitleEntity title) {
        Matcher emoji = EMOJI.matcher(title.getTitle());
        if (!emoji.find()) return false;
        if (StringUtils.isBlank(title.getEmoji())) {
            title.setEmoji(StringUtils.defaultIfBlank(emoji.group(1), emoji.group(2)));
        }
        title.setTitle(withoutEmojis(title.getTitle()));
        standaloneTitleRepository.save(title);
        return true;
    }

    private static boolean isWinnerTitleFor(String title, String tourneyName) {
        return withoutEmojis(title).equalsIgnoreCase(winnerText(tourneyName));
    }

    private static String withoutEmojis(String title) {
        String spaced = EMOJI.matcher(title).replaceAll(" ");
        return WHITESPACE.matcher(spaced).replaceAll(" ").trim();
    }

    private UserEntity findOrCreateUser(String userId, String userName) {
        return userRepository
                .findById(userId)
                .orElseGet(() ->
                        userRepository.save(new UserEntity(userId, StringUtils.defaultIfBlank(userName, userId))));
    }

    private static String winnerText(String tourneyName) {
        return tourneyName.trim() + " Winner";
    }
}
