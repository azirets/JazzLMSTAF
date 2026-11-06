package kg.jazzlms.http;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;

/**
 * Общая настройка HTTP-клиента для всех запросов тестов.
 *
 * Сервисы изредка обрывают соединение ("Connection reset") ещё до ответа. Это не ответ API, а сбой сети,
 * при параллельном запуске он заметнее. Клиент сам повторяет запрос до 2 раз именно в этом случае;
 * настоящие ответы сервиса (4xx и 5xx) не повторяются.
 */
public final class HttpSetup {

    private static boolean done;

    private HttpSetup() {
    }

    @SuppressWarnings("deprecation")
    public static synchronized void init() {
        if (done) {
            return;
        }
        RestAssured.config = RestAssured.config().httpClient(HttpClientConfig.httpClientConfig().httpClientFactory(() -> {
            DefaultHttpClient client = new DefaultHttpClient();
            // true: повторять и тогда, когда запрос уже отправлен (обрыв случается после отправки, при чтении ответа)
            client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(2, true));
            return client;
        }));
        done = true;
    }
}
