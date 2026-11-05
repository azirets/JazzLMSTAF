package kg.jazzlms.utils;

import java.util.function.BooleanSupplier;

/**
 * Ожидание асинхронных эффектов: события между сервисами идут через Kafka,
 * поэтому письмо, запись в ленте или очки появляются не мгновенно.
 */
public class Waiter {

    private static final long POLL_MILLIS = 500;

    private Waiter() {
    }

    /** Проверяет условие каждые 0.5 с, пока оно не станет true или не истекут секунды. Возвращает итоговое значение */
    public static boolean waitUntil(int seconds, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}
