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

package org.kitodo.config;

import static org.kitodo.constants.StringConstants.KITODO;

import de.unigoettingen.sub.search.opac.ConfigOpac;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.faces.model.SelectItem;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.ConfigurationBuilderEvent;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.enums.KitodoConfigFile;
import org.kitodo.exceptions.DoctypeMissingException;
import org.kitodo.production.process.field.AdditionalField;

public class ConfigProject {

    private static final Logger logger = LogManager.getLogger(ConfigProject.class);

    private XMLConfiguration config;
    private String projectTitle;

    private static final String CREATE_NEW_PROCESS = "createNewProcess";
    private static final String ITEM_LIST = CREATE_NEW_PROCESS + ".itemlist";
    private static final String ITEM_LIST_ITEM = ITEM_LIST + ".item";
    private static final String ITEM_LIST_PROCESS_TITLE = ITEM_LIST + ".processtitle";

    /**
     * Constructor for ConfigProject.
     *
     * @param projectTitle
     *            for which configuration is going to be read
     * @throws IOException
     *             if config file not found
     */
    public ConfigProject(String projectTitle) throws IOException {
        KitodoConfigFile configFile = KitodoConfigFile.PROJECT_CONFIGURATION;

        if (!configFile.exists()) {
            throw new IOException("File not found: " + configFile.getAbsolutePath());
        }
        try {
            // Create and initialize the builder
            ReloadingFileBasedConfigurationBuilder<XMLConfiguration> builder =
                new ReloadingFileBasedConfigurationBuilder<>(XMLConfiguration.class)
                    .configure(new Parameters().xml()
                        .setFile(configFile.getFile())
                        .setListDelimiterHandler(new DefaultListDelimiterHandler('&')));
            // Register an event listener for triggering reloading checks
            builder.addEventListener(ConfigurationBuilderEvent.CONFIGURATION_REQUEST,
                event -> builder.getReloadingController().checkForReloading(null));
            this.config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            logger.error(e.getMessage(), e);
            this.config = new XMLConfiguration();
        }

        int countProjects = this.config.getMaxIndex("project");
        for (int i = 0; i <= countProjects; i++) {
            String title = this.config.getString("project(" + i + ")[@name]");
            if (title.equals(projectTitle)) {
                this.projectTitle = "project(" + i + ").";
                break;
            }
        }

        try {
            this.config.getBoolean(this.projectTitle + "createNewProcess.opac[@use]");
        } catch (NoSuchElementException e) {
            this.projectTitle = "project(0).";
        }
    }

    /**
     * Get doc type.
     *
     * @return value of docType
     */
    public String getDocType() throws DoctypeMissingException {
        try {
            return getParamString(CREATE_NEW_PROCESS + ".defaultdoctype", ConfigOpac.getAllDoctypes().get(0).getTitle());
        } catch (IndexOutOfBoundsException e) {
            throw new DoctypeMissingException("No doctypes configured in kitodo_opac.xml");
        }
    }

    /**
     * Get use opac.
     *
     * @return value of useOpac
     */
    public boolean isUseOpac() {
        return getParamBoolean(CREATE_NEW_PROCESS + ".opac[@use]");
    }

    /**
     * Get use templates.
     *
     * @return value of useTemplates
     */
    public boolean isUseTemplates() {
        return getParamBoolean(CREATE_NEW_PROCESS + ".templates[@use]", true);
    }

    /**
     * Get opac catalog.
     *
     * @return value of opacCatalog
     */
    public String getOpacCatalog() {
        return getParamString(CREATE_NEW_PROCESS + ".opac.catalogue");
    }

    /**
     * Get tif definition.
     *
     * @return tif definition as String
     */
    public String getTifDefinition() throws DoctypeMissingException {
        return getParamString("tifheader." + getDocType(), KITODO);
    }

