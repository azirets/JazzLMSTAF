package kg.jazzlms.config;

import java.io.FileInputStream;
import java.util.Properties;

public class ConfigReader {
    private static Properties properties;

    private ConfigReader() {

    }

    static {
        try {
            String path = "src/main/resources/app.properties";
            FileInputStream fileInputStream = new FileInputStream(path);
            properties = new Properties();
            properties.load(fileInputStream);
            fileInputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getValue(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Ключ '" + key + "' не найден в конфиге. Доступные ключи: " + properties.stringPropertyNames());
        }
        return value.trim();
    }
}