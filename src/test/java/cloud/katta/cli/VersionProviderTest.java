/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.cli;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionProviderTest {

    @Test
    void testVersion() {
        final StringWriter out = new StringWriter();
        final CommandLine commandLine = Katta.commandLine();
        commandLine.setOut(new PrintWriter(out));
        assertEquals(0, commandLine.execute("--version"));
        final String version = out.toString().trim();
        assertTrue(version.startsWith("katta "), version);
        assertFalse(version.contains("${"), version);
        assertFalse(version.contains("unknown"), version);
    }
}
