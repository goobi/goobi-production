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

package org.kitodo.production.helper.messages;

import java.util.Enumeration;
import java.util.Locale;
import java.util.MissingResourceException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ErrorTest {

    private final Locale locale = new Locale("EN");
    private final String customBundle = "errors";
    private final String defaultBundle = "messages.errors";

    @Test
    public void shouldGetKeys() {
        Enumeration<String> keys = Error.getResourceBundle(defaultBundle, customBundle, locale).getKeys();

        boolean containsKey = false;
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (key.equals("error")) {
                containsKey = true;
                break;
            }
        }

        assertTrue("Keys set doesn't contain searched key!", containsKey);
    }

    @Test
    public void shouldGetStringFromDefaultBundle() {
        // in case custom bundle does not exist
        assertEquals(
            "Error...", 
            Error.getResourceBundle(defaultBundle, "non-existent-bundle", locale).getString("error")
        );
    }

    @Test
    public void shouldGetStringFromCustomBundle() {      
        // in case custom bundle exists, and also contains definition for the requested key
        assertEquals(
            "Test custom error", 
            Error.getResourceBundle(defaultBundle, customBundle, locale).getString("error")
        );
    }

    @Test
    public void shouldThrowMissingRessourceExceptionForNonExistentKey() {
        // in case custom bundle is loaded, but key does not exist in either resource bundles 
        assertThrows(
            MissingResourceException.class, 
            () -> Error.getResourceBundle(defaultBundle, customBundle, locale).getString("non-existent-key")
        );

        // in case custom bundle is missing, and key does also not exist in default bundle
        assertThrows(
            MissingResourceException.class, 
            () -> Error.getResourceBundle(defaultBundle, "non-existent-bundle", locale).getString("non-existent-key")
        );

        // in case custom bundle is loaded, but does not include the key, even if the key exists in the default bundle
        assertThrows(
            MissingResourceException.class, 
            () -> Error.getResourceBundle(defaultBundle, customBundle, locale).getString("errorOccurred")
        );
    }
}
