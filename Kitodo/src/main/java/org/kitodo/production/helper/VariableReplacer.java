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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.KitodoConfig;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Task;
import org.kitodo.production.metadata.MetadataEditor;

/**
 * Replaces placeholders in a string. The variable replacer enables placeholders
 * specified in a character string to be replaced with internal values from the
 * process or the metadata of its workpiece. The character string is, for
 * example, the call of an external program.
 */
public class VariableReplacer {
    private static final Logger logger = LogManager.getLogger(VariableReplacer.class);

    /**
     * There are three different levels of access to the workpiece's metadata.
     */
    private enum MetadataLevel {
        /**
         * First, a metadata entry with the specified name is searched for in
         * the first division of the first subordinate hierarchy level; if it is
         * not found there, a search is then made in the top hierarchy level.
         */
        ALL,

        /**
         * A metadata entry with the specified name is only searched for in the
         * first division of the first subordinate hierarchy level.
         */
        FIRSTCHILD,

        /**
         * A metadata entry with the specified name is only searched for in the
         * top hierarchy level.
         */
        TOPSTRUCT
    }

    /**
     * This regular expression is used to search for placeholders that need to
     * be replaced.
     */
    private static final Pattern VARIABLE_FINDER_REGEX = Pattern.compile(
            "(\\$?)\\((?:(prefs|processid|processtitle|projectid|stepid|stepname|generatorsource|generatorsourcepath|ocrdworkflowid)|"
                    + "(?:(meta|process|product|template)\\.(?:(firstchild|topstruct)\\.)?([^)]+)|"
                    + "(?:(filename|basename|relativepath))))\\)");
    /**
     * The map is filled with replacement instructions that are required for
     * backwards compatibility with version 2.
     */
    private static Map<String, String> legacyVariablesMap;

    /**
     * The program builds a regular expression to search for outdated
     * replacement instructions and to replace them with the new instructions in
     * a first step.
     */
    private static Pattern legacyVariablesPattern;

    private Workpiece workpiece;
    private Process process;
    private Task task;

    /**
     * Creates a new Variable Replacer.
     *
     * @param workpiece
     *            Workpiece to read metadata values from
     * @param process
     *            Process to read values from
     * @param task
     *            Task to read values from
     */
    public VariableReplacer(Workpiece workpiece, Process process, Task task) {
        initializeLegacyVariablesPreprocessor();
        this.workpiece = workpiece;
        this.process = process;
        this.task = task;
    }

    /**
     * The method is called by the constructor of the class to load conversion
     * instructions for obsolete replacement patterns from the configuration
     * file.
     */
    private final void initializeLegacyVariablesPreprocessor() {
        StringBuilder regexBuilder = null;
        boolean useLegacyVariablesPreprocessor = false;
        for (Iterator<String> iterator = ConfigCore.getConfig().getKeys(); iterator.hasNext();) {
            String key = iterator.next();
            if (key.startsWith("variable.")) {
                String variableName = key.substring(9);
                if (useLegacyVariablesPreprocessor) {
                    regexBuilder.append('|');
                } else {
                    regexBuilder = new StringBuilder("\\((");
                    legacyVariablesMap = new HashMap<>();
                    useLegacyVariablesPreprocessor = true;
                }
                regexBuilder.append(Pattern.quote(variableName));
                legacyVariablesMap.put(variableName, ConfigCore.getParameter(key));
            }
        }
        if (useLegacyVariablesPreprocessor) {
            regexBuilder.append(")\\)");
            legacyVariablesPattern = Pattern.compile(regexBuilder.toString());
        }
    }

    /**
     * Replace variables within a string. Like an ant, run through the variables
     * and fetch them from the digital document.
     *
     * @param stringWithVariables
     *            a string maybe holding variables
     * @return string with variables replaced
     */
    public String replace(String stringWithVariables) {
        return replaceWithFilename(stringWithVariables,null);
    }
    
