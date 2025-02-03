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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.enums.IndexAction;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.TemplateDAO;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.elasticsearch.index.Indexer;
import org.kitodo.data.elasticsearch.index.type.TemplateType;
import org.kitodo.data.elasticsearch.index.type.enums.TemplateTypeField;
import org.kitodo.data.elasticsearch.search.Searcher;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.production.dto.ProjectDTO;
import org.kitodo.production.dto.TaskDTO;
import org.kitodo.production.dto.TemplateDTO;
import org.kitodo.production.dto.WorkflowDTO;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.base.ClientSearchService;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.primefaces.model.SortOrder;

public class TemplateService extends ClientSearchService<Template, TemplateDTO, TemplateDAO> {

    private static final Logger logger = LogManager.getLogger(TemplateService.class);
    private static volatile TemplateService instance = null;
    private boolean showInactiveTemplates = false;

    /**
     * Constructor with Searcher and Indexer assigning.
     */
    private TemplateService() {
        super(new TemplateDAO(), new TemplateType(), new Indexer<>(Template.class), new Searcher(Template.class),
                TemplateTypeField.CLIENT_ID.getKey());
    }

    /**
     * Return singleton variable of type TemplateService.
     *
     * @return unique instance of TemplateService
     */
    public static TemplateService getInstance() {
        TemplateService localReference = instance;
        if (Objects.isNull(localReference)) {
            synchronized (TemplateService.class) {
                localReference = instance;
                if (Objects.isNull(localReference)) {
                    localReference = new TemplateService();
                    instance = localReference;
                }
            }
        }
        return localReference;
    }

