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

package org.kitodo.production.services.command;

// static functions used
import static java.lang.System.lineSeparator;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.kitodo.constants.StringConstants.CREATE;

// base Java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// open source code
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.dataformat.mets.LinkedMetsResource;
import org.kitodo.api.validation.State;
import org.kitodo.api.validation.ValidationResult;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.InvalidImagesException;
import org.kitodo.exceptions.MediaNotFoundException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.process.NewspaperProcessesGenerator;
import org.kitodo.production.process.ProcessGenerator;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.data.TaskService;
import org.kitodo.production.services.dataformat.MetsService;
import org.kitodo.production.services.file.FileService;
import org.kitodo.production.services.validation.MetadataValidationService;

/**
 * A process to import. For each process to be imported (that is, a process
 * directory with a {@code meta.xml} file and sub-folders with media files),
 * such an object is created, that imports this process into the application
 * logic. The existence of the {@code meta.xml} file is checked and its content
 * is validated against the specified ruleset. In the event of an error, an
 * error information text file is written. Furthermore, a special treatment
 * represents the handling of child processes.
 */
final class ImportingProcess {
    private static final Logger logger = LogManager.getLogger(ImportingProcess.class);

    // Constants
    private static final String META_FILE_NAME = "meta.xml";

    // Services in use
    private final FileService fileService = ServiceManager.getFileService();
    private final MetadataValidationService metadataValidationService = ServiceManager.getMetadataValidationService();
    private final MetsService metsService = ServiceManager.getMetsService();
    private final ProcessService processService = ServiceManager.getProcessService();
    private final TaskService taskService = ServiceManager.getTaskService();

    /*
     * This class makes extensive use of global variables (fields). This is due
     * to the fact that the methods of the class do not run from front to back,
     * but the sub-steps are called individually by the task manager in order to
     * implement the ability to stop and restart the task.
     */

    // Input directories and files
    private Path sourceDir;
    final String directoryName;
    private List<Path> filesAndDirectories = new ArrayList<>();
    private int numberOfFileSystemItems;
    private Iterator<Path> filesAndDirectoriesIterator;

    // Process hierarchy
    private Map<String, ImportingProcess> importingProcesses;
    private ImportingProcess parent;
    private final List<ImportingProcess> children = new ArrayList<>();

    // Errors
    private Set<String> sharedErroneousProcesses = null;
    final List<String> errors = new ArrayList<>();

    // database process
    private Project project;
    private Template template;
    private String processTitleRule;
    private String title;
    private String baseType;
    private Integer processId;

    // Output directories
    private Path copyToRoot;
    private Path outputDir;