    /**
     * Replace variables withing a string. Like an ant, run through the variables
     * and fetch them from the digital document. Filename variables are replaced using the filename parameter.
     * 
     * @param stringWithVariables
     *             string with placeholders
     * @param filename
     *             filename for replacement
     *
     * @return string with replaced placeholders
     */
    public String replaceWithFilename(String stringWithVariables, String filename) {
        if (Objects.isNull(stringWithVariables)) {
            return "";
        }

        stringWithVariables = invokeLegacyVariableReplacer(stringWithVariables);

        Matcher variableFinder = VARIABLE_FINDER_REGEX.matcher(stringWithVariables);
        boolean stringChanged = false;
        StringBuffer replacedStringBuffer = null;
        while (variableFinder.find()) {
            if (!stringChanged) {
                replacedStringBuffer = new StringBuffer();
                stringChanged = true;
            }
            variableFinder.appendReplacement(replacedStringBuffer, determineReplacement(variableFinder, filename));
        }
        if (stringChanged) {
            variableFinder.appendTail(replacedStringBuffer);
            return replacedStringBuffer.toString();
        } else {
            return stringWithVariables;
        }
    }

    /**
     * Replaces outdated replacement patterns with appropriate ones.
     *
     * @param stringToReplace
     *            string (perhaps) containing obsolete replacement patterns
     * @return string in which obsolete replacement patterns have been replaced
     *         by appropriate ones
     */
    private String invokeLegacyVariableReplacer(String stringToReplace) {
        if (Objects.isNull(legacyVariablesPattern)) {
            return stringToReplace;
        }
        Matcher legacyVariablesMatcher = legacyVariablesPattern.matcher(stringToReplace);
        StringBuffer replacedLegaycVariablesBuffer = new StringBuffer();
        while (legacyVariablesMatcher.find()) {
            legacyVariablesMatcher.appendReplacement(replacedLegaycVariablesBuffer,
                legacyVariablesMap.get(legacyVariablesMatcher.group(1)));
        }
        legacyVariablesMatcher.appendTail(replacedLegaycVariablesBuffer);
        return replacedLegaycVariablesBuffer.toString();
    }

    /**
     * This method is called in the replacement loop to determine the
     * replacement value for a revealed variable.
     */
    private String determineReplacement(Matcher variableFinder, String filename) {
        if (Objects.nonNull(variableFinder.group(2))) {
            return determineReplacementForInternalValue(variableFinder);
        }
        if (Objects.nonNull(variableFinder.group(3))) {
            return determineReplacementForMetadata(variableFinder);
        }
        if (Objects.nonNull(variableFinder.group(6)) && Objects.nonNull(filename)) {
            return determineReplacementForFilePlaceholder(variableFinder, filename);
        }
        return variableFinder.group();
    }

    /**
     * If an internal value is to be determined, it is determined here.
     */
    private String determineReplacementForInternalValue(Matcher variableFinder) {
        switch (variableFinder.group(2)) {
            case "prefs":
                return determineReplacementForPrefs(variableFinder);
            case "processid":
                return determineReplacementForProcessid(variableFinder);
            case "processtitle":
                return determineReplacementForProcesstitle(variableFinder);
            case "projectid":
                return determineReplacementForProjectid(variableFinder);
            case "stepid":
                return determineReplacementForStepid(variableFinder);
            case "stepname":
                return determineReplacementForStepname(variableFinder);
            case "generatorsource" :
            case "generatorsourcepath":
                return determineReplacementForGeneratorSource(variableFinder, variableFinder.group(2));
            case "ocrdworkflowid":
                return determineReplacementForOcrdWorkflowId(variableFinder);
            default:
                logger.warn("Cannot replace \"{}\": no such case defined in switch", variableFinder.group());
                return variableFinder.group();
        }
    }

    private String determineReplacementForPrefs(Matcher variableFinder) {
        String rulesetsDirectory;
        try {
            rulesetsDirectory = ConfigCore.getParameter(ParameterCore.DIR_RULESETS);
        } catch (NoSuchElementException e) {
            logger.warn("Cannot replace \"(prefs)\": Missing configuration entry: directory.rulesets");
            return variableFinder.group(1);
        }
        if (Objects.isNull(process)) {
            logger.warn("Cannot replace \"(prefs)\": no process given");
            return variableFinder.group(1);
        }
        if (Objects.isNull(process.getRuleset())) {
            logger.warn("Cannot replace \"(prefs)\": process has no ruleset assigned");
            return variableFinder.group(1);
        }
        if (Objects.isNull(process.getRuleset().getFile())) {
            logger.warn("Cannot replace \"(prefs)\": process's ruleset has no file");
            return variableFinder.group(1);
        }
        return variableFinder.group(1) + rulesetsDirectory + process.getRuleset().getFile();
    }

