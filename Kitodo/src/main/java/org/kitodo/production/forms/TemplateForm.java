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

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.view.ViewScoped;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Docket;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.Workflow;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.WorkflowException;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.model.LazyDTOModel;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.TemplateService;
import org.kitodo.production.services.workflow.WorkflowControllerService;
import org.kitodo.production.workflow.model.Converter;

@Named("TemplateForm")
@ViewScoped
public class TemplateForm extends TemplateBaseForm {

    private static final Logger logger = LogManager.getLogger(TemplateForm.class);
    private Template template;
    private List<Project> assignedProjects = new ArrayList<>();
    private Task task;
    private boolean showInactiveTemplates = false;
    private static final String TITLE_USED = "templateTitleAlreadyInUse";
    private final String templateListPath = MessageFormat.format(REDIRECT_PATH, "projects");
    private final String templateEditPath = MessageFormat.format(REDIRECT_PATH, "templateEdit");
    private List<String> templateFilters;
    private List<String> selectedTemplateFilters;
    private static final String DEACTIVATED_TEMPLATES_FILTER = "deactivatedTemplates";
    private Map<Integer,Boolean> templateUsageMap;

    /**
     * Constructor.
     */
    public TemplateForm() {
        super.setLazyDTOModel(new LazyDTOModel(ServiceManager.getTemplateService()));
    }

    /**
     * Initialize list of template filters (currently only 'deactivated templates').
     */
    @PostConstruct
    public void init() {
        templateFilters = new LinkedList<>();
        templateFilters.add(DEACTIVATED_TEMPLATES_FILTER);
        selectedTemplateFilters = new LinkedList<>();
    }

    /**
     * Check if inactive templates should be shown.
     *
     * @return true or false
     */
    public boolean isShowInactiveTemplates() {
        return this.showInactiveTemplates;
    }

    /**
     * Set if inactive templates should be shown.
     *
     * @param showInactiveTemplates
     *            true or false
     */
    public void setShowInactiveTemplates(boolean showInactiveTemplates) {
        this.showInactiveTemplates = showInactiveTemplates;
        ServiceManager.getTemplateService().setShowInactiveTemplates(showInactiveTemplates);
    }

    /**
     * Create new template.
     *
     * @return page
     */
    public String newTemplate() {
        this.template = new Template();
        this.template.setTitle("");
        this.template.setClient(ServiceManager.getUserService().getSessionClientOfAuthenticatedUser());
        this.assignedProjects.clear();
        return templateEditPath + "&id=" + (Objects.isNull(this.template.getId()) ? 0 : this.template.getId());
    }

    /**
     * Duplicate the selected template.
     *
     * @param itemId
     *            ID of the template to duplicate
     * @return page address; either redirect to the edit template page or return
     *         'null' if the template could not be retrieved, which will prompt
     *         JSF to remain on the same page and reuse the bean.
     */
    public String duplicate(Integer itemId) {
        try {
            Template baseTemplate = ServiceManager.getTemplateService().getById(itemId);
            this.template = ServiceManager.getTemplateService().duplicateTemplate(baseTemplate);
            this.assignedProjects.clear();
            this.assignedProjects.addAll(template.getProjects());
            this.setSaveDisabled(false);
            return templateEditPath;
        } catch (DAOException e) {
            Helper.setErrorMessage(ERROR_DUPLICATE, new Object[] {ObjectType.TEMPLATE.getTranslationSingular() },
                logger, e);
            return this.stayOnCurrentPage;
        }
    }

    /**
     * Save template and redirect to list view.
     *
     * @return url to list view
     */
    public String save() {
        if (isTitleValid()) {
            try {
                prepareTasks();
            } catch (DAOException | DataException | IOException e) {
                Helper.setErrorMessage("errorDiagram", new Object[] {this.template.getWorkflow().getTitle() },
                    logger, e);
                return this.stayOnCurrentPage;
            } catch (WorkflowException e) {
                Helper.setErrorMessage("errorDiagram",
                    new Object[] {this.template.getWorkflow().getTitle(), e.getMessage() }, logger, e);
                return this.stayOnCurrentPage;
            }

            this.template.getProjects().clear();
            this.template.getProjects().addAll(assignedProjects);

            try {
                ServiceManager.getTemplateService().save(this.template, true);
                template = ServiceManager.getTemplateService().getById(this.template.getId());
                new WorkflowControllerService().activateNextTasks(template.getTasks());
            } catch (DataException | IOException | DAOException e) {
                Helper.setErrorMessage(ERROR_SAVING, new Object[] {ObjectType.TEMPLATE.getTranslationSingular() },
                    logger, e);
                return this.stayOnCurrentPage;
            }
        } else {
            return this.stayOnCurrentPage;
        }
        return templateListPath;
    }

