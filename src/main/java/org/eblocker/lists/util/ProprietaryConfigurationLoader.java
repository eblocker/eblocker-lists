package org.eblocker.lists.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Reads system properties that configure the inclusion of proprietary list providers.
 * These providers need secret URLs and credentials for downloading lists.
 *
 * The following system properties are evaluated:
 * <ul>
 *     <li>includeProprietaryLists: include proprietary lists? Default is "false".</li>
 *     <li>proprietaryProviderConfiguration: path of a file containing additional configuration parameters for
 *     proprietary list providers.</li>
 * </ul>
 */
public class ProprietaryConfigurationLoader {
    public static final String INCLUDE_PROPRIETARY_LISTS_KEY = "includeProprietaryLists";
    public static final String PROPRIETARY_PROVIDER_CONFIGURATION_KEY = "proprietaryProviderConfiguration";

    /**
     * Adds parameters for proprietary list providers to a given configuration
     * @param configuration base configuration
     * @return true if proprietary lists shall be included
     * @throws IOException
     */
    public static boolean addProprietaryConfiguration(Properties configuration) throws IOException {
        boolean includeProprietaryLists = "true".equals(System.getProperty(INCLUDE_PROPRIETARY_LISTS_KEY));
        if (includeProprietaryLists) {
            String proprietaryProviderConfiguration = System.getProperty(PROPRIETARY_PROVIDER_CONFIGURATION_KEY);
            if (proprietaryProviderConfiguration == null || proprietaryProviderConfiguration.isEmpty()) {
                throw new RuntimeException("System property 'proprietaryProviderConfiguration' must be set if includeProprietaryLists==true");
            }
            configuration.putAll(loadProperties(Paths.get(proprietaryProviderConfiguration)));
        }
        return includeProprietaryLists;
    }
    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(path.toFile()));
        return properties;
    }
}
