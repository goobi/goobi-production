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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.enterprise.context.RequestScoped;
import javax.faces.model.SelectItem;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.production.enums.FilterString;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;

@Named("SearchForm")
@RequestScoped
public class SearchForm {

    /**
     * Logger instance.
     */
    private static final Logger logger = LogManager.getLogger(SearchForm.class);

    private List<String> projects = new ArrayList<>(); // proj:
    private String project = "";

    private List<String> processPropertyTitles = new ArrayList<>(); // processeig:
    private String processPropertyTitle = "";
    private String processPropertyValue = "";

    private List<String> stepTitles = new ArrayList<>(); // step:
    private List<TaskStatus> stepstatus = new ArrayList<>();
    private String status = "";
    private String stepname = "";

    private List<User> user = new ArrayList<>();
    private String stepdonetitle = "";
    private String stepdoneuser = "";

    private String idin = "";
    private String processParentId = "";
    private String processTitle = ""; // proc:

    private String projectOperand = "";
    private String processOperand = "";
    private String processPropertyOperand = "";
    private String masterpiecePropertyOperand = "";
    private String templatePropertyOperand = "";
    private String stepOperand = "";

    private final ProcessForm processForm;
    private final CurrentTaskForm taskForm;

    /**
     * Constructor with inject process form.
     *
     * @param processForm
     *            managed bean
     */
    @Inject
    public SearchForm(ProcessForm processForm, CurrentTaskForm taskForm) {
        this.stepstatus.addAll(ServiceManager.getFilterService().initStepStatus());
        this.projects = ServiceManager.getFilterService().initProjects();
        this.stepTitles = ServiceManager.getFilterService().initStepTitles();
        this.processPropertyTitles = ServiceManager.getFilterService().initProcessPropertyTitles();
        this.user.addAll(ServiceManager.getFilterService().initUserList());
        this.processForm = processForm;
        this.taskForm = taskForm;
    }

    public List<String> getProjects() {
        return this.projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects;
    }

    public List<String> getProcessPropertyTitles() {
        return this.processPropertyTitles;
    }

    public void setProcessPropertyTitles(List<String> processPropertyTitles) {
        this.processPropertyTitles = processPropertyTitles;
    }

    public List<String> getStepTitles() {
        return this.stepTitles;
    }

    public void setStepTitles(List<String> stepTitles) {
        this.stepTitles = stepTitles;
    }

    public List<TaskStatus> getStepstatus() {
        return this.stepstatus;
    }

    public void setStepstatus(List<TaskStatus> stepstatus) {
        this.stepstatus = stepstatus;
    }

    public String getStepdonetitle() {
        return this.stepdonetitle;
    }

    public void setStepdonetitle(String stepdonetitle) {
        this.stepdonetitle = stepdonetitle;
    }

    public String getStepdoneuser() {
        return this.stepdoneuser;
    }

    public void setStepdoneuser(String stepdoneuser) {
        this.stepdoneuser = stepdoneuser;
    }

    public String getIdin() {
        return this.idin;
    }

    public void setIdin(String idin) {
        this.idin = idin;
    }

    public String getProcessParentId() {
        return processParentId;
    }

    public void setProcessParentId(String processParentId) {
        this.processParentId = processParentId;
    }

