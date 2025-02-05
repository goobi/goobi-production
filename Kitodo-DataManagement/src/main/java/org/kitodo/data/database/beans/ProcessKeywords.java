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

package org.kitodo.data.database.beans;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.KitodoConfig;
import org.kitodo.data.database.enums.TaskStatus;

/**
 * Search keywords for a process.
 */
class ProcessKeywords {
    private static final Logger logger = LogManager.getLogger(ProcessKeywords.class);

    private static final String PSEUDOWORD_TASK_AUTOMATIC = "automatic";
    private static final String PSEUDOWORD_TASK_DONE = "closed";
    private static final String PSEUDOWORD_TASK_DONE_PROCESSING_USER = "closeduser";
    private static final String ANY_METADATA_MARKER = "mdWrap";
    private static final char VALUE_SEPARATOR = 'q';

    private static final Pattern TITLE_GROUPS_PATTERN = Pattern.compile("[\\p{IsLetter}\\p{Digit}]+");
    private static final Pattern METADATA_PATTERN = Pattern.compile("name=\"([^\"]+)\">([^<]*)<", Pattern.DOTALL);
    private static final Pattern METADATA_SECTIONS_PATTERN = Pattern.compile("<mets:dmdSec.*?o>(.*?)</kitodo:k",
        Pattern.DOTALL);
    private static final Pattern RULESET_KEY_PATTERN = Pattern.compile("key id=\"([^\"]+)\">(.*?)</key>",
        Pattern.DOTALL);
    private static final Pattern RULESET_LABEL_PATTERN = Pattern.compile("<label[^>]*>([^<]+)", Pattern.DOTALL);

    private static final Map<String, Map<String, Collection<String>>> rulesetCache = new HashMap<>();

    private final Set<String> defaultKeywords;
    private final Set<String> titleKeywords;
    private final Set<String> projectKeywords;
    private final Set<String> batchKeywords;
    private final Set<String> taskKeywords;
    private final Set<String> taskPseudoKeywords;

    public ProcessKeywords(Process process) {
        // keywords for title search + default search
        this.titleKeywords = filterMinLength(initTitleKeywords(process.getTitle()));

        // keywords for project search + default search
        String projectTitle = Objects.nonNull(process.getProject()) ? process.getProject().getTitle() : "";
        this.projectKeywords = filterMinLength(initSimpleKeywords(projectTitle));

        // keywords for batch search + default search
        this.batchKeywords = filterMinLength(initBatchKeywords(process.getBatches()));

        // keywords for task search, partly in default search
        var taskKeywords = initTaskKeywords(process.getTasksUnmodified());
        this.taskKeywords = filterMinLength(taskKeywords.getLeft()); // in default
        this.taskPseudoKeywords = filterMinLength(taskKeywords.getRight());

        // more keywords for default search only
        this.defaultKeywords = new HashSet<String>();
        if (Objects.nonNull(process.getId())) {
            defaultKeywords.add(process.getId().toString());
        }
        defaultKeywords.addAll(filterMinLength(initCommentKeywords(process.getComments())));
        defaultKeywords.addAll(filterMinLength(initMetadataKeywords(process)));

        if (logger.isTraceEnabled()) {
            logKeywords(process.getId());
        }
    }

