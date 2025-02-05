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

package org.kitodo.production.services.data;

/**
 * An interface for the two possible query variants when parsing the filter
 * string.
 */
interface UserSpecifiedFilter {
    /**
     * Returns the filter field.
     * 
     * @return the filter field
     */
    FilterField getFilterField();
}
