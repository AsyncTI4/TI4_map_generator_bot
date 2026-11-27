package ti4.spring.service.tournamentwinner;

import java.util.List;
import java.util.Locale;
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

    private final StandaloneTitleEntityRepository standaloneTitleRepository;
    private final UserEntityRepository userRepository;

    void grant(String userId, String userName, String tourneyName) {
        if (StringUtils.isBlank(tourneyName)) return;
        if (!findWinnerTitles(userId, tourneyName).isEmpty()) return;
        StandaloneTitleEntity title = new StandaloneTitleEntity();
        title.setUser(findOrCreateUser(userId, userName));
        title.setTitle(winnerText(tourneyName));
        title.setSource(tourneyName);
        title.setEmoji(emojiFor(tourneyName));
        standaloneTitleRepository.save(title);
    }

    void revoke(String userId, String tourneyName) {
        standaloneTitleRepository.deleteAll(findWinnerTitles(userId, tourneyName));
    }

    static String emojiFor(String tourneyName) {
        if (tourneyName.toLowerCase(Locale.ROOT).contains("lazax")) return FactionEmojis.Lazax.name();
        return StandaloneTitleEntity.DEFAULT_EMOJI;
    }

    private List<StandaloneTitleEntity> findWinnerTitles(String userId, String tourneyName) {
        String winnerText = winnerText(tourneyName);
        return standaloneTitleRepository.findByUserId(userId).stream()
                .filter(title -> title.getTitle().trim().equalsIgnoreCase(winnerText))
                .toList();
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