    /**
     * Get title definition.
     *
     * @return title definition as String
     */
    public String getTitleDefinition() throws DoctypeMissingException {
        int count = getParamList(ITEM_LIST_PROCESS_TITLE).size();
        String titleDefinition = "";

        for (int i = 0; i < count; i++) {
            String title = getParamString(ITEM_LIST_PROCESS_TITLE + "(" + i + ")");
            String isDocType = getParamString(ITEM_LIST_PROCESS_TITLE + "(" + i + ")[@isdoctype]");
            String isNotDocType = getParamString(ITEM_LIST_PROCESS_TITLE + "(" + i + ")[@isnotdoctype]");

            title = processNullValues(title);
            isDocType = processNullValues(isDocType);
            isNotDocType = processNullValues(isNotDocType);

            titleDefinition = findTitleDefinition(title, getDocType(), isDocType, isNotDocType);

            // break loop after title definition was found
            if (isTitleDefinitionFound(titleDefinition)) {
                break;
            }
        }
        return titleDefinition;
    }

    /**
     * Get hidden fields which are appended to standard fields with false value.
     *
     * @return value of hidden fields
     */
    public Map<String, Boolean> getHiddenFields() {
        Map<String, Boolean> hiddenFields = new HashMap<>();
        for (String standardField : getParamList(ITEM_LIST + ".hide")) {
            hiddenFields.put(standardField, false);
        }
        return hiddenFields;
    }

    /**
     * Get additional fields.
     *
     * @return list of AdditionalField objects
     */
    public List<AdditionalField> getAdditionalFields() throws DoctypeMissingException {
        List<AdditionalField> additionalFields = new ArrayList<>();

        int count = getParamList(ITEM_LIST_ITEM).size();
        for (int i = 0; i < count; i++) {
            AdditionalField additionalField = new AdditionalField(getDocType());
            additionalField.setFrom(getParamString(ITEM_LIST_ITEM + "(" + i + ")[@from]"));
            additionalField.setTitle(getParamString(ITEM_LIST_ITEM + "(" + i + ")"));
            additionalField.setRequired(getParamBoolean(ITEM_LIST_ITEM + "(" + i + ")[@required]"));
            additionalField.setIsDocType(getParamString(ITEM_LIST_ITEM + "(" + i + ")[@isdoctype]"));
            additionalField.setIsNotDoctype(getParamString(ITEM_LIST_ITEM + "(" + i + ")[@isnotdoctype]"));
            // attributes added 30.3.09
            String test = getParamString(ITEM_LIST_ITEM + "(" + i + ")[@initStart]");
            additionalField.setInitStart(test);

            additionalField.setInitEnd(getParamString(ITEM_LIST_ITEM + "(" + i + ")[@initEnd]"));

            // binding to a metadata of a doc struct
            if (getParamBoolean(ITEM_LIST_ITEM + "(" + i + ")[@ughbinding]")) {
                additionalField.setUghBinding(true);
                additionalField.setDocStruct(getParamString(ITEM_LIST_ITEM + "(" + i + ")[@docstruct]"));
                additionalField.setMetadata(getParamString(ITEM_LIST_ITEM + "(" + i + ")[@metadata]"));
            }
            if (getParamBoolean(ITEM_LIST_ITEM + "(" + i + ")[@autogenerated]")) {
                additionalField.setAutogenerated(true);
            }

            // check whether the current item should become a selection list
            int selectItemCount = getParamList(ITEM_LIST_ITEM + "(" + i + ").select").size();
            // go through Children and create SelectItem elements
            if (selectItemCount > 0) {
                additionalField.setSelectList(new ArrayList<>());
                for (int j = 0; j < selectItemCount; j++) {
                    String svalue = getParamString(ITEM_LIST_ITEM + "(" + i + ").select(" + j + ")[@label]");
                    String sid = getParamString(ITEM_LIST_ITEM + "(" + i + ").select(" + j + ")");
                    additionalField.getSelectList().add(new SelectItem(sid, svalue, null));
                }
            }
            additionalFields.add(additionalField);
        }

        return additionalFields;
    }

