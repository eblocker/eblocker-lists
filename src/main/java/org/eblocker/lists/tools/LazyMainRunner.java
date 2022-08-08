package org.eblocker.lists.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

/**
 * Takes a list of classes with main methods to run.
 *
 * The last successful run of each class is recorded in a properties file.
 *
 * Only classes which were not successful in the last 12 hours are run.
 */
public class LazyMainRunner {
    private static final Logger log = LoggerFactory.getLogger(LazyMainRunner.class);
    private static final String PROPERTIES_FILE = "LazyMainRunner.properties";
    private static final long SUCCESS_TIMEOUT_SECONDS = 12*60*60;

    private Properties lastSuccess = new Properties();
    private Path propertiesFile = Path.of(PROPERTIES_FILE);

    public LazyMainRunner() throws Exception {
        if (Files.exists(propertiesFile)) {
            try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
                lastSuccess.load(inputStream);
            }
        }
    }

    private void save() throws Exception {
        try (OutputStream outputStream = Files.newOutputStream(propertiesFile)) {
            lastSuccess.store(outputStream, "Last successful runs by the LazyMainRunner");
        }
    }

    public void runIfNecessary(String className) throws Throwable {
        Instant lastSuccessfulRun = Instant.ofEpochMilli(Long.parseLong(lastSuccess.getProperty(className, "0")));
        Instant now = Instant.now();
        if (now.isAfter(lastSuccessfulRun.plusSeconds(SUCCESS_TIMEOUT_SECONDS))) {
            log.info("Last successful run of {} was more than {} seconds ago. Running it again...", className, SUCCESS_TIMEOUT_SECONDS);
            invokeMain(Class.forName(className));
            lastSuccess.setProperty(className, String.valueOf(Instant.now().toEpochMilli()));
        } else {
            log.info("Last successful run of {} was less than {} seconds ago.", className, SUCCESS_TIMEOUT_SECONDS);
        }
    }

    public static void main(String[] args) throws Throwable {
        LazyMainRunner listCreator = new LazyMainRunner();
        for (String className: args) {
            try {
                listCreator.runIfNecessary(className);
            } catch (Throwable e) {
                log.error("Running {} failed", className, e);
                listCreator.save();
                throw e;
            }
        }
        listCreator.save();
    }

    private static void invokeMain(Class clazz) throws Throwable {
        log.info("Invoking main method of {}", clazz);
        Method m = clazz.getDeclaredMethod("main", String[].class);
        String[] args = null;
        try {
            m.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
