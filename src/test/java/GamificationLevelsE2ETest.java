import kg.jazzlms.models.request.GamificationSettings;
import kg.jazzlms.models.request.GamificationSettings.LevelRule;
import kg.jazzlms.models.request.GamificationSettings.Levels;
import kg.jazzlms.models.response.UserGamificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E: уровни при разных настройках.
 *
 * Правило, подтверждённое наблюдениями: каждое включённое правило (по очкам, по курсам, по бейджам)
 * даёт уровень 1 + счётчик / шаг, итоговый уровень — наибольший из них. Если уровни выключены — всегда 1.
 * nextLevelPoints = шаг по очкам x уровень и есть только при включённом правиле "по очкам", иначе null.
 *
 * За полный путь ученика: очки зависят от настроек, курс 1, бейджей 3.
 */
@Tag("e2e")
@DisplayName("E2E gamification: уровни — выключены, по очкам, по курсам, по бейджам, комбинации")
public class GamificationLevelsE2ETest extends GamificationE2ESupport {

    private static LevelRule on(int every) {
        return new LevelRule(true, every);
    }

    private static LevelRule off() {
        return new LevelRule(false, 5);
    }

    static Stream<Arguments> levelSettings() {
        return Stream.of(
                Arguments.of("уровни выключены (правила включены)", new Levels(false, on(50), on(1), on(1))),
                Arguments.of("только по очкам, шаг 50", new Levels(true, on(50), off(), off())),
                Arguments.of("только по очкам, шаг 1000", new Levels(true, on(1000), off(), off())),
                Arguments.of("только по курсам, шаг 1", new Levels(true, off(), on(1), off())),
                Arguments.of("только по бейджам, шаг 1", new Levels(true, off(), off(), on(1))),
                Arguments.of("по очкам и курсам: 300 / 1", new Levels(true, on(300), on(1), off())),
                Arguments.of("по очкам и бейджам: 300 / 1", new Levels(true, on(300), off(), on(1))),
                Arguments.of("все три: очки 20, курсы 1, бейджи 1", new Levels(true, on(20), on(1), on(1))),
                Arguments.of("все три правила выключены (уровни включены)", new Levels(true, off(), off(), off()))
        );
    }

    /** Ожидаемый уровень по формуле из описания класса */
    private static int expectedLevel(Levels levels, UserGamificationResponse profile) {
        if (!levels.getEnabled()) {
            return 1;
        }
        int level = 1;
        if (levels.getByPoints().getEnabled()) {
            level = Math.max(level, 1 + profile.getPoints() / levels.getByPoints().getEvery());
        }
        if (levels.getByCourses().getEnabled()) {
            level = Math.max(level, 1 + profile.getCourses() / levels.getByCourses().getEvery());
        }
        if (levels.getByBadges().getEnabled()) {
            level = Math.max(level, 1 + profile.getBadges().size() / levels.getByBadges().getEvery());
        }
        return level;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("levelSettings")
    @DisplayName("Уровень и очки до следующего уровня считаются по включённым правилам")
    void levelFollowsEnabledRules(String name, Levels levels) {
        apply(original.toBuilder().levels(levels).build());

        UserGamificationResponse profile = play(true).profile;

        int expectedLevel = expectedLevel(levels, profile);
        Integer expectedNext = levels.getEnabled() && levels.getByPoints().getEnabled()
                ? levels.getByPoints().getEvery() * expectedLevel : null;
        assertAll(
                () -> assertEquals(3, profile.getBadges().size(), "Для расчёта по бейджам ожидаем 3 бейджа"),
                () -> assertEquals(1, profile.getCourses()),
                () -> assertEquals(expectedLevel, profile.getLevel(), "Уровень (очки " + profile.getPoints() + ", курсов "
                        + profile.getCourses() + ", бейджей " + profile.getBadges().size() + ")"),
                () -> assertEquals(expectedNext, profile.getNextLevelPoints(), "Очки до следующего уровня"),
                () -> assertEquals(expectedPoints(original), profile.getPoints(), "Очки от уровней не зависят")
        );
    }

    @Test
    @DisplayName("Уровни по умолчанию (шаги 300 / 5 / 5): набранные очки дают уровень 1, до следующего 300")
    void defaultLevels() {
        UserGamificationResponse profile = play(true).profile;

        int expectedLevel = expectedLevel(original.getLevels(), profile);
        assertAll(
                () -> assertEquals(expectedLevel, profile.getLevel()),
                () -> assertEquals(original.getLevels().getByPoints().getEvery() * expectedLevel, profile.getNextLevelPoints())
        );
    }

    @Test
    @DisplayName("Бейджи выключены, а уровень по бейджам включён: бейджей 0, значит уровень 1")
    void levelByBadgesWithoutBadges() {
        apply(original.toBuilder()
                .badges(original.getBadges().toBuilder().enabled(false).build())
                .levels(new Levels(true, off(), off(), on(1))).build());

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertTrue(profile.getBadges().isEmpty()),
                () -> assertEquals(1, profile.getLevel())
        );
    }

    @Test
    @DisplayName("Очки выключены, а уровень по очкам включён: очков 0, значит уровень 1")
    void levelByPointsWithoutPoints() {
        apply(original.toBuilder()
                .points(original.getPoints().toBuilder().enabled(false).build())
                .levels(new Levels(true, on(50), off(), off())).build());

        UserGamificationResponse profile = play(true).profile;

        assertAll(
                () -> assertEquals(0, profile.getPoints()),
                () -> assertEquals(1, profile.getLevel())
        );
    }
}