    public String getProject() {
        return this.project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getProcessTitle() {
        return this.processTitle;
    }

    public void setProcessTitle(String processTitle) {
        this.processTitle = processTitle;
    }

    public String getProcessPropertyTitle() {
        return this.processPropertyTitle;
    }

    public void setProcessPropertyTitle(String processPropertyTitle) {
        this.processPropertyTitle = processPropertyTitle;
    }

    public String getProcessPropertyValue() {
        return this.processPropertyValue;
    }

    public void setProcessPropertyValue(String processPropertyValue) {
        this.processPropertyValue = processPropertyValue;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStepname() {
        return this.stepname;
    }

    public void setStepname(String stepname) {
        this.stepname = stepname;
    }

    public List<User> getUser() {
        return this.user;
    }

    public void setUser(List<User> user) {
        this.user = user;
    }

    /**
     * Filter processes.
     *
     * @return filter as java.lang.String
     */
    public String filterProcesses() {
        processForm.changeFilter(createFilter());
        return processForm.getProcessesPage();
    }

    /**
     * Filter tasks.
     *
     * @return filter as java.lang.String
     */
    public String filterTasks() {
        taskForm.changeFilter(createFilter());
        return taskForm.getTaskListPath();
    }

    private String createFilter() {
        String search = "";
        if (!this.processTitle.isEmpty()) {
            search += "\"" + this.processOperand + "process:" + this.processTitle + "\" ";
        }
        if (!this.idin.isEmpty()) {
            search += "\"" + FilterString.ID.getFilterEnglish() + this.idin + "\" ";
        }
        if (!this.processParentId.isEmpty()) {
            search += "\"" + FilterString.PARENTPROCESSID.getFilterEnglish() + this.processParentId + "\" ";
        }
        if (!this.project.isEmpty()) {
            search += "\"" + this.projectOperand + FilterString.PROJECT.getFilterEnglish() + this.project + "\" ";
        }
        if (!this.stepname.isEmpty()) {
            search += "\"" + this.stepOperand + this.status + ":" + this.stepname + "\" ";
        }
        if (!this.stepdonetitle.isEmpty() && !this.stepdoneuser.isEmpty()
                && ConfigCore.getBooleanParameterOrDefaultValue(ParameterCore.WITH_USER_STEP_DONE_SEARCH)) {
            search += "\"" + FilterString.TASKDONEUSER.getFilterEnglish() + this.stepdoneuser + "\" \""
                    + FilterString.TASKDONETITLE.getFilterEnglish() + this.stepdonetitle + "\" ";
        }
        if (StringUtils.isNotBlank(this.processPropertyValue)) {
            if (StringUtils.isNotBlank(this.processPropertyTitle)) {
                search += "\"" + this.processPropertyOperand + FilterString.PROPERTY.getFilterEnglish() 
                        + this.processPropertyTitle + ":" + this.processPropertyValue + "\" ";
            } else {
                search += "\"" + this.processPropertyOperand + FilterString.PROPERTY.getFilterEnglish() 
                        + "*:" + this.processPropertyValue + "\" ";
            }
        } else {
            if (StringUtils.isNotBlank(this.processPropertyTitle)) {
                search += "\"" + this.processPropertyOperand + FilterString.PROPERTY.getFilterEnglish() 
                        + this.processPropertyTitle + ":*\" ";
            }
        }
        return search;
    }

    private String createSearchProperty(String title, String value, String operand, FilterString filterString) {
        if (Objects.nonNull(value) && !value.isEmpty()) {
            if (Objects.nonNull(title) && !title.isEmpty()) {
                return "\"" + operand + filterString.getFilterEnglish() + title + ":" + value + "\" ";
            } else {
                return "\"" + operand + filterString.getFilterEnglish() + value + "\" ";
            }
        }
        return "";
    }

    /**
     * Get operands.
     *
     * @return list of SelectItem objects
     */
    public List<SelectItem> getOperands() {
        List<SelectItem> answer = new ArrayList<>();
        SelectItem and = new SelectItem("", Helper.getTranslation("AND"));
        SelectItem not = new SelectItem("-", Helper.getTranslation("NOT"));
        answer.add(and);
        answer.add(not);
        return answer;
    }

    public String getProjectOperand() {
        return this.projectOperand;
    }

    public void setProjectOperand(String projectOperand) {
        this.projectOperand = projectOperand;
    }

    public String getProcessPropertyOperand() {
        return this.processPropertyOperand;
    }

    public void setProcessPropertyOperand(String processPropertyOperand) {
        this.processPropertyOperand = processPropertyOperand;
    }

    public String getMasterpiecePropertyOperand() {
        return this.masterpiecePropertyOperand;
    }

    public void setMasterpiecePropertyOperand(String masterpiecePropertyOperand) {
        this.masterpiecePropertyOperand = masterpiecePropertyOperand;
    }

    public String getTemplatePropertyOperand() {
        return this.templatePropertyOperand;
    }

    public void setTemplatePropertyOperand(String templatePropertyOperand) {
        this.templatePropertyOperand = templatePropertyOperand;
    }

    public String getStepOperand() {
        return this.stepOperand;
    }

    public void setStepOperand(String stepOperand) {
        this.stepOperand = stepOperand;
    }

    public String getProcessOperand() {
        return this.processOperand;
    }

    public void setProcessOperand(String processOperand) {
        this.processOperand = processOperand;
    }

}
