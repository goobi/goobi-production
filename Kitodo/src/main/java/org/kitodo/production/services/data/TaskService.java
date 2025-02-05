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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.command.CommandResult;
import org.kitodo.data.database.beans.Client;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Role;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.enums.TaskEditType;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.BaseDAO;
import org.kitodo.data.database.persistence.HibernateUtil;
import org.kitodo.data.database.persistence.TaskDAO;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.InvalidImagesException;
import org.kitodo.exceptions.MediaNotFoundException;
import org.kitodo.export.ExportDms;
import org.kitodo.production.enums.GenerationMode;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.VariableReplacer;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyPrefsHelper;
import org.kitodo.production.helper.tasks.EmptyTask;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.command.CommandService;
import org.kitodo.production.services.command.KitodoScriptService;
import org.kitodo.production.services.file.SubfolderFactoryService;
import org.kitodo.production.services.image.ImageGenerator;
import org.kitodo.production.services.workflow.WorkflowControllerService;
import org.primefaces.model.SortOrder;

/**
 * The class provides a service for tasks. The service can be used to perform
 * functions on the task because the task itself is a database bean and
 * therefore may not include functionality.
 */
public class TaskService extends BaseBeanService<Task, TaskDAO> {

    private static final Map<String, String> SORT_FIELD_MAPPING;

    static {
        SORT_FIELD_MAPPING = new HashMap<>();
        SORT_FIELD_MAPPING.put("title", "title");
        SORT_FIELD_MAPPING.put("title.keyword", "title");
        SORT_FIELD_MAPPING.put("processForTask.id", "process_id");
        SORT_FIELD_MAPPING.put("processForTask.title.keyword", "process.title");
        SORT_FIELD_MAPPING.put("processingStatus", "processingStatus");
        SORT_FIELD_MAPPING.put("processingUser.name.keyword", "task.processingUser.surname");
        SORT_FIELD_MAPPING.put("processingBegin", "processingBegin");
        SORT_FIELD_MAPPING.put("processingEnd", "processingEnd");
        SORT_FIELD_MAPPING.put("correctionCommentStatus", "correction");
        SORT_FIELD_MAPPING.put("projectForTask.title.keyword", "process.project.title");
        SORT_FIELD_MAPPING.put("processForTask.creationDate", "process.creationDate");
    }

    private static final Logger logger = LogManager.getLogger(TaskService.class);
    private static volatile TaskService instance = null;

    /**
     * Constructor with Searcher and Indexer assigning.
     */
    private TaskService() {
        super(new TaskDAO());
    }

    /**
     * Return singleton variable of type TaskService.
     *
     * @return unique instance of TaskService
     */
    public static TaskService getInstance() {
        TaskService localReference = instance;
        if (Objects.isNull(localReference)) {
            synchronized (TaskService.class) {
                localReference = instance;
                if (Objects.isNull(localReference)) {
                    localReference = new TaskService();
                    instance = localReference;
                }
            }
        }
        return localReference;
    }

    @Override
    public Long count() throws DAOException {
        return count("SELECT COUNT(*) FROM Task WHERE " + BaseDAO.getDateFilter("processingBegin"));
    }

    @Override
    public Long countResults(Map filters) throws DAOException {
        return countResults(new HashMap<String, String>(filters), false, false, false, null);
    }