    /**
     * Determine a specific parameter of the configuration as a String.
     *
     * @return Parameter als String
     */
    public String getParamString(String inParameter) {
        try {
            String paramString = this.config.getString(this.projectTitle + inParameter);
            return cleanXmlFormattedString(paramString);
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            return "";
        }
    }

    /**
     * Determining a specific parameter of the configuration with specification of a
     * default value.
     *
     * @return Parameter als String
     */
    public String getParamString(String parameter, String defaultIfNull) {
        try {
            String myParam = this.projectTitle + parameter;
            String paramString = this.config.getString(myParam, defaultIfNull);
            return cleanXmlFormattedString(paramString);
        } catch (RuntimeException e) {
            return defaultIfNull;
        }
    }

    /**
     * Determine a boolean parameter of the configuration. Return given 'defaultValue' if boolean
     * parameter was not found in the configuration.
     *
     * @param parameter as boolean
     * @param defaultValue default value
     * @return value of parameter in configuration
     */
    public boolean getParamBoolean(String parameter, boolean defaultValue) {
        try {
            return this.config.getBoolean(this.projectTitle + parameter);
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    /**
     * Determine a boolean parameter of the configuration.
     *
     * @return Parameter als boolean
     */
    public boolean getParamBoolean(String parameter) {
        return getParamBoolean(parameter, false);
    }

    /**
     * Determine a list of configuration parameters.
     *
     * @return Parameter als List
     */
    public List<String> getParamList(String parameter) {
        try {
            List<Object> configs = this.config.getList(this.projectTitle + parameter);
            return configs.stream().map(object -> Objects.toString(object, null)).collect(Collectors.toList());
        } catch (RuntimeException e) {
            logger.error(e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private String cleanXmlFormattedString(String inString) {
        if (Objects.nonNull(inString)) {
            inString = inString.replaceAll("\t", " ");
            inString = inString.replaceAll("\n", " ");
            while (inString.contains("  ")) {
                inString = inString.replaceAll(" {2}", " ");
            }
        }
        return inString;
    }

    /**
     * Find title definitions. Conditions:
     * <dl>
     * <dt>{@code isDocType.equals("") && isNotDocType.equals("")}</dt>
     * <dd>nothing was specified</dd>
     * <dt>{@code isNotDocType.equals("") && StringUtils.containsIgnoreCase(isDocType, docType)}</dt>
     * <dd>only duty was specified</dd>
     * <dt>{@code isDocType.equals("") && !StringUtils.containsIgnoreCase(isNotDocType, docType)}</dt>
     * <dd>only may not was specified</dd>
     * <dt>{@code !isDocType.equals("") && !isNotDocType.equals("") && StringUtils.containsIgnoreCase(isDocType, docType)
     *                 && !StringUtils.containsIgnoreCase(isNotDocType, docType)}</dt>
     * <dd>both were specified</dd>
     * </dl>
     */
    private String findTitleDefinition(String title, String docType, String isDocType, String isNotDocType) {
        if ((isDocType.isEmpty()
                && (isNotDocType.isEmpty() || !StringUtils.containsIgnoreCase(isNotDocType, docType)))
                || (!isDocType.isEmpty() && !isNotDocType.isEmpty()
                        && StringUtils.containsIgnoreCase(isDocType, docType)
                        && !StringUtils.containsIgnoreCase(isNotDocType, docType))
                || (isNotDocType.isEmpty() && StringUtils.containsIgnoreCase(isDocType, docType))) {
            return title;
        }
        return "";
    }

    private boolean isTitleDefinitionFound(String titleDefinition) {
        return !titleDefinition.isEmpty();
    }

    private String processNullValues(String value) {
        if (Objects.isNull(value)) {
            value = "";
        }
        return value;
    }
}
