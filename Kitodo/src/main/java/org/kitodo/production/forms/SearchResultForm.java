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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.workflow.WorkflowControllerService;
import org.primefaces.PrimeFaces;

@Named("SearchResultForm")
@SessionScoped
public class SearchResultForm extends ProcessListBaseView {
    private static final Logger logger = LogManager.getLogger(SearchResultForm.class);

    private static final String SEARCH_RESULT_VIEW_ID = "/pages/searchResult.xhtml";
    private static final String SEARCH_RESULT_TABLE_ID = "searchResultTabView:searchResultForm:searchResultTable";

    private List<Process> filteredList = new ArrayList<>();
    private List<Process> resultList = new ArrayList<>();
    private String searchQuery;
    private String currentTaskFilter;
    private Integer currentProjectFilter;
    private Integer currentTaskStatusFilter;
    private final WorkflowControllerService workflowControllerService = new WorkflowControllerService();

    /**
     * Searches for processes with the entered searchQuery.
     *
     * @return The searchResultPage
     */
    public String searchForProcessesBySearchQuery() {
        return MessageFormat.format(REDIRECT_PATH, "processes") + "&input=" + searchQuery;
    }

    /**
     * Filters the searchResults by project.
     */
    void filterListByProject() {
        if (Objects.nonNull(currentProjectFilter)) {
            this.filteredList.removeIf(result -> !result.getProject().getId()
                    .equals(currentProjectFilter));
        }
    }

    /**
     * Filters the searchResults by task and status.
     */
    void filterListByTaskAndStatus() {
        if (Objects.nonNull(currentTaskFilter) && Objects.nonNull(currentTaskStatusFilter)) {
            for (Process process : new ArrayList<>(this.filteredList)) {
                boolean remove = true;
                for (Task task : process.getTasks()) {
                    if (task.getTitle().equalsIgnoreCase(currentTaskFilter)
                            && task.getProcessingStatus().getValue().equals(currentTaskStatusFilter)) {
                        remove = false;
                        break;
                    }
                }
                if (remove) {
                    this.filteredList.remove(process);
                }

            }
        }
    }

    /**
     * Filters the searchResultList by the selected filters.
     */
    public void filterList() {
        refreshFilteredList();

        filterListByProject();
        filterListByTaskAndStatus();
    }

    /**
     * Get all Projects assigned to the search results.
     *
     * @return A list of projects for filter list
     */
    public Collection<Project> getProjectsForFiltering() {
        HashMap<Integer, Project> projectsForFiltering = new HashMap<>();
        for (Process process : this.resultList) {
            projectsForFiltering.put(process.getProject().getId(), process.getProject());
        }
        return projectsForFiltering.values();
    }

    /**
     * Get all current Tasks from to the search results.
     *
     * @return A list of tasks for filter list
     */
    public Collection<Task> getTasksForFiltering() {
        HashMap<String, Task> tasksForFiltering = new HashMap<>();
        for (Process process : this.resultList) {
            for (Task currentTask : process.getTasks()) {
                tasksForFiltering.put(currentTask.getTitle(), currentTask);
            }
        }
        return tasksForFiltering.values();
    }

    /**
     * Get the values of taskStatus.
     * @return a list of status
     */
    public Collection<TaskStatus> getTaskStatusForFiltering() {
        return Arrays.asList(TaskStatus.values());
    }

    private void refreshFilteredList() {
        this.filteredList.clear();
        this.filteredList.addAll(this.resultList);
    }

    /**
     * Delete Process.
     *
     * @param process
     *            process to delete.
     */
    @Override
    public void delete(Process process) {
        if (process.getChildren().isEmpty()) {
            try {
                ProcessService.deleteProcess(process);
                this.filteredList.remove(process);
            } catch (DAOException | IOException e) {
                Helper.setErrorMessage(ERROR_DELETING, new Object[] {ObjectType.PROCESS.getTranslationSingular() },
                    logger, e);
            }
        } else {
            this.deleteProcessDialog = new DeleteProcessDialog();
            this.deleteProcessDialog.setProcess(process);
            PrimeFaces.current().executeScript("PF('deleteChildrenDialog').show();");
        }
    }

    /**
     * Set up processing status selection.
     */
    public void setTaskStatusUpForSelection() {
        this.workflowControllerService.setTaskStatusUpForProcesses(this.getSelectedProcesses());
    }

    /**
     * Set down processing status selection.
     */
    public void setTaskStatusDownForSelection() {
        this.workflowControllerService.setTaskStatusDownForProcesses(this.getSelectedProcesses());
    }

    /**
     * Gets the filtered list.
     *
     * @return a list of Process
     */
    public List<Process> getFilteredList() {
        return this.filteredList;
    }

    /**
     * Sets the filtered list.
     *
     * @param filteredList
     *            a list of Process
     */
    public void setFilteredList(List<Process> filteredList) {
        this.filteredList = filteredList;
    }

    /**
     * Get currentTaskStatusFilter.
     *
     * @return value of currentTaskStatusFilter
     */
    public Integer getCurrentTaskStatusFilter() {
        return currentTaskStatusFilter;
    }

    /**
     * Set currentTaskStatusFilter.
     *
     * @param currentTaskStatusFilter as java.lang.Integer
     */
    public void setCurrentTaskStatusFilter(Integer currentTaskStatusFilter) {
        this.currentTaskStatusFilter = currentTaskStatusFilter;
    }

    /**
     * Gets the search query.
     *
     * @return the search query
     */
    public String getSearchQuery() {
        return searchQuery;
    }

    /**
     * Sets the searchQuery.
     *
     * @param searchQuery
     *            the query to search for
     */
    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    /**
     * Gets the current task filter.
     *
     * @return a List of Task title to filter
     */
    public String getCurrentTaskFilter() {
        return currentTaskFilter;
    }

    /**
     * Sets the current task filter.
     *
     * @param currentTaskFilter
     *            the task title to filter
     */
    public void setCurrentTaskFilter(String currentTaskFilter) {
        this.currentTaskFilter = currentTaskFilter;
    }

    /**
     * Gets the current project filter.
     *
     * @return a List of project id to filter
     */
    public Integer getCurrentProjectFilter() {
        return currentProjectFilter;
    }

    /**
     * Sets the current project filter.
     *
     * @param currentProjectFilter
     *            the project id to filter
     */
    public void setCurrentProjectFilter(Integer currentProjectFilter) {
        this.currentProjectFilter = currentProjectFilter;
    }

    /**
     * This method resets the view state of the search result table, which was
     * enabled by adding "multiViewState=true" to the DataTable component.
     * 
     * <p>This affects both the pagination state and the sort order of 
     * the table. It should be called whenever a new search is triggered such 
     * that the user is presented with the most relevant results (page 1) 
     * in the default order.</p>
     */
    public void resetSearchResultTableViewState() {
        if (!Objects.isNull(FacesContext.getCurrentInstance())) {
            // clear multi view state only if there is a state available
            Object mvs = PrimeFaces.current().multiViewState().get(SEARCH_RESULT_VIEW_ID, SEARCH_RESULT_TABLE_ID, false, null);
            if (Objects.nonNull(mvs)) {
                PrimeFaces.current().multiViewState().clear(SEARCH_RESULT_VIEW_ID, SEARCH_RESULT_TABLE_ID);
            }
        }
    }

}