    /**
     * Retrieves a map indicating the usage status of templates.
     * The method executes an HQL query to determine whether each template is used
     * (i.e., has associated processes).
     *
     * @return a map where the key is the template ID and the value is a boolean
     *         indicating whether the template is used
     */
    public Map<Integer, Boolean> getTemplateUsageMap() {
        String hql = "SELECT t.id AS templateId, "
                + " CASE WHEN EXISTS (SELECT 1 FROM Process p WHERE p.template.id = t.id) "
                + " THEN true ELSE false END AS isUsed "
                + " FROM Template t";
        List<Object[]> results = getProjectionByQuery(hql);
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0], // templateId
                        row -> (Boolean) row[1]  // isUsed
                ));
    }

    @Override
    public Long countDatabaseRows() throws DAOException {
        return countDatabaseRows("SELECT COUNT(*) FROM Template");
    }

    @Override
    public Long countNotIndexedDatabaseRows() throws DAOException {
        return countDatabaseRows("SELECT COUNT(*) FROM Template WHERE indexAction = 'INDEX' OR indexAction IS NULL");
    }

    @Override
    public Long countResults(Map filters) throws DataException {
        return countDocuments(createUserTemplatesQuery(filters));
    }

    @Override
    public List<Template> getAllNotIndexed() {
        return getByQuery("FROM Template WHERE indexAction = 'INDEX' OR indexAction IS NULL");
    }

    @Override
    public List<Template> getAllForSelectedClient() {
        return dao.getActiveTemplates(ServiceManager.getUserService().getSessionClientId());
    }

    @Override
    public List<TemplateDTO> loadData(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters)
            throws DataException {
        return findByQuery(createUserTemplatesQuery(filters), getSortBuilder(sortField, sortOrder), first, pageSize,
            false);

    }

    /**
     * Method saves or removes tasks and project related to modified template.
     *
     * @param template
     *            object
     */
    @Override
    protected void manageDependenciesForIndex(Template template)
            throws CustomResponseException, DAOException, DataException, IOException {
        manageProjectDependenciesForIndex(template);
        manageTaskDependenciesForIndex(template);
    }

    /**
     * Find all templates available to assign to the edited project. It will be
     * displayed in the templateAddPopup.
     *
     * @param projectId
     *            id of project which is going to be edited
     * @return list of all matching templates
     */
    public List<TemplateDTO> findAllAvailableForAssignToProject(Integer projectId) throws DataException {
        return findAvailableForAssignToUser(projectId);
    }

    private List<TemplateDTO> findAvailableForAssignToUser(Integer projectId) throws DataException {
        BoolQueryBuilder query = new BoolQueryBuilder();
        if (Objects.nonNull(projectId)) {
            query.must(createSimpleQuery(TemplateTypeField.PROJECTS + ".id", projectId, false));
        }
        query.must(getQueryForSelectedClient());
        query.must(getQueryForActive(true));
        return findByQuery(query, true);
    }

    /**
     * Duplicate the template with the given ID 'itemId'.
     *
     * @return the duplicated Template
     */
    public Template duplicateTemplate(Template baseTemplate) {
        Template duplicatedTemplate = new Template();

        duplicatedTemplate.setTitle(baseTemplate.getTitle() + "_" + Helper.generateRandomString(3));
        duplicatedTemplate.setCreationDate(new Date());
        duplicatedTemplate.setClient(baseTemplate.getClient());
        duplicatedTemplate.setDocket(baseTemplate.getDocket());
        duplicatedTemplate.setRuleset(baseTemplate.getRuleset());
        // tasks don't need to be duplicated - will be created out of copied workflow
        duplicatedTemplate.setWorkflow(baseTemplate.getWorkflow());

        // TODO: make sure if copy should be assigned automatically to all projects
        for (Project project : baseTemplate.getProjects()) {
            duplicatedTemplate.getProjects().add(project);
            project.getTemplates().add(duplicatedTemplate);
        }

        return duplicatedTemplate;
    }

    @Override
    public TemplateDTO convertJSONObjectToDTO(Map<String, Object> jsonObject, boolean related) throws DataException {
        TemplateDTO templateDTO = new TemplateDTO();
        templateDTO.setId(getIdFromJSONObject(jsonObject));
        templateDTO.setTitle(TemplateTypeField.TITLE.getStringValue(jsonObject));
        templateDTO.setActive(TemplateTypeField.ACTIVE.getBooleanValue(jsonObject));
        templateDTO.setCreationDate(TemplateTypeField.CREATION_DATE.getStringValue(jsonObject));
        templateDTO.setDocket(
            ServiceManager.getDocketService().findById(TemplateTypeField.DOCKET.getIntValue(jsonObject)));
        templateDTO.setRuleset(
            ServiceManager.getRulesetService().findById(TemplateTypeField.RULESET_ID.getIntValue(jsonObject)));
        WorkflowDTO workflowDTO = new WorkflowDTO();
        workflowDTO.setTitle(TemplateTypeField.WORKFLOW_TITLE.getStringValue(jsonObject));
        templateDTO.setWorkflow(workflowDTO);
        templateDTO.setTasks(convertRelatedJSONObjectToDTO(jsonObject, TemplateTypeField.TASKS.getKey(),
            ServiceManager.getTaskService()));
        templateDTO.setCanBeUsedForProcess(hasCompleteTasks(templateDTO.getTasks()));

        if (!related) {
            convertRelatedJSONObjects(jsonObject, templateDTO);
        }

        return templateDTO;
    }

    private void convertRelatedJSONObjects(Map<String, Object> jsonObject, TemplateDTO templateDTO)
            throws DataException {
        templateDTO.setProjects(convertRelatedJSONObjectToDTO(jsonObject, TemplateTypeField.PROJECTS.getKey(),
            ServiceManager.getProjectService()).stream().sorted(Comparator.comparing(ProjectDTO::getTitle))
                .collect(Collectors.toList()));
    }

    /**
     * Find templates by docket id.
     *
     * @param docketId
     *            id of docket for search
     * @return list of JSON objects with templates for specific docket id
     */
    public List<Map<String, Object>> findByDocket(int docketId) throws DataException {
        QueryBuilder query = createSimpleQuery(TemplateTypeField.DOCKET.getKey(), docketId, true);
        return findDocuments(query);
    }

    /**
     * Find templates by ruleset id.
     *
     * @param rulesetId
     *            id of ruleset for search
     * @return list of JSON objects with templates for specific ruleset id
     */
    public List<Map<String, Object>> findByRuleset(int rulesetId) throws DataException {
        QueryBuilder query = createSimpleQuery(TemplateTypeField.RULESET_ID.getKey(), rulesetId, true);
        return findDocuments(query);
    }

    /**
     * Get diagram image for current template.
     *
     * @return diagram image file
     */
    public InputStream getTasksDiagram(String fileName) {
        if (Objects.nonNull(fileName) && !fileName.isEmpty()) {
            File tasksDiagram = new File(ConfigCore.getKitodoDiagramDirectory(), fileName + ".svg");
            try {
                return new FileInputStream(tasksDiagram);
            } catch (FileNotFoundException e) {
                logger.error(e.getMessage(), e);
                return getEmptyInputStream();
            }
        }
        return getEmptyInputStream();
    }

    private InputStream getEmptyInputStream() {
        return new InputStream() {
            @Override
            public int read() {
                return -1;
            }
        };
    }

    /**
     * Check for unreachable tasks. Unreachable task is this one which has no roles
     * assigned to itself. Other possibility is that given list is empty. It means
     * that whole workflow is unreachable.
     *
     * @param tasks
     *            list of tasks for check
     */
    public void checkForUnreachableTasks(List<Task> tasks) throws ProcessGenerationException {
        if (tasks.isEmpty()) {
            throw new ProcessGenerationException(Helper.getTranslation("noStepsInWorkflow"));
        }
        for (Task task : tasks) {
            if (ServiceManager.getTaskService().getRolesSize(task) == 0) {
                throw new ProcessGenerationException(
                        Helper.getTranslation("noUserInStep", task.getTitle()));
            }
        }
    }

    /**
     * Check whether the tasks assigned to template are complete. If it contains
     * tasks that are not assigned to a user or user group - tasks are not complete.
     *
     * @param tasks
     *            list of tasks for testing
     * @return true or false
     */
    boolean hasCompleteTasks(List<TaskDTO> tasks) {
        if (tasks.isEmpty()) {
            return false;
        }
        for (TaskDTO task : tasks) {
            if (task.getRolesSize() == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Set show inactive templates.
     *
     * @param showInactiveTemplates
     *            as boolean
     */
    public void setShowInactiveTemplates(boolean showInactiveTemplates) {
        this.showInactiveTemplates = showInactiveTemplates;
    }

    /**
     * Add process to project, if project is assigned to process.
     *
     * @param template
     *            object
     */
    private void manageProjectDependenciesForIndex(Template template)
            throws CustomResponseException, DataException, IOException {
        for (Project project : template.getProjects()) {
            if (template.getIndexAction().equals(IndexAction.DELETE)) {
                project.getTemplates().remove(template);
                ServiceManager.getProjectService().saveToIndex(project, false);
            } else {
                ServiceManager.getProjectService().saveToIndex(project, false);
            }
        }
    }

    /**
     * Check IndexAction flag in for process object. If DELETE remove all tasks from
     * index, if other call saveOrRemoveTaskInIndex() method.
     *
     * @param template
     *            object
     */
    private void manageTaskDependenciesForIndex(Template template)
            throws CustomResponseException, DAOException, IOException, DataException {
        if (template.getIndexAction().equals(IndexAction.DELETE)) {
            for (Task task : template.getTasks()) {
                ServiceManager.getTaskService().removeFromIndex(task, false);
            }
        } else {
            saveOrRemoveTasksInIndex(template);
        }
    }

    /**
     * Compare index and database, according to comparisons results save or remove
     * tasks.
     *
     * @param template
     *            object
     */
    private void saveOrRemoveTasksInIndex(Template template)
            throws CustomResponseException, DAOException, IOException, DataException {
        List<Integer> database = new ArrayList<>();
        List<Integer> index = new ArrayList<>();

        for (Task task : template.getTasks()) {
            database.add(task.getId());
            ServiceManager.getTaskService().saveToIndex(task, false);
        }

        List<Map<String, Object>> searchResults = ServiceManager.getTaskService().findByTemplateId(template.getId());
        for (Map<String, Object> object : searchResults) {
            index.add(getIdFromJSONObject(object));
        }

        List<Integer> missingInIndex = findMissingValues(database, index);
        List<Integer> notNeededInIndex = findMissingValues(index, database);
        for (Integer missing : missingInIndex) {
            ServiceManager.getTaskService().saveToIndex(ServiceManager.getTaskService().getById(missing), false);
        }

        for (Integer notNeeded : notNeededInIndex) {
            ServiceManager.getTaskService().removeFromIndex(notNeeded, false);
        }
    }

    /**
     * Compare two list and return difference between them.
     *
     * @param firstList
     *            list from which records can be remove
     * @param secondList
     *            records stored here will be removed from firstList
     * @return difference between two lists
     */
    private List<Integer> findMissingValues(List<Integer> firstList, List<Integer> secondList) {
        List<Integer> newList = new ArrayList<>(firstList);
        newList.removeAll(secondList);
        return newList;
    }

    private BoolQueryBuilder readFilters(Map<String, String> filterMap) throws DataException {
        BoolQueryBuilder query = new BoolQueryBuilder();

        for (Map.Entry<String, String> entry : filterMap.entrySet()) {
            query.must(
                ServiceManager.getFilterService().queryBuilder(entry.getValue(), ObjectType.TEMPLATE, false, false));
        }
        return query;
    }

    /**
     * Creates and returns a query to retrieve templates for which the currently
     * logged in user is eligible.
     *
     * @param filters
     *            map of applicable filters
     * @return query to retrieve templates for which the user eligible
     */
    @SuppressWarnings("unchecked")
    private BoolQueryBuilder createUserTemplatesQuery(Map filters) throws DataException {
        BoolQueryBuilder query = new BoolQueryBuilder();

        if (Objects.nonNull(filters) && !filters.isEmpty()) {
            Map<String, String> filterMap = filters;
            query.must(readFilters(filterMap));
        }
        query.must(getQueryForSelectedClient());

        if (!showInactiveTemplates) {
            query.must(getQueryForActive(true));
        }

        return query;
    }

    /**
     * Get query for projects assigned to selected client.
     *
     * @return query as QueryBuilder
     */
    private QueryBuilder getQueryForActive(boolean active) {
        return createSimpleQuery(TemplateTypeField.ACTIVE.getKey(), active, true);
    }

    /**
     * Get query for projects assigned to selected client.
     *
     * @return query as QueryBuilder
     */
    private QueryBuilder getQueryForSelectedClient() {
        return createSimpleQuery(TemplateTypeField.CLIENT_ID.getKey(),
            ServiceManager.getUserService().getSessionClientId(), true);
    }

    /**
     * Get all process templates for given title.
     *
     * @param title
     *            of Template
     * @return list of all process templates as Template objects
     */
    public List<Template> getProcessTemplatesWithTitle(String title) {
        return dao.getTemplatesWithTitle(title);
    }

    /**
     * Get all process templates for given title and client id.
     *
     * @param title
     *            of Template
     * @param clientId
     *            id of client
     * @return list of all process templates as Template objects
     */
    public List<Template> getTemplatesWithTitleAndClient(String title, Integer clientId) {
        String query = "SELECT t FROM Template AS t INNER JOIN t.client AS c WITH c.id = :clientId WHERE t.title = :title";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", clientId);
        parameters.put("title", title);
        return getByQuery(query, parameters);
    }
}
