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

package org.kitodo.dataeditor;

import static org.kitodo.constants.StringConstants.KITODO;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MetsKitodoNamespacePrefixMapperTest {
    MetsKitodoNamespacePrefixMapper mapper = new MetsKitodoNamespacePrefixMapper();

    @Test
    public void shouldReturnPrefix() {
        String preferredKitodoPrefix = mapper.getPreferredPrefix("http://meta.kitodo.org/v1/", null, true);
        String preferredMetsPrefix = mapper.getPreferredPrefix("http://www.loc.gov/METS/", null, true);
        String preferredXlinkPrefix = mapper.getPreferredPrefix("http://www.w3.org/1999/xlink", null, true);
        String notExistingPrefix = mapper.getPreferredPrefix("http://not.existing", "return this", true);
        assertEquals(KITODO, preferredKitodoPrefix, "Prefix mapper return the wrong prefix for kitodo uri");
        assertEquals("mets", preferredMetsPrefix, "Prefix mapper return the wrong prefix for mets uri");
        assertEquals("xlink", preferredXlinkPrefix, "Prefix mapper return the wrong prefix for xlink uri");
        assertEquals("return this", notExistingPrefix, "Prefix mapper return the wrong prefix for not existing uri");
    }
}