    /**
     * <b>Constructor.</b><!-- --> Creates a new Importing Process.
     * 
     * @param sourceDir
     *            source directory of process to import
     */
    ImportingProcess(Path sourceDir) {
        this.sourceDir = sourceDir;
        this.directoryName = sourceDir.getFileName().toString();
        try (Stream<Path> pathStream = Files.walk(sourceDir)) {
            for (Path entry : (Iterable<Path>) pathStream::iterator) {
                if (!entry.equals(sourceDir)) {
                    filesAndDirectories.add(sourceDir.relativize(entry));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        numberOfFileSystemItems = filesAndDirectories.size();
    }

    /**
     * Specifies the number of actions required to import this process. This is
     * used to display the progress bar in Task Manager.
     * 
     * <p>
     * One action is the validation. One action is to create the directory for
     * the import. One action is either creating the process in the database
     * <i>or</i> writing the error file. One action each is creating a
     * sub-directory or copying a file.
     * 
     * @return sum of actions needed
     */
    int numberOfActions() {
        int result = numberOfFileSystemItems + 3;
        if (logger.isTraceEnabled()) {
            logger.trace("Import folder {}: {} actions required. List: {}", this.directoryName, result,
                filesAndDirectories.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        }
        return result;
    }

    /**
     * Validates the METS file against the ruleset, generates the process title,
     * and checks for the existence of all child processes listed. Validation is
     * performed extensively to uncover and report as many bugs as possible in
     * one run.
     * 
     * @param ruleset
     *            the ruleset to validate against
     * @param strictValidation
     *            whether warnings are an error
     * @param importingProcesses
     *            access to all processes in this import run, to confirm the
     *            presence of child processes
     */
    void validate(RulesetManagementInterface ruleset, boolean strictValidation,
            Map<String, ImportingProcess> importingProcesses) throws IOException, DAOException {

        this.importingProcesses = importingProcesses;
        logger.info("Starting to validate " + this.directoryName);
        Path metaFilePath = sourceDir.resolve(META_FILE_NAME);
        Workpiece workpiece = metsService.loadWorkpiece(metaFilePath.toUri());
        validateMetsFile(ruleset, strictValidation, workpiece);
        validateChildren(workpiece.getLogicalStructure());
        if (!errors.isEmpty()) {
            getSharedErroneousProcesses().add(this.directoryName);
        }

        // determine data for process object
        baseType = workpiece.getLogicalStructure().getType();
        title = formProcessTitle(ruleset, workpiece);

        logger.info("Validation of " + this.directoryName + (errors.isEmpty() ? " completed without errors"
                : " completed with errors:" + lineSeparator() + String.join(lineSeparator(), errors)));
    }

    /**
     * Validates the METS file against the ruleset. Possible rule violations
     * will be reported.
     * 
     * @param ruleset
     *            the ruleset to validate against
     * @param strictValidation
     *            whether warnings are an error
     * @param workpiece
     *            the workpiece to be validated
     */
    private void validateMetsFile(RulesetManagementInterface ruleset, boolean strictValidation, Workpiece workpiece)
            throws DAOException {
        ValidationResult validationResult = metadataValidationService.validate(workpiece, ruleset, false);
        State state = validationResult.getState();
        if (State.ERROR.equals(state) || (strictValidation && !State.SUCCESS.equals(state))) {
            errors.add(Helper.getTranslation("dataEditor.validation.state.error").concat(":"));
            for (String resultMessage : validationResult.getResultMessages()) {
                errors.add(" - ".concat(resultMessage));
            }
            logger.info(String.join(System.lineSeparator(), errors));
        }
    }

    /**
     * Checks for the presence of linked child processes. If not, then this will
     * break this process, but also any existing child processes.
     * 
     * @param logicalStructure
     *            in the logical structure of the process, the children are
     *            listed, if there are any
     */
    private void validateChildren(LogicalDivision logicalStructure) {
        Set<String> linkedChildren = searchLinkedProcesses(logicalStructure).keySet();
        List<String> problemChildren = linkedChildren.parallelStream().filter(not(importingProcesses::containsKey))
                .collect(toList());
        if (!problemChildren.isEmpty()) {
            String errorMessage = Helper.getTranslation("kitodoScript.importProcesses.missingChildren",
                this.directoryName, String.join(", ", problemChildren));
            this.errors.add(errorMessage);
        }
        for (String linkedChild : linkedChildren) {
            ImportingProcess importingChild = importingProcesses.get(linkedChild);
            if (Objects.nonNull(importingChild)) {
                this.children.add(importingChild);
                importingChild.parent = this;
            }
        }
    }

    /**
     * Recursively determines the existence of linked child processes and
     * returns any found ones.
     * 
     * @param division
     *            division under which the search is made
     * @return found references, with name and the division under which it is
     *         located
     */
    private Map<String, LogicalDivision> searchLinkedProcesses(LogicalDivision division) {
        Map<String, LogicalDivision> result = new HashMap<>();
        if (division.getChildren().isEmpty()) {
            if (Objects.nonNull(division.getLink())) {
                String name = division.getLink().getUri().toString();
                int firstEqualsSign = name.indexOf('=');
                if (firstEqualsSign > -1) {
                    name = name.substring(firstEqualsSign + 1);
                }
                result.put(name, division);
            }
        } else {
            for (LogicalDivision child : division.getChildren()) {
                result.putAll(searchLinkedProcesses(child));
            }
        }
        return result;
    }

    /**
     * Forms the process title. To do this, the formation rule is obtained from
     * the ruleset, and then the composition is executed.
     * 
     * @param ruleset
     *            the ruleset to validate against
     * @param workpiece
     *            the workpiece to be validated
     * @return the process title. May be {@code null} if the process title
     *         cannot yet be formed, because it requires a parent process that
     *         has not yet been initialized
     */
    private String formProcessTitle(RulesetManagementInterface ruleset, Workpiece workpiece) {
        Optional<String> processTitleAttribute = ruleset
                .getStructuralElementView(baseType, CREATE, NewspaperProcessesGenerator.ENGLISH).getProcessTitle();
        if (processTitleAttribute.isEmpty()) {
            errors.add(Helper.getTranslation("kitodoScript.importProcesses.noProcessTitleRule", baseType));
            return null;
        }
        processTitleRule = processTitleAttribute.get();
        if (!processTitleRule.startsWith("+") || (parent != null && parent.title != null)) {
            String processTitle = calculateProcessTitle(processTitleRule, parent == null ? null : parent.title,
                workpiece.getLogicalStructure().getMetadata());
            processTitleRule = null;
            return processTitle;
        }
        return null;
    }

    /**
     * Calculates the process title.
     * 
     * @param rule
     *            formation rule
     * @param parentTitle
     *            title of the parent process
     * @param metadata
     *            metadata of the current process
     * @return the process title
     */
    private String calculateProcessTitle(String rule, String parentTitle, Collection<Metadata> metadata) {
        List<String> components = Arrays.asList(rule.split("\\+"));
        for (int i = 0; i < components.size(); i++) {
            String component = components.get(i);
            if (i == 0 && component.isEmpty()) {
                components.set(i, parentTitle);
            } else if (component.matches("'.*'")) {
                components.set(i, component.substring(1, component.length() - 1));
            } else {
                Optional<String> lookedUpValue = metadata.parallelStream().filter(MetadataEntry.class::isInstance)
                        .filter(metadataEntry -> metadataEntry.getKey().equals(component))
                        .map(MetadataEntry.class::cast).map(MetadataEntry::getValue).findAny();
                if (lookedUpValue.isPresent()) {
                    components.set(i, lookedUpValue.get());
                }
            }
        }
        return String.join("", components);
    }

    /**
     * Returns whether the process to import successfully validated.
     * 
     * @return whether the process is correct
     */
    boolean isCorrect() {
        getSharedErroneousProcesses();
        if (!errors.isEmpty()) {
            sharedErroneousProcesses.add(this.directoryName);
        }
        return sharedErroneousProcesses.isEmpty();
    }

    private Set<String> getSharedErroneousProcesses() {
        if (Objects.isNull(sharedErroneousProcesses)) {
            setSharedErroneousProcesses(
                Objects.isNull(parent) ? new HashSet<>() : parent.getSharedErroneousProcesses());
        }
        return sharedErroneousProcesses;
    }

    private void setSharedErroneousProcesses(Set<String> sharedErroneousProcesses) {
        this.sharedErroneousProcesses = sharedErroneousProcesses;
        for (ImportingProcess importingChild : children) {
            importingChild.setSharedErroneousProcesses(sharedErroneousProcesses);
        }
    }

    // === PROCESSING ===

    /**
     * Creates the directory where the process should be copied to. It can be
     * {@code null} in case of a validation error, then the faulty process is
     * not copied anywhere.
     * 
     * @param copyToRoot
     *            copy target
     */
    void setOutputRoot(Path copyToRoot) throws IOException {
        this.copyToRoot = copyToRoot;
    }

    /**
     * Executes a processing step. The method is called from the Task Manager
     * once per processing step. Because validation is called separately, the
     * number of calls here is one less than the number returned by
     * {@link #numberOfActions()}. In other words, {@code action} must be in the
     * range 0 to {@code (numberOfActions() - 2)}.
     * 
     * @param action
     *            processing step
     * @throws IOException
     *             if accessing the file system fails
     * @throws DAOException
     *             if accessing the database fails
     * @throws DataException
     *             if accessing the database fails
     * @throws ProcessGenerationException
     *             if creating the process fails
     * @throws InvalidImagesException
     *             if media file names in the METS file cannot be aligned with
     *             the project configuration
     * @throws MediaNotFoundException
     *             if media files are missing
     */
    void executeAction(int action) throws IOException, DAOException, DataException, ProcessGenerationException,
            MediaNotFoundException, InvalidImagesException {

        assert action >= 0 && action <= numberOfFileSystemItems + 1
                : "action out of range: " + action + " [0.." + (numberOfFileSystemItems + 1) + "]";
        if (isCorrect()) {
            executeActionForCorrectProcess(action);
        } else if (Objects.nonNull(copyToRoot)) {
            executeActionForErroreousProcess(action);
        }
    }

    /**
     * Performs a processing step on a successfully validated process. First,
     * the process is created in the database to get its ID, which is the name
     * of the process directory. In the second step, this directory is created.
     * Then all files will be copied into it, except for the {@code meta.xml},
     * which will be skipped. The {@code meta.xml} is transferred at the end and
     * adjusted while doing so.
     *
     * @param action
     *            processing step
     */
    private void executeActionForCorrectProcess(int action) throws IOException, DataException,
            ProcessGenerationException, DAOException, InvalidImagesException, MediaNotFoundException {

        if (action == 0) {
            if (Objects.nonNull(processTitleRule) && Objects.isNull(title)) {
                Workpiece workpiece = metsService.loadWorkpiece(sourceDir.resolve(META_FILE_NAME).toUri());
                title = calculateProcessTitle(processTitleRule, parent.title,
                    workpiece.getLogicalStructure().getMetadata());
            }
            processId = createDatabaseProcess();
            logger.info("Created process #" + processId);
        } else if (action == 1) {
            createBaseDirectory(processId.toString());
            filesAndDirectoriesIterator = filesAndDirectories.iterator();
        } else if (filesAndDirectoriesIterator.hasNext()) {
            Path relativeItemToCopy = filesAndDirectoriesIterator.next();
            if (relativeItemToCopy.toString().equals(META_FILE_NAME)) {
                if (filesAndDirectoriesIterator.hasNext()) {
                    relativeItemToCopy = filesAndDirectoriesIterator.next();
                } else {
                    Process process = processService.getById(processId);
                    copyAndAdjustMetsFile(process);
                    processService.save(process);
                }
            } else {
                copyDirectoryOrFile(relativeItemToCopy);
            }
        } else {
            Process process = processService.getById(processId);
            copyAndAdjustMetsFile(process);
            processService.save(process);
        }
    }

    /**
     * Creates a process in the database. The process title must have already
     * been generated during validation.
     * 
     * @return database number of the created process
     */
    private Integer createDatabaseProcess() throws ProcessGenerationException, DataException {
        ProcessGenerator processGenerator = new ProcessGenerator();
        processGenerator.generateProcess(template.getId(), project.getId());
        Process process = processGenerator.getGeneratedProcess();
        process.setTitle(title);
        process.setBaseType(baseType);
        processService.save(process);
        for (Task task : process.getTasks()) {
            taskService.save(task);
        }
        return process.getId();
    }

    /**
     * Transfers and adapts the {@code meta.xml}. The new process number is
     * stored, the links are updated with the new IDs of the imported children,
     * and media files are added.
     * 
     * @param process
     *            the newly created process
     */
    private void copyAndAdjustMetsFile(Process process)
            throws IOException, InvalidImagesException, MediaNotFoundException, DAOException, DataException {

        Workpiece workpiece = metsService.loadWorkpiece(sourceDir.resolve(META_FILE_NAME).toUri());
        workpiece.setId(processId.toString());
        Map<String, LogicalDivision> linkedChildren = searchLinkedProcesses(workpiece.getLogicalStructure());
        for (Entry<String, LogicalDivision> linkedChild : linkedChildren.entrySet()) {
            LinkedMetsResource link = linkedChild.getValue().getLink();
            link.setLoctype("Kitodo.Production");
            ImportingProcess importedChildProcess = importingProcesses.get(linkedChild.getKey());
            link.setUri(processService.getProcessURI(importedChildProcess.processId));
            addLinkInDatabase(process, importedChildProcess.processId);
        }
        fileService.searchForMedia(process, workpiece);
        Path outputMetsFile = outputDir.resolve(META_FILE_NAME);
        metsService.saveWorkpiece(workpiece, outputMetsFile.toUri());
        logger.info("Wrote METS file " + outputMetsFile);
    }

    private void addLinkInDatabase(Process parent, Integer childProcessId) throws DAOException, DataException {
        Process child = processService.getById(childProcessId);
        child.setParent(parent);
        parent.getChildren().add(child);
        processService.save(child);
    }

    /**
     * Processes a faulty process. The output folder will be created in the
     * error target folder. The fault information file is written in it. And
     * then all the content is copied over.
     * 
     * @param action
     *            action to take. The processing does not take place in one run,
     *            but the function is called by the task thread once per step.
     *            This is necessary to ensure stopability and the progress
     *            display in the task manager.
     */
    private void executeActionForErroreousProcess(int action) throws IOException {
        if (action == 0) {
            createBaseDirectory(this.directoryName);
        } else if (action == 1) {
            String nameOfErrorFile = Helper.getTranslation("errors").concat(".txt");
            Path errorFile = outputDir.resolve(nameOfErrorFile);
            if (errors.isEmpty()) {
                String message = Helper.getTranslation("kitodoScript.importProcesses.brokenRelatedProcess",
                    String.join(", ", sharedErroneousProcesses));
                Files.writeString(errorFile, message, StandardCharsets.UTF_8);
            } else {
                Files.write(errorFile, errors, StandardCharsets.UTF_8);
            }
            logger.info("Wrote errors file " + errorFile);
        } else {
            copyDirectoryOrFile(filesAndDirectories.get(action - 2));
        }
    }

    /**
     * Determines the number of levels of children below this process.
     * 
     * @return number of levels of children below
     */
    int childDepth() {
        OptionalInt childrenMaxDepth = children.parallelStream().mapToInt(ImportingProcess::childDepth).max();
        if (childrenMaxDepth.isEmpty()) {
            return 0;
        } else {
            return childrenMaxDepth.getAsInt() + 1;
        }
    }

    // === UTILITIES ===

    /**
     * Creates the directory for the output.
     * 
     * @param directoryName
     *            the name for the directory
     */
    private void createBaseDirectory(String directoryName) throws IOException {
        outputDir = copyToRoot.resolve(directoryName);
        Files.createDirectories(outputDir);
        logger.info("Created process directory " + outputDir);
    }

    /**
     * Copies a directory (without content) or a file from the source directory
     * to the output directory.
     * 
     * @param relativeItem
     *            the relative path, relative to the respective root
     */
    private void copyDirectoryOrFile(Path relativeItem) throws IOException {
        Path sourceItem = sourceDir.resolve(relativeItem);
        Path destinationItem = outputDir.resolve(relativeItem);
        if (Files.isDirectory(sourceItem)) {
            Files.createDirectories(destinationItem);
            logger.info("Created directory " + destinationItem);
        } else {
            Files.copy(sourceItem, destinationItem, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
            logger.info("Copied " + sourceItem + " as " + destinationItem);
        }
    }

    void setProject(Project project) {
        this.project = project;
    }

    void setTemplate(Template template) {
        this.template = template;
    }

    @Override
    public String toString() {
        return "Importing process " + sourceDir;
    }
}
