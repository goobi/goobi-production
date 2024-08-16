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

import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class KitodoVersion {

    private static String version = "N/A";
    private static String buildDate = "N/A";

    /**
     * Setup KitodoVersion form manifest.
     *
     * @param manifest as Manifest
     */
    void setupFromManifest(Manifest manifest) {
        Attributes mainAttributes = manifest.getMainAttributes();

        version = getValueOrThrowException(mainAttributes, "Implementation-Version");
        buildDate = getValueOrThrowException(mainAttributes, "Implementation-Build-Date");
    }

    private String getValueOrThrowException(Attributes attributes, String attributeName) {
        String value = attributes.getValue(attributeName);
        if (null == value) {
            throw new IllegalArgumentException(
                    "Manifest does not contain " + attributeName + ". The build may be corrupted.");
        }
        return value;
    }

    public String getVersion() {
        return version;
    }

    public String getBuildDate() {
        return buildDate;
    }
}
