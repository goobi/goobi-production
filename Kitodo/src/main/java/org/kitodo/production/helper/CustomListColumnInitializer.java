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

package org.kitodo.production.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.ListColumn;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.production.services.ServiceManager;

@ApplicationScoped
@SuppressWarnings("unused")
public class CustomListColumnInitializer {

    private static final String PROCESS_PREFIX = "process.";
    private static final String TASK_PREFIX = "task.";
    private static final Logger logger = LogManager.getLogger(CustomListColumnInitializer.class);
    private String[] processProperties;
    private String[] taskProcessProperties;

    /**
     * Load custom columns from Kitodo configuration file and save them as custom list columns to
     * database during application startup.
     *
     * @param context ServletContext
     */
    public void init(@Observes @Initialized(ApplicationScoped.class) ServletContext context) {
        try {
            processProperties = loadCustomColumnsFromConfigurationFile(ParameterCore.PROCESS_PROPERTIES, PROCESS_PREFIX);
            taskProcessProperties = loadCustomColumnsFromConfigurationFile(ParameterCore.TASK_CUSTOM_COLUMNS, TASK_PREFIX);
            List<String> customColumns = new ArrayList<>();
            customColumns.addAll(Arrays.asList(processProperties));
            customColumns.addAll(Arrays.asList(taskProcessProperties));

            updateCustomColumnsInDatabase(customColumns);

        } catch (DAOException e) {
            logger.error("Unable to update custom list columns in database!");
        } catch (NoSuchElementException e) {
            logger.info("Configuration key '"
                    + ParameterCore.PROCESS_PROPERTIES
                    + "' or '" + ParameterCore.TASK_CUSTOM_COLUMNS
                    + "' not found in configuration => unable to load corresponding custom columns!");
        }
    }

    /**
     * Get names of process properties configured in kitodo_config.properties as custom columns for the process list
     *
     * @return array of process properties
     */
    public String[] getProcessProperties() {
        return processProperties;
    }

    /**
     * Get names of custom task columns configured in kitodo_config.properties as custom columns for the task list
     *
     * @return array of custom task columns
     */
    public String[] getTaskProcessProperties() {
        return taskProcessProperties;
    }

    /**
     * Load custom column names for configuration key 'configurationKey' from Kitodo configuration file, prefix them
     * with given String 'prefix' and return names as list.
     *
     * @param configurationKey
     *              Configuration key for custom column names in Kitodo configuration file
     * @param prefix
     *              String that is prepend to extracted custom column names before returning them as a list
     * @return List of custom column names prepended with given String 'prefix'.
     */
    private String[] loadCustomColumnsFromConfigurationFile(ParameterCore configurationKey, String prefix) {
        return Arrays.stream(ConfigCore.getParameter(configurationKey).split(","))
                .filter(name -> !name.trim().isEmpty())
                .map(name -> prefix + name.trim())
                .toArray(String[]::new);
    }

    /**
     * Update list of custom columns in database. First, delete all custom column in database except for those in given
     * list 'customColumnList'. Then, add all elements from given list to database that are not already in database.
     *
     * @param customColumnList
     *              list of custom column names that should be in the database
     * @throws DAOException
     *              thrown when custom column table or entries cannot be found in database
     */
    private void updateCustomColumnsInDatabase(List<String> customColumnList) throws DAOException {
        ServiceManager.getListColumnService().removeCustomListColumns(customColumnList);

        List<String> availableColumnNames = ServiceManager.getListColumnService().getAllCustomListColumns().stream()
                .map(ListColumn::getTitle)
                .collect(Collectors.toList());
        logger.info("Loading custom column names from configuration file...");
        for (String ccn : customColumnList) {
            if (!ccn.trim().isEmpty()) {
                if (availableColumnNames.contains(ccn)) {
                    logger.info("Custom column '{}' already saved to database as custom list column!", ccn);
                } else {
                    ListColumn customColumn = new ListColumn(ccn, true);
                    ServiceManager.getListColumnService().saveToDatabase(customColumn);
                    logger.info("Custom column '{}' successfully saved to database as custom list column!", ccn);
                }
            }
        }
    }
}