    private boolean isTitleValid() {
        String templateTitle = this.template.getTitle();
        if (StringUtils.isNotBlank(templateTitle)) {
            List<Template> templates = ServiceManager.getTemplateService().getTemplatesWithTitleAndClient(templateTitle,
                    this.template.getClient().getId());
            int count = templates.size();
            if (count > 1) {
                Helper.setErrorMessage(ERROR_OCCURRED, TITLE_USED);
                return false;
            } else if (count == 1) {
                Integer templateId = this.template.getId();
                if (Objects.nonNull(templateId) && templates.get(0).getId().equals(templateId)) {
                    return true;
                }
                Helper.setErrorMessage(ERROR_OCCURRED, TITLE_USED);
                return false;
            }
            return true;
        }
        Helper.setErrorMessage(ERROR_INCOMPLETE_DATA, "templateTitleEmpty");
        return false;
    }

    /**
     * Remove template if there is no assigned processes.
     */
    public void delete() {
        if (!this.template.getProcesses().isEmpty()) {
            Helper.setErrorMessage("processAssignedError");
        } else {
            try {
                if (Objects.nonNull(this.template.getWorkflow())) {
                    this.template.getWorkflow().getTemplates().remove(this.template);
                    this.template.setWorkflow(null);
                }

                ServiceManager.getTemplateService().remove(this.template);
            } catch (Exception e) {
                Helper.setErrorMessage(ERROR_DELETING, new Object[] {ObjectType.TEMPLATE.getTranslationSingular() },
                    logger, e);
            }
        }
    }

    /**
     * Save task and redirect to processEdit view.
     *
     * @return url to templateEdit view
     */
    public String saveTaskAndRedirect() {
        saveTask(this.task);
        return templateEditPath + "&id=" + (Objects.isNull(this.template.getId()) ? 0 : this.template.getId());
    }

    /**
     * Get diagram image for current template.
     *
     * @return diagram image file
     */
    public InputStream getTasksDiagram() {
        if (Objects.nonNull(this.template.getWorkflow())) {
            return ServiceManager.getTemplateService().getTasksDiagram(this.template.getWorkflow().getTitle());
        }
        return ServiceManager.getTemplateService().getTasksDiagram("");
    }

    /**
     * Get list of dockets for select list.
     *
     * @return list of dockets
     */
    public List<Docket> getDockets() {
        return ServiceManager.getDocketService().getAllForSelectedClient();
    }

    /**
     * Get list of projects for select list.
     *
     * @return list of SelectItem objects
     */
    public List<Project> getProjects() {
        return ServiceManager.getProjectService().getAllForSelectedClient();
    }

    /**
     * Get list of rulesets for select list.
     *
     * @return list of ruleset
     */
    public List<Ruleset> getRulesets() {
        return ServiceManager.getRulesetService().getAllForSelectedClient();
    }

    /**
     * Get list of workflows for select list.
     *
     * @return list of workflows
     */
    public List<Workflow> getWorkflows() {
        return ServiceManager.getWorkflowService().getAvailableWorkflows();
    }

    /**
     * Get list of OCR-D workflows for select list.
     *
     * @return list of OCR-D workflows
     */
    public List<Pair<?, ?>> getOcrdWorkflows() {
        return ServiceManager.getOcrdWorkflowService().getOcrdWorkflows();
    }

    /**
     * Get the OCR-D workflow.
     *
     * @return Immutable key value pair
     */
    public Pair<?, ?> getOcrdWorkflow() {
        return ServiceManager.getOcrdWorkflowService().getOcrdWorkflow(template.getOcrdWorkflowId());
    }

    /**
     * Set the OCR-D workflow.
     *
     * @param ocrdWorkflow
     *         The immutable key value pair
     */
    public void setOcrdWorkflow(Pair<?, ?> ocrdWorkflow) {
        String ocrdWorkflowId = StringUtils.EMPTY;
        if (Objects.nonNull(ocrdWorkflow)) {
            ocrdWorkflowId = ocrdWorkflow.getKey().toString();
        }
        template.setOcrdWorkflowId(ocrdWorkflowId);
    }

