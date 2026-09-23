/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine;

/**
 * Reads the version from a resource filtered by Maven at build time. The JAR manifest is not available in the native image.
 */
public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() throws IOException {
        final Properties properties = new Properties();
        try (InputStream in = VersionProvider.class.getResourceAsStream("version.properties")) {
            if(in == null) {
                return new String[]{"katta (unknown version)"};
            }
            properties.load(in);
        }
        return new String[]{String.format("katta %s", properties.getProperty("version", "unknown"))};
    }
}
