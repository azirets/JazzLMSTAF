import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.request.GamificationSettings.Badges;
import kg.jazzlms.models.response.UserGamificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: бейджи при разных настройках. За полный путь ученика выдаются три бейджа:
 * Newcomer (первый вход, категория Activity), First step (первый юнит, Learning), Graduate (курс и сертификат, Certification).
 * Выключенные бейджи не выдаются и не попадают в историю, а очки, счётчики и уровень от них не зависят.
 */
@Tag("e2e")
@DisplayName("E2E gamification: бейджи — выключены все, по категориям")
public class GamificationBadgesE2ETest extends GamificationE2ESupport {

    private void applyBadges(Badges badges) {
        apply(original.toBuilder().badges(badges).build());
    }

    @Tag("smoke")
    @Test
    @DisplayName("Бейджи включены: выданы Newcomer, First step и Graduate своих категорий, у каждого есть дата")
    void allBadgesEnabled() {
        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertEquals(Set.of("newcomer", "first-step", "graduate"), Set.copyOf(badgeCodes(profile))),
                () -> assertEquals("Activity", categoryOf(profile, "newcomer")),
                () -> assertEquals("Learning", categoryOf(profile, "first-step")),
                () -> assertEquals("Certification", categoryOf(profile, "graduate")),
                () -> assertTrue(profile.getBadges().stream().allMatch(b -> b.get("awardedAt") != null && b.get("name") != null)),
                () -> assertEquals(3, badgeHistory(profile).size(), "Каждая выдача бейджа записана в историю")
        );
    }

    @Test
    @DisplayName("badges.enabled = false: бейджей нет и в истории их нет; очки, счётчики и уровень как обычно")
    void badgesDisabled() {
        applyBadges(original.getBadges().toBuilder().enabled(false).build());

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertTrue(profile.getBadges().isEmpty(), "Бейджи не должны выдаваться: " + profile.getBadges()),
                () -> assertTrue(badgeHistory(profile).isEmpty(), "В истории не должно быть записей о бейджах"),
                () -> assertEquals(expectedPoints(original), profile.getPoints(), "Очки от бейджей не зависят"),
                () -> assertEquals(1, profile.getLogins()),
                () -> assertEquals(2, profile.getUnits()),
                () -> assertEquals(1, profile.getCourses())
        );
    }

    @Test
    @DisplayName("badges.enabled = false: /config показывает badges = false")
    void configShowsBadgesDisabled() {
        applyBadges(original.getBadges().toBuilder().enabled(false).build());

        assertFalse(GAMIFICATION.configResponse().jsonPath().getBoolean("badges"));
    }

    @ParameterizedTest(name = "выключена категория {0}: нет бейджа {1}")
    @CsvSource({"activity, newcomer", "learning, first-step", "certification, graduate"})
    @DisplayName("Выключена одна категория: нет только её бейджа, остальные выдаются")
    void oneCategoryDisabled(String category, String missingBadge) {
        Badges.BadgesBuilder badges = original.getBadges().toBuilder();
        switch (category) {
            case "activity" -> badges.activity(false);
            case "learning" -> badges.learning(false);
            case "certification" -> badges.certification(false);
            default -> throw new IllegalArgumentException(category);
        }
        applyBadges(badges.build());

        UserGamificationResponse profile = play(true).profile;

        List<String> expected = List.of("newcomer", "first-step", "graduate").stream().filter(c -> !c.equals(missingBadge)).toList();
        assertAll(
                () -> assertEquals(Set.copyOf(expected), Set.copyOf(badgeCodes(profile)), "Должны остаться только бейджи других категорий"),
                () -> assertFalse(badgeCodes(profile).contains(missingBadge)),
                () -> assertEquals(2, badgeHistory(profile).size()),
                () -> assertEquals(expectedPoints(original), profile.getPoints(), "Очки от категорий бейджей не зависят")
        );
    }

    @Test
    @DisplayName("Выключены все три категории при включённых бейджах: бейджей нет")
    void allCategoriesDisabled() {
        applyBadges(new Badges(true, false, false, false));

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertTrue(profile.getBadges().isEmpty(), "Бейджей быть не должно: " + profile.getBadges()),
                () -> assertTrue(badgeHistory(profile).isEmpty()),
                () -> assertEquals(expectedPoints(original), profile.getPoints())
        );
    }

    @Test
    @DisplayName("Выключили бейджи, включили обратно: бейджи начинают выдаваться за новые действия")
    void badgesAreAwardedAgainAfterEnabling() {
        applyBadges(original.getBadges().toBuilder().enabled(false).build());
        Run run = play(true);
        assertTrue(run.profile.getBadges().isEmpty());

        apply(original);
        completeExtraLesson(run);

        // по счётчику юнитов бейдж First step ещё не выдавался, поэтому теперь он должен появиться
        boolean awarded = kg.jazzlms.utils.Waiter.waitUntil(WAIT_SECONDS,
                () -> !GAMIFICATION.user(run.userId).getBadges().isEmpty());
        UserGamificationResponse profile = GAMIFICATION.user(run.userId);
        assertTrue(awarded, "Бейджи после включения не выдаются: " + profile.getHistory());
    }

    private static String categoryOf(UserGamificationResponse profile, String code) {
        return profile.getBadges().stream().filter(b -> code.equals(b.get("code")))
                .map(b -> (String) b.get("category")).findFirst().orElse(null);
    }
}