    /**
     * Returns the number of objects of the implementing type that the filter
     * matches.
     * 
     * <p>
     * <b>API Note:</b><br>
     * This function counts the data records for the client, for which the
     * logged-in user is currently working.
     * 
     * <p>
     * <b>Implementation Requirements:</b><br>
     * This function requires that the thread is assigned to a logged-in user.
     *
     * <!-- Here, an additional function countResults() is specified with
     * additional parameters, and the generally specified function from
     * SearchDatabaseServiceInterface is not used. However, in
     * DatabaseTemplateServiceInterface, a value is set that affects the
     * generally specified functions countResults() and loadData() in
     * SearchDatabaseServiceInterface. This could be equalized at some point in
     * the future. -->
     * 
     * @param filters
     *            a map with exactly one entry, only the value is important, in
     *            which the content of the filter field is passed
     * @param onlyOwnTasks
     *            whether only tasks, to which the current user is already
     *            assigned as processor (usually not)
     * @param hideCorrectionTasks
     *            whether tasks in correction runs should be hidden (usually
     *            not)
     * @param showAutomaticTasks
     *            whether automatic tasks should be included (usually not)
     * @param taskStatus
     *            tasks in what status Tasks in what status. Must not be
     *            {@code} null. One of: [OPEN, INWORK], [OPEN], [INWORK] or [].
     * @return the number of matching objects
     * @throws DAOException
     *             database access error
     * @throws DAOException
     *             index access error
     */
    public Long countResults(Map<?, String> filters, boolean onlyOwnTasks, boolean hideCorrectionTasks,
            boolean showAutomaticTasks, List<TaskStatus> taskStatus) throws DAOException {

        BeanQuery query = formBeanQuery(filters, onlyOwnTasks, hideCorrectionTasks, showAutomaticTasks, taskStatus);
        query.performIndexSearches();
        return count(query.formCountQuery(), query.getQueryParameters());
    }

    @Override
    public List<Task> loadData(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters)
            throws DAOException {
        return loadData(first, pageSize, sortField, sortOrder, filters, false, false, false,
                Arrays.asList(TaskStatus.OPEN, TaskStatus.INWORK));
    }

    /**
     * Provides a window onto the task objects. This makes it possible to
     * navigate through the tasks page by page, without having to load all
     * objects into memory.
     * 
     * <p>
     * <b>API Note:</b><br>
     * This function filters the data according to the client, for which the
     * logged-in user is currently working.
     *
     * <!-- Here, an additional function loadData() is specified with additional
     * parameters, and the generally specified function from
     * SearchDatabaseServiceInterface is not used. However, in
     * DatabaseTemplateServiceInterface, a value is set that affects the
     * generally specified functions countResults() and loadData() in
     * SearchDatabaseServiceInterface. This could be equalized at some point in
     * the future. -->
     * 
     * <p>
     * <b>Implementation Requirements:</b><br>
     * This function requires that the thread is assigned to a logged-in user.
     * 
     * @param offset
     *            number of objects to be skipped at the list head
     * @param limit
     *            maximum number of objects to return
     * @param sortField
     *            by which column the data should be sorted. Must not be
     *            {@code null} or empty.<br>
     *            One of:<br>
     *            <ul>
     *            <li>"title.keyword": Title</li>
     *            <li>"processForTask.id": Process ID</li>
     *            <li>"processForTask.title.keyword": Process</li>
     *            <li>"processingStatus": Status</li>
     *            <li>"processingUser.name.keyword": Last editing user</li>
     *            <li>"processingBegin": Start of work</li>
     *            <li>"processingEnd": End of work</li>
     *            <li>"correctionCommentStatus": Comments</li>
     *            <li>"projectForTask.title.keyword": Project</li>
     *            <li>"processForTask.creationDate": Duration (Process)
     *            [sic!]</li>
     *            </ul>
     * @param sortOrder
     *            sort ascending or descending?
     * @param filters
     *            a map with exactly one entry, only the value is important, in
     *            which the content of the filter field is passed
     * @param onlyOwnTasks
     *            whether only tasks, to which the current user is already
     *            assigned as processor (usually not)
     * @param hideCorrectionTasks
     *            whether tasks in correction runs should be hidden (usually
     *            not)
     * @param showAutomaticTasks
     *            whether automatic tasks should be included (usually not)
     * @param taskStatus
     *            Tasks in what status. Must not be {@code} null. One of: [OPEN,
     *            INWORK], [OPEN], [INWORK] or [].
     * @return the data objects to be displayed
     * @throws DAOException
     *             if processes cannot be loaded from search index
     */
    public List<Task> loadData(int offset, int limit, String sortField, SortOrder sortOrder, Map<?, String> filters,
                                  boolean onlyOwnTasks, boolean hideCorrectionTasks, boolean showAutomaticTasks,
                                  List<TaskStatus> taskStatus)
            throws DAOException {

        BeanQuery query = formBeanQuery(filters, onlyOwnTasks, hideCorrectionTasks, showAutomaticTasks, taskStatus);
        query.defineSorting(SORT_FIELD_MAPPING.get(sortField), sortOrder);
        query.performIndexSearches();
        return getByQuery(query.formQueryForAll(), query.getQueryParameters(), offset, limit);
    }