    private String determineReplacementForProcessid(Matcher variableFinder) {
        if (Objects.isNull(process)) {
            logger.warn("Cannot replace \"(processid)\": no process given");
            return variableFinder.group(1);
        }
        return variableFinder.group(1) + process.getId().toString();
    }

    private String determineReplacementForOcrdWorkflowId(Matcher variableFinder) {
        if (Objects.isNull(process)) {
            logger.warn("Cannot replace \"(ocrdworkflowid)\": no process given");
            return variableFinder.group(1);
        }

        if (StringUtils.isNotEmpty(process.getOcrdWorkflowId())) {
            return variableFinder.group(1) + process.getOcrdWorkflowId();
        }

        if (Objects.isNull(process.getTemplate())) {
            logger.warn("Cannot replace \"(ocrdworkflowid)\": process has no template assigned");
            return variableFinder.group(1);
        }

        if (StringUtils.isEmpty(process.getTemplate().getOcrdWorkflowId())) {
            logger.warn("Cannot replace \"(ocrdworkflowid)\": template has no OCR-D workflow assigned");
            return variableFinder.group(1);
        }
        return variableFinder.group(1) + process.getTemplate().getOcrdWorkflowId();
    }

    private String determineReplacementForProcesstitle(Matcher variableFinder) {
        if (Objects.isNull(process)) {
            logger.warn("Cannot replace \"(processtitle)\": no process given");
            return variableFinder.group(1);
        }
        return variableFinder.group(1) + process.getTitle();
    }

    private String determineReplacementForProjectid(Matcher variableFinder) {
        if (Objects.isNull(process)) {
            logger.warn("Cannot replace \"(projectid)\": no process given");
            return variableFinder.group(1);
        }
        if (Objects.isNull(process.getProject())) {
            logger.warn("Cannot replace \"(projectid)\": process has no project assigned");
            return variableFinder.group(1);
        }
        return variableFinder.group(1) + process.getProject().getId();
    }

    private String determineReplacementForStepid(Matcher variableFinder) {
        if (Objects.isNull(task)) {
            logger.warn("Cannot replace \"(stepid)\": no task given");
            return variableFinder.group(1);
        }
        return variableFinder.group(1) + task.getId();
    }

    private String determineReplacementForStepname(Matcher variableFinder) {
        if (Objects.isNull(task)) {
            logger.warn("Cannot replace \"(stepname)\": no task given");
            return variableFinder.group(1);
        }
        return variableFinder.group(1) + task.getTitle();
    }

    private String determineReplacementForGeneratorSource(Matcher variableFinder, String match) {
        if (Objects.isNull(process)) {
            logger.warn("Cannot replace \"(" + match + ")\": no process given");
            return variableFinder.group(1);
        }
        if (Objects.isNull(process.getProject())) {
            logger.warn("Cannot replace \"(" + match + ")\": process has no project assigned");
            return variableFinder.group(1);
        }
        if (Objects.isNull(process.getProject().getGeneratorSource())) {
            logger.warn("Cannot replace \"(" + match + ")\": process has no generator source assigned");
            return variableFinder.group(1);
        }

        //Since image paths may contain variables themselves, use recursion
        String generatorSource = replace(String.valueOf(process.getProject().getGeneratorSource().getPath()));
        String replacedString = variableFinder.group(1);
        if (match.equals("generatorsource")) {
            replacedString += generatorSource;    
        }
        else if (match.equals("generatorsourcepath")) {
            replacedString += KitodoConfig.getKitodoDataDirectory() + process.getId() + "/" + generatorSource;
        }
        return replacedString;
    }

