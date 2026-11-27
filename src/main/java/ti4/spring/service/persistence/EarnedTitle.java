package ti4.spring.service.persistence;

public record EarnedTitle(String title, String source, String emoji) {

    public EarnedTitle(String title, String source) {
        this(title, source, null);
    }
}