    private BeanQuery formBeanQuery(Map<?, String> filters, boolean onlyOwnTasks, boolean hideCorrectionTasks,
            boolean showAutomaticTasks, List<TaskStatus> taskStatus) {
        BeanQuery query = new BeanQuery(Task.class);
        query.restrictToClient(ServiceManager.getUserService().getSessionClientId());
        Collection<Integer> projectIDs = ServiceManager.getUserService().getCurrentUser().getProjects()
                .stream().filter(Project::isActive).map(Project::getId).collect(Collectors.toList());
        query.restrictToProjects(projectIDs);
        List<Role> userRoles = ServiceManager.getUserService().getCurrentUser().getRoles();
        final Client currentClient = ServiceManager.getUserService().getSessionClientOfAuthenticatedUser();
        List<Role> userClientRoles = userRoles.stream().filter(role -> Objects.equals(role.getClient(), currentClient))
                .collect(Collectors.toList());
        query.restrictToRoles(userClientRoles);
        Iterator<? extends Entry<?, String>> filtersIterator = filters.entrySet().iterator();
        if (filtersIterator.hasNext()) {
            String filterString = filtersIterator.next().getValue();
            if (StringUtils.isNotBlank(filterString)) {
                query.restrictWithUserFilterString(filterString);
            }
        }
        if (onlyOwnTasks) {
            query.addIntegerRestriction("user.id", ServiceManager.getUserService().getCurrentUser().getId());
        }
        if (hideCorrectionTasks) {
            query.addIntegerRestriction("correction", 0);
        }
        if (!showAutomaticTasks) {
            query.addBooleanRestriction("typeAutomatic", Boolean.FALSE);
        }
        if (!taskStatus.isEmpty()) {
            query.addInCollectionRestriction("processingStatus", taskStatus);
        }
        return query;
    }

    /**
     * Replace processing user for given task. Handles add/remove from list of
     * processing tasks.
     *
     * @param task
     *            for which user will be assigned as processing user
     * @param user
     *            which will process given task
     */
    public void replaceProcessingUser(Task task, User user) {
        User currentProcessingUser = task.getProcessingUser();

        if (Objects.isNull(user) && Objects.isNull(currentProcessingUser)) {
            logger.info("do nothing - there is neither a new nor an old user");
        } else if (Objects.isNull(user)) {
            currentProcessingUser.getProcessingTasks().remove(task);
            task.setProcessingUser(null);
        } else if (Objects.isNull(currentProcessingUser)) {
            user.getProcessingTasks().add(task);
            task.setProcessingUser(user);
        } else if (Objects.equals(currentProcessingUser.getId(), user.getId())) {
            logger.info("do nothing - both are the same");
        } else {
            currentProcessingUser.getProcessingTasks().remove(task);
            user.getProcessingTasks().add(task);
            task.setProcessingUser(user);
        }
    }

    /**
     * Finds all task names that each differ from any other. That means, no
     * doubles.
     *
     * <p>
     * <b>API Note:</b><br>
     * This method actually returns all task names of all clients and is
     * therefore more suitable for operational purposes, rather not for display
     * purposes.
     *
     * @return all different task names
     */
    public List<String> findTaskTitlesDistinct() throws DAOException {
        BeanQuery beanQuery = new BeanQuery(Task.class);
        return super.dao.getStringsByQuery(beanQuery.formQueryForDistinct("title", true),
                beanQuery.getQueryParameters());
    }

