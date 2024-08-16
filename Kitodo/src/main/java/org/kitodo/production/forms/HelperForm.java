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

package org.kitodo.production.forms;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.production.KitodoProduction;
import org.kitodo.production.helper.Helper;

/**
 * Helper form - used for some single methods which don't match yet to other
 * forms.
 */
@Named("HelperForm")
@SessionScoped
public class HelperForm implements Serializable {

    public String getVersion() {
        return KitodoProduction.getInstance().getVersionInformation().getVersion();
    }

    public boolean getAnonymized() {
        return ConfigCore.getBooleanParameterOrDefaultValue(ParameterCore.ANONYMIZE);
    }

    /**
     * Returning value of configuration parameter withUserStepDoneSearch. Used
     * for enabling/disabling search for done steps by user.
     *
     * @return boolean
     */
    public boolean getUserStepDoneSearchEnabled() {
        return ConfigCore.getBooleanParameterOrDefaultValue(ParameterCore.WITH_USER_STEP_DONE_SEARCH);
    }

    /**
     * Get translated input.
     *
     * @param input
     *            for translation
     * @return translated input
     */
    public String getTranslated(String input) {
        return Helper.getTranslation(input);
    }
}