    /**
     * Creates search terms for process titles. To do this, the process title is
     * separated into groups of consecutive characters and numbers, and these
     * are generated in their entirety and starting from the front or from the
     * back. The latter is more common, as the last four digits of a PPN are
     * used for the search and the hit rate is excellent.
     * 
     * @param processTitle
     *            the title of the process
     * @return keywords
     */
    private static Set<String> initTitleKeywords(String processTitle) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TITLE_GROUPS_PATTERN.matcher(processTitle);
        while (matcher.find()) {
            String normalized = normalize(matcher.group());
            final int length = normalized.length();
            // starting from the beginning
            for (int end = 1; end <= length; end++) {
                tokens.add(normalized.substring(0, end));
            }
            // ending at the end
            for (int beginning = length - 1; beginning >= 0; beginning--) {
                tokens.add(normalized.substring(beginning, length));
            }
        }
        return tokens;
    }

    /**
     * Makes the keywords for searching by a string. These are just single
     * words.
     * 
     * @param input
     *            the input string
     * @return keywords
     */
    private static final Set<String> initSimpleKeywords(String input) {
        Set<String> tokens = new HashSet<>();
        for (String term : splitValues(input)) {
            tokens.add(normalize(term));
        }
        return tokens;
    }

    /**
     * Generates the search terms by batch. Note that batch title can be
     * optional. Batch ID is not indexed because ID search is done via the
     * database.
     * 
     * @param batches
     *            batches containing the process
     * @return batch search terms
     */
    private static final Set<String> initBatchKeywords(Collection<Batch> batches) {
        if (batches.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> tokens = new HashSet<>();
        for (Batch batch : batches) {
            String optionalTitle = batch.getTitle();
            if (StringUtils.isNotBlank(optionalTitle)) {
                tokens.addAll(initSimpleKeywords(optionalTitle));
            }
        }
        return tokens;
    }

    /**
     * Generates all search terms by task and pseudo search terms.
     * 
     * @param tasks
     *            tasks for the words to be generated
     * @return search terms and pseudo search terms
     */
    private static final Pair<Set<String>, Set<String>> initTaskKeywords(Collection<Task> tasks) {
        Set<String> taskKeywords = new HashSet<>();
        Set<String> taskPseudoKeywords = new HashSet<>();
        for (Task task : tasks) {
            for (String token : splitValues(task.getTitle())) {
                String term = normalize(token);
                taskKeywords.add(term);
                if (task.isTypeAutomatic()) {
                    taskKeywords.add(PSEUDOWORD_TASK_AUTOMATIC + VALUE_SEPARATOR + term);
                }
                TaskStatus taskStatus = task.getProcessingStatus();
                if (Objects.isNull(taskStatus)) {
                    continue;
                }
                if (Objects.equals(taskStatus, TaskStatus.DONE)) {
                    taskPseudoKeywords.add(PSEUDOWORD_TASK_DONE);
                    taskPseudoKeywords.add(PSEUDOWORD_TASK_DONE + VALUE_SEPARATOR + term);
                    User closedUser = task.getProcessingUser();
                    if (Objects.isNull(closedUser)) {
                        continue;
                    }
                    if (StringUtils.isNotBlank(closedUser.getName())) {
                        taskPseudoKeywords.add(PSEUDOWORD_TASK_DONE_PROCESSING_USER + VALUE_SEPARATOR
                            + normalize(closedUser.getName()));
                    }
                    if (StringUtils.isNotBlank(closedUser.getSurname())) {
                        taskPseudoKeywords.add(PSEUDOWORD_TASK_DONE_PROCESSING_USER + VALUE_SEPARATOR
                            + normalize(closedUser.getSurname()));
                    }
                } else {
                    String taskKeyword = taskStatus.toString().toLowerCase();
                    taskPseudoKeywords.add(taskKeyword);
                    taskPseudoKeywords.add(taskKeyword + VALUE_SEPARATOR + term);
                }
            }
        }
        return Pair.of(taskKeywords, taskPseudoKeywords);
    }

    /**
     * Generates all metadata keywords and pseudowords for metadata of a METS
     * file KITODO-metadata.
     * 
     * @param process
     *            process of the METS file
     * @param placeDebug
     *            what the metadata is read for, only for debug log messages.
     *            Can be null if logging is disabled on the debug level.
     * @return metadata keywords, and metadata pseudo keywords
     */
    private static final Set<String> initMetadataKeywords(Process process) {
        try {
            String processId = Integer.toString(process.getId());
            Path path = Paths.get(KitodoConfig.getKitodoDataDirectory(), processId, "meta.xml");
            if (!Files.isReadable(path)) {
                logger.info((Files.exists(path) ? "File not readable for indexing: "
                        : "Missing metadata file for indexing: ") + path);
                return Collections.emptySet();
            }
            logger.debug("Indexing {} in process {} \"{}\"", path, process.getId(), process.getTitle());
            String metaXml = FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8);
            if (!metaXml.contains(ANY_METADATA_MARKER)) {
                return Collections.emptySet();
            }
            Set<String> result = new HashSet<>();
            Map<String, Collection<String>> rulesetLabelMap = getRulesetLabelMap(process.getRuleset().getFile());
            Matcher metadataSectionsMatcher = METADATA_SECTIONS_PATTERN.matcher(metaXml);
            while (metadataSectionsMatcher.find()) {
                Matcher keyMatcher = METADATA_PATTERN.matcher(metadataSectionsMatcher.group(1));
                while (keyMatcher.find()) {
                    String key = normalize(keyMatcher.group(1));
                    String valueString = keyMatcher.group(2);
                    for (String singleValue : splitValues(valueString)) {
                        String value = normalize(singleValue);
                        result.add(value);
                        result.add(key + VALUE_SEPARATOR + value);
                        result.add(key);
                        for (String label : rulesetLabelMap.getOrDefault(key, Collections.emptyList())) {
                            result.add(label + VALUE_SEPARATOR + value);
                            result.add(label);
                        }
                    }
                }
            }
            return result;
        } catch (IOException | RuntimeException e) {
            logger.catching(e instanceof FileNotFoundException ? Level.INFO : Level.WARN, e);
            return Collections.emptySet();
        }
    }

    /**
     * Returns a map for ruleset key translations. A cache is used, but if there
     * is nothing in cache, the ruleset is parsed and the map is created. Since
     * Kitodo-DataEditor is not available here, we have to do this directly, and
     * it also increases performance massively.
     * 
     * @param file
     *            indicates a ruleset
     * @return a map
     */
    private static Map<String, Collection<String>> getRulesetLabelMap(String file) {
        Map<String, Collection<String>> rulesetLabelMap = rulesetCache.get(file);
        if (Objects.nonNull(rulesetLabelMap)) {
            return rulesetLabelMap;
        }
        try {
            File rulesetFile = Paths.get(KitodoConfig.getParameter("directory.rulesets"), file).toFile();
            logger.debug("Reading {} ...", rulesetFile);
            String ruleset = FileUtils.readFileToString(rulesetFile, StandardCharsets.UTF_8);
            rulesetLabelMap = new HashMap<>();
            Matcher keysMatcher = RULESET_KEY_PATTERN.matcher(ruleset);
            while (keysMatcher.find()) {
                String key = normalize(keysMatcher.group(1));
                Matcher labelMatcher = RULESET_LABEL_PATTERN.matcher(keysMatcher.group(2));
                Set<String> labels = new HashSet<>();
                while (labelMatcher.find()) {
                    labels.add(normalize(labelMatcher.group(1)));
                }
                rulesetLabelMap.put(key, labels);
                if (logger.isTraceEnabled()) {
                    logger.trace("- {} -> {}", key, String.join(", ", labels));
                }
            }
            rulesetCache.put(file, rulesetLabelMap);
            return rulesetLabelMap;
        } catch (IOException | RuntimeException e) {
            logger.catching(Level.WARN, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Creates the keywords for searching in correction messages.
     * 
     * @param comments
     *            the comments of a process
     * @return keywords
     */
    private static final Set<String> initCommentKeywords(List<Comment> comments) {
        Set<String> tokens = new HashSet<>();
        for (Comment comment : comments) {
            String message = comment.getMessage();
            if (StringUtils.isNotBlank(message)) {
                tokens.addAll(initSimpleKeywords(message));
            }
        }
        return tokens;
    }

    /**
     * Converts the string to lowercase and removes special characters.
     * 
     * @param string
     *            string to clean
     * @return clean string in lowercase
     */
    private static String normalize(String string) {
        return string.toLowerCase().replaceAll("[\0-/:-`{-¿]", "");
    }

    /**
     * Splits the values ​​of a string at special characters. Groups of letters
     * and numbers written together are not split.
     * 
     * @param value
     *            string to split
     * @return groups
     */
    private static List<String> splitValues(String value) {
        String initializedValue = value != null ? value : "";
        return Arrays.asList(initializedValue.split("[ ,\\-._]+"));
    }

    /**
     * Filter minimum-length tokens. Only tokens at least three characters long
     * should be indexed, because you'll never search for tokens that are too
     * short anyway, but it would bloat the index a lot.
     * 
     * @param tokens
     *            input set, is changed!
     * @return input set
     */
    private static Set<String> filterMinLength(Set<String> tokens) {
        for (Iterator<String> iterator = tokens.iterator(); iterator.hasNext();) {
            if (iterator.next().length() < 3) {
                iterator.remove();
            }
        }
        return tokens;
    }

    /**
     * Returns the search keywords for the free search. These are search
     * keywords for title terms, the title of the project, names of assigned
     * batches, task names, and metadata. For searching the metadata, there are
     * both the bare terms and pseudowords to particularly powerfully search the
     * various metadata keys.
     * 
     * <p>
     * Suppose there is metadata with the key "TitleDocMain" and a value
     * containing the string "Berlin, Charlottenburg". In the ruleset,
     * "TitleDocMain" is translated as "Maint title" and "Hauptsachtitel". This
     * produces the following pseudo search terms in addition to the search term
     * "berlin":
     * <ul>
     * <li>{@code titledocmainqberlin} - for search "TitleDocMain:berlin"</li>
     * <li>{@code mainttitleqberlin} - for search "Maint title:berlin"</li>
     * <li>{@code hauptsachtitelqberlin} - for search
     * "Hauptsachtitel:berlin"</li>
     * <li>and, formed according to the same scheme, {@code charlottenburg} and
     * its psodowords.
     * </ul>
     * This means that if a user searches for "Maint title:Berlin,
     * Charlottenburg", the index has to search for:
     * {@code mainttitleqberlin mainttitleqcharlottenburg}.
     * 
     * @return search keywords for the free search
     */
    public String getSearch() {
        Set<String> freeKeywords = new HashSet<>(initialHashMapSize(defaultKeywords, titleKeywords, projectKeywords,
            batchKeywords, taskKeywords));
        freeKeywords.addAll(defaultKeywords);
        freeKeywords.addAll(titleKeywords);
        freeKeywords.addAll(projectKeywords);
        freeKeywords.addAll(batchKeywords);
        freeKeywords.addAll(taskKeywords);
        return String.join(" ", freeKeywords);
    }

    /**
     * Returns the search keywords for the title search. The title is sequenced
     * in a meaningful way to achieve meaningful hits even with the substring
     * search.
     * 
     * <p>
     * A process with title "PineSeve_313539383" would be searchable as: pin,
     * pine, pines, pinese, pinesev, pineseve, ineseve, eseve, seve, eve, 313,
     * 3135, 31353, 313539, 3135393, 31353938, 313539383, 13539383, 3539383,
     * 539383, 39383, 9383, 383.
     * 
     * @return search keywords for the title
     */
    public String getSearchTitle() {
        return String.join(" ", titleKeywords);
    }

    /**
     * Returns the search keywords for the project name search. These are the
     * words from the project name in normalized form.
     * 
     * @return search keywords for the project
     */
    public String getSearchProject() {
        return String.join(" ", projectKeywords);
    }

    /**
     * Returns the search keywords for searching for operations assigned to a
     * batch. The same splitting criteria apply as for the title.
     * 
     * @return search keywords for batches
     */
    public String getSearchBatch() {
        return String.join(" ", batchKeywords);
    }

    /**
     * Returns search keywords for finding tasks. This uses pseudowords to
     * particularly powerfully search the various task states.
     * 
     * <p>
     * Given an automated task called "OCR" and the task is running, it
     * generates the tokens: {@code ocr}, {@code automaticqocr},
     * {@code inworkqocr}.
     * 
     * <p>
     * If a task "Quality Assurance" is finished and it was processed by John
     * Doe, the token results: {@code quality}, {@code assurance},
     * {@code closedqquality}, {@code closedqassurance},
     * {@code closeduserqjohn}, {@code closeduserqdoe}.
     * 
     * @return search keywords for tasks
     */
    public String getSearchTask() {
        Set<String> allTaskKeywords = new HashSet<>(initialHashMapSize(taskKeywords, taskPseudoKeywords));
        allTaskKeywords.addAll(taskKeywords);
        allTaskKeywords.addAll(taskPseudoKeywords);
        return String.join(" ", allTaskKeywords);
    }

    private static int initialHashMapSize(Collection<?>... collections) {
        final double hashMapDefaultLoadFactor = 0.75;
        int items = 0;
        for (Collection<?> collection : collections) {
            items += collection.size();
        }
        return (int) Math.ceil(items / hashMapDefaultLoadFactor);
    }

    /**
     * Writes the keywords to an output file in the metadata directory of the
     * process. This is only done if the log level for this class is set to
     * TRACE.
     * 
     * @param keywordsLogfile
     *            file name (without path)
     */
    private final void logKeywords(Integer processId) {
        try {
            File log = Paths.get(KitodoConfig.getKitodoDataDirectory(), Objects.toString(processId), "keywords.log").toFile();
            traceLogCollection("[defaultKeywords]", defaultKeywords, log, true);
            traceLogCollection("[titleKeywords]", titleKeywords, log, false);
            traceLogCollection("[projectKeywords]", projectKeywords, log, true);
            traceLogCollection("[batchKeywords]", batchKeywords, log, true);
            traceLogCollection("[taskKeywords]", taskKeywords, log, true);
            traceLogCollection("[taskPseudoKeywords]", taskPseudoKeywords, log, true);
            logger.trace("Keywords logged to {}", log);
        } catch (RuntimeException | IOException e) {
            logger.catching(Level.TRACE, e);
        }
    }

    private static final void traceLogCollection(String caption, Collection<String> tokens, File log, boolean append)
            throws IOException {
        FileUtils.write(log, caption.concat(System.lineSeparator()), StandardCharsets.UTF_8, append);
        FileUtils.writeLines(log, StandardCharsets.UTF_8.toString(), new TreeSet<>(tokens), true);
        FileUtils.write(log, System.lineSeparator(), StandardCharsets.UTF_8, true);
    }
}