    /**
     * Convert date of processing begin to formatted String.
     *
     * @param task
     *            object
     * @return formatted date string
     */
    public String getProcessingBeginAsFormattedString(Task task) {
        return Helper.getDateAsFormattedString(task.getProcessingBegin());
    }

    /**
     * Convert date of processing end to formatted String.
     *
     * @param task
     *            object
     * @return formatted date string
     */
    public String getProcessingEndAsFormattedString(Task task) {
        return Helper.getDateAsFormattedString(task.getProcessingEnd());
    }

    /**
     * Convert date of processing day to formatted String.
     *
     * @param task
     *            object
     * @return formatted date string
     */
    public String getProcessingTimeAsFormattedString(Task task) {
        return Helper.getDateAsFormattedString(task.getProcessingTime());
    }

    /**
     * Get localized (translated) title of task.
     *
     * @param title
     *            as String
     * @return localized title
     */
    public String getLocalizedTitle(String title) {
        return Helper.getTranslation(title);
    }

    /**
     * Get project(s). If the task belongs to a template, the projects are in
     * the template. If the task belongs to a process, the project is in the
     * process.
     *
     * @return value of project(s)
     */
    public static List<Project> getProjects(Task task) {
        Process process = task.getProcess();
        Template template = task.getTemplate();
        if (Objects.nonNull(process)) {
            return Collections.singletonList(process.getProject());
        } else if (Objects.nonNull(template)) {
            return template.getProjects();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Get roles list size.
     *
     * @param task
     *            object
     * @return size of roles assigned to task
     */
    public int getRolesSize(Task task) {
        return task.getRoles().size();
    }

    /**
     * Get title with user.
     *
     * @return des Schritttitels sowie (sofern vorhanden) den Benutzer mit
     *         vollständigem Namen
     */
    public String getTitleWithUserName(Task task) {
        String titleWithUserName = task.getTitle();
        User user = task.getProcessingUser();
        if (Objects.nonNull(user) && Objects.nonNull(user.getId())) {
            titleWithUserName += " (" + ServiceManager.getUserService().getFullName(user) + ")";
        }
        return titleWithUserName;
    }

    /**
     * Get script path.
     *
     * @param task
     *            object
     * @return script path as String
     */
    public String getScriptPath(Task task) {
        if (Objects.nonNull(task.getScriptPath()) && !task.getScriptPath().isEmpty()) {
            return task.getScriptPath();
        }
        return "";
    }

    /**
     * Runs a script for a task. The script can be a programmatic branch into
     * the application, a so-called Kitodo Script, or a command line call. If it
     * is an automatic task, depending on the result of the script, the task is
     * completed or set to open and the changed state is immediately saved to
     * the database.
     * 
     * <p>
     * <b>Implementation Note:</b><br>
     * In previous versions, up to five scripts could be available in a task,
     * and the operator would then start one or another of them manually.
     * Therefore, the script can still be passed here (because there were
     * several of them). Actually, this is no longer necessary, since there is
     * only one script per task remaining, and that can be cleaned up one day.
     *
     * @param task
     *            task in which the script is executed
     * @param script
     *            The command to be executed. If it starts with "action:", it is
     *            an internal function call, otherwise a command line that the
     *            JVM executes with the user it runs under.
     * @param automatic
     *            if so, the task is set to completed if the outcome is positive
     *            (without errors, for a command line call with the return code
     *            0), and if the outcome is negative it is set to open; and the
     *            object is then immediately updated in the database.
     * @return whether it had a successful outcome
     */
    public boolean executeScript(Task task, String script, boolean automatic) throws DAOException {
        if (Objects.isNull(script) || script.isEmpty()) {
            return false;
        }
        script = script.replace("{", "(").replace("}", ")");
        LegacyMetsModsDigitalDocumentHelper dd = null;
        Process po = task.getProcess();

        LegacyPrefsHelper prefs = ServiceManager.getRulesetService().getPreferences(po.getRuleset());

        try {
            dd = ServiceManager.getProcessService()
                    .readMetadataFile(ServiceManager.getFileService().getMetadataFilePath(po), prefs)
                    .getDigitalDocument();
        } catch (IOException e2) {
            logger.error(e2);
        }
        VariableReplacer replacer = new VariableReplacer(dd.getWorkpiece(), po, task);

        script = replacer.replace(script);
        boolean executedSuccessful = false;
        try {
            if (script.startsWith("action:")) {
                logger.info("Calling KitodoScript interpreter: {}", script);

                KitodoScriptService kitodoScriptService = ServiceManager.getKitodoScriptService();
                kitodoScriptService.execute(Arrays.asList(task.getProcess()), script);
                executedSuccessful = true;
            } else {
                logger.info("Calling the shell: {}", script);

                CommandService commandService = ServiceManager.getCommandService();
                CommandResult commandResult = commandService.runCommand(script);
                executedSuccessful = commandResult.isSuccessful();
            }
            finishOrReturnAutomaticTask(task, automatic, executedSuccessful);
        } catch (IOException | DAOException | InvalidImagesException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        } catch (MediaNotFoundException e) {
            Helper.setWarnMessage(e.getMessage());
        }
        return executedSuccessful;
    }

    /**
     * Runs a script for a task. The script can be a programmatic branch into
     * the application, a so-called Kitodo Script, or a command line call. If it
     * is an automatic task, depending on the result of the script, the task is
     * completed or set to open and the changed state is immediately saved to
     * the database.
     *
     * @param task
     *            task in which the script is executed
     * @param automatic
     *            if so, the task is set to completed if the outcome is positive
     *            (without errors, for a command line call with the return code
     *            0), and if the outcome is negative it is set to open; and the
     *            object is then immediately updated in the database.
     */
    public void executeScript(Task task, boolean automatic) throws DAOException {
        String script = task.getScriptPath();
        boolean scriptFinishedSuccessful = true;
        logger.debug("starting script {}", script);
        if (Objects.nonNull(script) && !script.trim().isEmpty()) {
            scriptFinishedSuccessful = executeScript(task, script, automatic);
        }
        if (!scriptFinishedSuccessful) {
            abortTask(task);
        }
    }

    /**
     * Make the necessary changes when performing an automatic task.
     *
     * @param task
     *            ongoing task
     * @param automatic
     *            if it is an automatic task
     * @param successful
     *            if the processing was successful
     * @throws DAOException
     *             if the task cannot be saved
     * @throws IOException
     *             if the task cannot be closed
     */
    private void finishOrReturnAutomaticTask(Task task, boolean automatic, boolean successful)
            throws DAOException, IOException {
        if (automatic) {
            task.setEditType(TaskEditType.AUTOMATIC);
            if (successful) {
                task.setProcessingStatus(TaskStatus.DONE);
                new WorkflowControllerService().close(task);
            } else {
                task.setProcessingStatus(TaskStatus.OPEN);
                save(task);
            }
        }
    }

    private void abortTask(Task task) throws DAOException {
        task.setProcessingStatus(TaskStatus.OPEN);
        task.setEditType(TaskEditType.AUTOMATIC);
        save(task);
    }

    /**
     * Generates browser-friendly images. (thumbnails, or even full images in
     * any format used by browsers)
     *
     * @param executingThread
     *            Background thread that controls image generation. In this
     *            thread, display fields are updated so that the user can see
     *            the progress.
     * @param task
     *            task whose status should be set upon completion
     * @param automatic
     *            if so, the task is set to completed if the processing was
     *            completed without errors, and else it is set to open; and the
     *            object is then immediately updated in the database.
     * @throws DAOException
     *             if the task cannot be saved
     */
    public void generateImages(EmptyTask executingThread, Task task, boolean automatic) throws DAOException {
        try {
            Process process = task.getProcess();
            Subfolder sourceFolder = new Subfolder(process, process.getProject().getGeneratorSource());
            List<Subfolder> foldersToGenerate = SubfolderFactoryService.createAll(process, task.getContentFolders());
            ImageGenerator generator = new ImageGenerator(sourceFolder, GenerationMode.ALL, foldersToGenerate);
            generator.setSupervisor(executingThread);
            generator.run();
            finishOrReturnAutomaticTask(task, automatic, Objects.isNull(executingThread.getException()));
        } catch (IOException | DAOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    /**
     * Execute DMS export.
     *
     * @param task
     *            as Task object
     */
    public void executeDmsExport(Task task) throws DAOException, IOException {
        new ExportDms(task).startExport(task);
    }

    /**
     * Returns all tasks in the batch with the specified name.
     *
     * @param title
     *            task title
     * @param batchId
     *            batch number
     * @return all identified tasks
     */
    public List<Task> getCurrentTasksOfBatch(String title, Integer batchId) {
        return dao.getCurrentTasksOfBatch(title, batchId);
    }

    /**
     * Returns all tasks that lie between the two specified tasks in the
     * processing sequence.
     *
     * @param previousOrdering
     *            processing sequence number of the previous task, that is the
     *            task in which the error can be corrected
     * @param laterOrdering
     *            processing sequence number of the later task, that is the task
     *            in which the error was discovered
     * @param processId
     *            record number of the process whose tasks are being searched
     * @return the tasks in between
     */
    public List<Task> getAllTasksInBetween(Integer previousOrdering, Integer laterOrdering, Integer processId) {
        return dao.getAllTasksInBetween(previousOrdering, laterOrdering, processId);
    }

    /**
     * Get next tasks for problem solution for given process id.
     *
     * @param ordering
     *            of Task for which it searches next ones as Integer
     * @param processId
     *            id of process for which tasks are searched as Integer
     * @return list of Task objects
     */
    public List<Task> getNextTasksForProblemSolution(Integer ordering, Integer processId) {
        return dao.getNextTasksForProblemSolution(ordering, processId);
    }

    /**
     * Finds all tasks that preceded the current one. This is used when a user
     * wants to send an error message to a previous task, so the user can choose
     * which task they want to send the message to.
     *
     * @param ordering
     *            processing sequence number of the task in which the error was
     *            discovered
     * @param processId
     *            record number of the process whose tasks are being searched
     * @return list of Task objects
     */
    public List<Task> getPreviousTasksForProblemReporting(Integer ordering, Integer processId) {
        return dao.getPreviousTasksForProblemReporting(ordering, processId);
    }

    /**
     * Find tasks by id of process.
     *
     * @param id
     *            of process
     * @return list of JSON objects with tasks for specific process id
     */
    List<Map<String, Object>> findByProcessId(Integer id) throws DataException {
        // TODO delete method stub
        throw new UnsupportedOperationException("no longer used function");
    }

    /**
     * Determines all processes with a specific production template.
     *
     * @param templateId
     *            record number of the production template
     * @return list that is not empty if something was found, otherwise empty
     *         list
     * @throws DAOException
     *             if an error occurred during the search
     */
    /*
     * Used in TemplateForm to find out whether a production template is used in
     * a process. (Then it may not be deleted.) Is only checked for isEmpty().
     */
    public List<Map<String, Object>> findByTemplateId(Integer id) throws DAOException {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /**
     * The function determines, from projects, the folders whose contents can be
     * generated automatically.
     *
     * <p>
     * This feature is needed once by the task in the template to determine
     * which folders show buttons in the interface to turn content creation on
     * or off. In addition, the function of the task in the process is required
     * to determine if there is at least one folder to be created in the task,
     * because then action links for generating are displayed, and not
     * otherwise.
     *
     * <p>
     * To create content automatically, a folder must be defined as the template
     * folder in the project. The templates serve to create the contents in the
     * other folders to be created. Under no circumstances should the contents
     * of the template folder be automatically generated, even if, for example,
     * after a reconfiguration, this is still set as otherwise they would
     * overwrite themselves. Also, contents cannot be created in folders where
     * nothing is configured. The folders that are left over can be created.
     *
     * @param projects
     *            an object stream of projects that may have folders defined
     *            whose contents can be auto-generated
     * @return an object stream of generable folders
     */
    public static Stream<Folder> generatableFoldersFromProjects(Stream<Project> projects) {
        Stream<Project> projectsWithSourceFolder = skipProjectsWithoutSourceFolder(projects);
        Stream<Folder> allowedFolders = dropOwnSourceFolders(projectsWithSourceFolder);
        return removeFoldersThatCannotBeGenerated(allowedFolders);
    }

    /**
     * Only lets projects pass where a source folder is selected.
     *
     * @param projects
     *            the unpurified stream of projects
     * @return a stream only of projects that define a source to generate images
     */
    private static Stream<Project> skipProjectsWithoutSourceFolder(Stream<Project> projects) {
        return projects.filter(project -> Objects.nonNull(project.getGeneratorSource()));
    }

    /**
     * Drops all folders to generate if they are their own source folder.
     *
     * @param projects
     *            projects whose folders allowed to be generated are to be
     *            determined
     * @return a stream of folders that are allowed to be generated
     */
    private static Stream<Folder> dropOwnSourceFolders(Stream<Project> projects) {
        Stream<Pair<Folder, Folder>> withSources = projects.flatMap(
            project -> project.getFolders().stream().map(folder -> Pair.of(folder, project.getGeneratorSource())));
        Stream<Pair<Folder, Folder>> filteredWithSources = withSources.filter(
            destinationAndSource -> !destinationAndSource.getLeft().equals(destinationAndSource.getRight()));
        return filteredWithSources.map(Pair::getLeft);
    }

    /**
     * Removes all folders to generate which do not have anything to generate
     * configured.
     *
     * @param folders
     *            a stream of folders
     * @return a stream only of those folders where an image generation module
     *         has been selected
     */
    private static Stream<Folder> removeFoldersThatCannotBeGenerated(Stream<Folder> folders) {
        return folders.filter(folder -> folder.getDerivative().isPresent() || folder.getDpi().isPresent()
                || folder.getImageSize().isPresent());
    }

    /**
     * Get the duration of a task in days.
     * @param task the task to get the duration for
     * @return the duration in days
     */
    public long getDurationInDays(Task task) {

        Date end = task.getProcessingEnd();
        if (Objects.isNull(end)) {
            end = new Date();
        }
        Date begin = task.getProcessingBegin();
        if (Objects.isNull(begin)) {
            begin = task.getProcessingTime();
            if (Objects.isNull(begin)) {
                begin = new Date();
            }
        }
        long differenceTime = end.getTime() - begin.getTime();
        return differenceTime / (1000 * 60 * 60 * 24);
    }

    /**
     * Compute and return list of tasks that are open or in work in the given Process 'process' and are concurrent but
     * not equal not the to given Task 'task', if 'task' is not null.
     * @param process Process whose tasks are returned
     * @param task task to filter from returned list, if not null
     * @return List of concurrent open or inwork tasks
     */
    public static List<Task> getConcurrentTasksOpenOrInWork(Process process, Task task) {
        List<Task> tasks = process.getTasks().stream()
                .filter(t -> TaskStatus.INWORK.equals(t.getProcessingStatus())
                        || TaskStatus.OPEN.equals(t.getProcessingStatus()))
                .collect(Collectors.toList());
        if (Objects.nonNull(task) && task.isConcurrent()) {
            return tasks.stream()
                    .filter(t -> !t.getId().equals(task.getId()))
                    .collect(Collectors.toList());
        } else {
            return tasks;
        }
    }

    /**
     * Filter and return current list of tasks for those in work by other users.
     * @param tasks list of tasks
     * @return list of tasks in work by other users
     */
    public static List<Task> getTasksInWorkByOtherUsers(List<Task> tasks) {
        int authenticatedUserId = ServiceManager.getUserService().getAuthenticatedUser().getId();
        return tasks.stream()
                .filter(t -> Objects.nonNull(t.getProcessingUser())
                        && authenticatedUserId != t.getProcessingUser().getId())
                .collect(Collectors.toList());
    }

    /**
     * Compute and return list of tasks that are eligible as 'currentTask' for a new correction comment.
     *
     * @return list of current task options for new correction comment
     */
    public static List<Task> getCurrentTaskOptions(Process process) {
        int authenticatedUserId = ServiceManager.getUserService().getAuthenticatedUser().getId();
        // NOTE: checking for 'INWORK' tasks that do not have a 'processingUser' shouldn't be necessary, but the current
        // version of Kitodo.Production allows setting tasks to 'INWORK' without explicitly assigning a user to it via a
        // process' task list (e.g. administrative action)
        return process.getTasks().stream()
                .filter(t -> (TaskStatus.INWORK.equals(t.getProcessingStatus())
                        && ((Objects.nonNull(t.getProcessingUser()) && authenticatedUserId == t.getProcessingUser().getId())
                        || Objects.isNull(t.getProcessingUser())))
                        || TaskStatus.OPEN.equals(t.getProcessingStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Create and return a tooltip for the correction message switch using the give Process and Task.
     * @param process Process for which tooltip is created
     * @param task Task for which tooltip is created
     * @return tooltip for correction message switch
     */
    public static String getCorrectionMessageSwitchTooltip(Process process, Task task) {
        if (isCorrectionWorkflow(process)) {
            return Helper.getTranslation("dataEditor.comment.correctionWorkflowAlreadyActive");
        } else if (Objects.nonNull(task) && task.getOrdering() == 1) {
            return Helper.getTranslation("dataEditor.comment.firstTaskInWorkflow");
        } else {
            List<Task> concurrentTasks = TaskService.getConcurrentTasksOpenOrInWork(process, task);
            if (concurrentTasks.isEmpty()) {
                Helper.setErrorMessage("Invalid process state: no 'inwork' or 'open' task found!");
                return "";
            } else if (concurrentTasks.get(0).getOrdering() == 1) {
                return Helper.getTranslation("dataEditor.comment.firstTaskInWorkflow");
            } else {
                List<Task> tasksInWorkByOtherUsers = TaskService.getTasksInWorkByOtherUsers(concurrentTasks);
                if (tasksInWorkByOtherUsers.isEmpty()) {
                    return "";
                } else {
                    return MessageFormat.format(Helper.getTranslation("dataEditor.comment.parallelTaskInWorkText"),
                            tasksInWorkByOtherUsers.get(0).getTitle(),
                            tasksInWorkByOtherUsers.get(0).getProcessingUser().getFullName());
                }
            }
        }
    }

    /**
     * Check and return whether 'correction' flag is set to true for any task of the current process,
     * e.g. if process is currently in a correction workflow.
     *
     * @return whether process is in correction workflow or not
     */
    public static boolean isCorrectionWorkflow(Process process) {
        return process.getTasks().stream().anyMatch(Task::isCorrection);
    }

    /**
     * Get the id of the template task corresponding to the given task.
     * The corresponding template task was the blueprint when creating the given task.
     * @param task task to find the corresponding template task for
     * @return id of the template task or -1 if no matching task could be found
     */
    public static int getCorrespondingTemplateTaskId(Task task) {
        List<Task> templateTasks = task.getProcess().getTemplate().getTasks().stream()
                .filter(t -> t.getOrdering().equals(task.getOrdering()))
                .filter(t -> t.getTitle().equals(task.getTitle()))
                .collect(Collectors.toList());
        if (templateTasks.size() == 1) {
            return templateTasks.get(0).getId();
        }
        return -1;
    }

    // === alternative functions that are no longer required ===

    /**
     * Find object in ES and convert it to Interface.
     *
     * @param id
     *            object id
     * @return Interface object
     * @deprecated Use {@link #getById(Integer)}.
     */
    @Deprecated
    public Task findById(Integer id) throws DAOException {
        return getById(id);
    }
}