    /**
     * Check if user is not assigned to the project. Used for disabling projects.
     *
     * @param project
     *            for check
     * @return false if user is assigned to this project, otherwise true
     */
    public boolean isUserNotAssignedToProject(Project project) {
        for (User user : project.getUsers()) {
            if (user.getId().equals(ServiceManager.getUserService().getCurrentUser().getId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Set template by id.
     *
     * @param id
     *            of template to set
     */
    public void setTemplateById(int id) {
        try {
            setTemplate(ServiceManager.getTemplateService().getById(id));
        } catch (DAOException e) {
            Helper.setErrorMessage(ERROR_LOADING_ONE, new Object[] {ObjectType.TEMPLATE.getTranslationSingular(), id },
                logger, e);
        }
    }

    /**
     * Method being used as viewAction for template edit form. If the given
     * parameter 'id' is '0', the form for creating a new template will be
     * displayed.
     *
     * @param id
     *            of the template to load
     */
    public void loadTemplate(int id) {
        try {
            if (id != 0) {
                setTemplate(ServiceManager.getTemplateService().getById(id));
            } else {
                newTemplate();
            }
            setSaveDisabled(false);
        } catch (DAOException e) {
            Helper.setErrorMessage(ERROR_LOADING_ONE, new Object[] {ObjectType.TEMPLATE.getTranslationSingular(), id },
                logger, e);
        }
    }

    /**
     * Method being used as viewAction for task form.
     *
     * @param id
     *            of the task to load
     */
    public void loadTask(int id) {
        try {
            if (id != 0) {
                setTask(ServiceManager.getTaskService().getById(id));
            }
            setSaveDisabled(true);
        } catch (DAOException e) {
            Helper.setErrorMessage(ERROR_LOADING_ONE, new Object[] {ObjectType.TASK.getTranslationSingular(), id },
                logger, e);
        }
    }

    /**
     * Get template.
     *
     * @return value of template
     */
    public Template getTemplate() {
        return template;
    }

    /**
     * Set template.
     *
     * @param template
     *            as Template
     */
    public void setTemplate(Template template) {
        this.template = template;
        this.assignedProjects.clear();
        this.assignedProjects.addAll(template.getProjects());
    }

    /**
     * Get assignedProjects.
     *
     * @return value of assigned projects
     */
    public List<Project> getAssignedProjects() {
        return assignedProjects;
    }

    /**
     * Set assignedProjects.
     *
     * @param assignedProjects
     *            as assignedProjects
     */
    public void setAssignedProjects(List<Project> assignedProjects) {
        this.assignedProjects = assignedProjects;
    }

    /**
     * Get task.
     *
     * @return value of task
     */
    public Task getTask() {
        return task;
    }

    /**
     * Set task.
     *
     * @param task
     *            as Task
     */
    public void setTask(Task task) {
        this.task = task;
    }

    private void prepareTasks() throws DAOException, IOException, WorkflowException, DataException {
        List<Task> templateTasks = new ArrayList<>(this.template.getTasks());
        if (!templateTasks.isEmpty()) {
            this.template.getTasks().clear();
            TemplateService templateService = ServiceManager.getTemplateService();
            templateService.save(template, true);
        }
        if (Objects.nonNull(template.getWorkflow())) {
            Converter converter = new Converter(this.template.getWorkflow().getTitle());
            converter.convertWorkflowToTemplate(this.template);
        }
    }

    /**
     * Check and return whether the template with the provided ID 'templateId' is used by any processes.
     *
     * @param templateId
     *          ID of template to check
     * @return whether template is used by any processes or not
     */
    public boolean isTemplateUsed(int templateId) {
        if (Objects.isNull(templateUsageMap)) {
            templateUsageMap = ServiceManager.getTemplateService().getTemplateUsageMap();
        }
        Boolean isUsed = templateUsageMap.get(templateId);
        return Boolean.TRUE.equals(isUsed);
    }

    /**
     * Check and return whether the current template is used by any processes.
     *
     * @return whether the current template is used by any processes or not
     */
    public boolean isCurrentTemplateInUse() {
        if (Objects.nonNull(this.template) && Objects.nonNull(this.template.getId()) && this.template.getId() > 0) {
            return isTemplateUsed(this.template.getId());
        } else {
            return false;
        }
    }

    /**
     * Get templateFilters.
     *
     * @return value of templateFilters
     */
    public List<String> getTemplateFilters() {
        return templateFilters;
    }

    /**
     * Set templateFilters.
     *
     * @param templateFilters as list of Strings
     */
    public void setTemplateFilters(List<String> templateFilters) {
        this.templateFilters = templateFilters;
    }

    /**
     * Get selectedTemplateFilters.
     *
     * @return value of selectedTemplateFilters
     */
    public List<String> getSelectedTemplateFilters() {
        return selectedTemplateFilters;
    }

    /**
     * Set selectedTemplateFilters.
     *
     * @param selectedTemplateFilters as list of Strings
     */
    public void setSelectedTemplateFilters(List<String> selectedTemplateFilters) {
        this.selectedTemplateFilters = selectedTemplateFilters;
    }

    /**
     * Event listener for template filter changed event.
     */
    public void templateFiltersChanged() {
        setShowInactiveTemplates(selectedTemplateFilters.contains(DEACTIVATED_TEMPLATES_FILTER));
    }
}