    /**
     * If a value is to be determined from the metadata, it is determined here.
     */
    private String determineReplacementForMetadata(Matcher variableFinder) {
        String dollarSignIfToKeep = variableFinder.group(1);
        MetadataLevel metadataLevel;
        if (variableFinder.group(3).equals("meta")) {
            if (dollarSignIfToKeep.isEmpty()) {
                return variableFinder.group();
            } else {
                dollarSignIfToKeep = "";
            }
            metadataLevel = Objects.isNull(variableFinder.group(4)) ? MetadataLevel.ALL
                    : MetadataLevel.valueOf(variableFinder.group(4).toUpperCase());
        } else {
            metadataLevel = MetadataLevel.TOPSTRUCT;
        }

        if (Objects.isNull(workpiece)) {
            logger.warn("Cannot replace \"{}\": no workpiece given", variableFinder.group());
            return dollarSignIfToKeep;
        }

        switch (metadataLevel) {
            case ALL:
                String allFirstchildValue = determinateReplacementForAll(variableFinder, dollarSignIfToKeep);

                if (Objects.nonNull(allFirstchildValue)) {
                    return allFirstchildValue;
                }
                // else fall through

            case TOPSTRUCT:
                return determineReplacementForTopstruct(variableFinder, dollarSignIfToKeep);

            case FIRSTCHILD:
                return determineReplacementForFirstchild(variableFinder, dollarSignIfToKeep);

            default:
                throw new IllegalStateException("complete switch");
        }
    }

    private String determinateReplacementForAll(Matcher variableFinder, String dollarSignIfToKeep) {
        List<LogicalDivision> allChildren = workpiece.getLogicalStructure().getChildren();
        String allFirstchildValue = null;
        if (!allChildren.isEmpty()) {
            allFirstchildValue = MetadataEditor.getMetadataValue(allChildren.get(0), variableFinder.group(5));
            if (Objects.isNull(allFirstchildValue)) {
                allFirstchildValue = determineReplacementForTopstruct(variableFinder, dollarSignIfToKeep);
            }
            if (StringUtils.isEmpty(allFirstchildValue)) {
                List<LogicalDivision> firstChildChildren = allChildren.get(0).getChildren();
                if (!firstChildChildren.isEmpty()) {
                    allFirstchildValue = MetadataEditor.getMetadataValue(firstChildChildren.get(0), variableFinder.group(5));
                }
            }
        }
        return allFirstchildValue;
    }

    private String determineReplacementForTopstruct(Matcher variableFinder, String failureResult) {
        String value = MetadataEditor.getMetadataValue(workpiece.getLogicalStructure(), variableFinder.group(5));
        if (Objects.isNull(value)) {
            logger.warn("Cannot replace \"{}\": No such metadata entry in the root element", variableFinder.group());
            return failureResult;
        }
        return value;
    }

    private String determineReplacementForFirstchild(Matcher variableFinder, String failureResult) {
        List<LogicalDivision> firstchildChildren = workpiece.getLogicalStructure().getChildren();
        if (firstchildChildren.isEmpty()) {
            logger.warn("Cannot replace \"{}\": Workpiece doesn't have subordinate logical divisions",
                variableFinder.group());
            return failureResult;
        }
        String value = MetadataEditor.getMetadataValue(firstchildChildren.get(0), variableFinder.group(5));
        if (Objects.isNull(value)) {
            logger.warn("Cannot replace \"{}\": No such metadata entry in the first division", variableFinder.group());
            return failureResult;
        }
        return value;
    }

    /**
    * Checks whether a string contains file variables.
     * 
     * @param stringWithVariables
     *             string to be checked for file variables
     * @return true if string contains file variables
     */
    public boolean containsFiles(String stringWithVariables) {
        if (Objects.isNull(stringWithVariables)) {
            return false;
        }
        return stringWithVariables.contains("(filename)") || stringWithVariables.contains("(basename)")
                || stringWithVariables.contains("(relativepath)");
    }

    /**
     * If a filename is to be determined, it is determined here.
     */
    private String determineReplacementForFilePlaceholder(Matcher variableFinder, String filename) {
        switch (variableFinder.group(6)) {
            case "filename":
                return variableFinder.group(1) + FilenameUtils.getName(filename);
            case "basename":
                return variableFinder.group(1) + FilenameUtils.getBaseName(filename);
            case "relativepath":
                return variableFinder.group(1) + filename;
            default:
                logger.warn("Cannot replace \"{}\": no such case defined in switch", variableFinder.group());
                return variableFinder.group();
        }
    }
}
