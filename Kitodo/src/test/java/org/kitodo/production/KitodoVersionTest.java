/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

public class KitodoVersionTest {

    private static final String VERSION = "1.2.3";
    private static final String BUILD_DATE = "17-Februrary-2011";

    @Test
    public void shouldThrowExceptionIfKitodoSectionIsMissingInManifest() {
        assertThrows(IllegalArgumentException.class, () -> new KitodoVersion().setupFromManifest(new Manifest()));
    }

    @Test
    public void attributeVersionShouldBeEqualToImplementationVersion() {
        Manifest manifest = createManifestWithValues();
        KitodoVersion version = new KitodoVersion();
        version.setupFromManifest(manifest);

        assertEquals(VERSION, version.getVersion(),
            "Version attribute should be equal to Implementation-Version as specified in the given Manifest.");
    }

    @Test
    public void attributeBuildDateShouldBeEqualToImplementationBuildDate() {
        Manifest manifest = createManifestWithValues();
        KitodoVersion version = new KitodoVersion();
        version.setupFromManifest(manifest);

        assertEquals(BUILD_DATE, version.getBuildDate(),
            "BuildDate attribute should be equal to Implementation-Build-Date as specified in the given Manifest.");
    }

    private Manifest createManifestWithValues() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Implementation-Version", VERSION);
        manifest.getMainAttributes().putValue("Implementation-Build-Date", BUILD_DATE);
        return manifest;
    }
}
