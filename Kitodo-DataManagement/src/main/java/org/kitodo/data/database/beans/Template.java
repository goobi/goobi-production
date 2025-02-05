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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.kitodo.data.database.persistence.TemplateDAO;

@Entity
@Table(name = "template")
public class Template extends BaseTemplateBean {

    @Column(name = "active")
    private Boolean active = true;

    @ManyToOne
    @JoinColumn(name = "client_id", foreignKey = @ForeignKey(name = "FK_template_client_id"))
    private Client client;

    @ManyToOne
    @JoinColumn(name = "docket_id", foreignKey = @ForeignKey(name = "FK_template_docket_id"))
    private Docket docket;

    @ManyToOne
    @JoinColumn(name = "ruleset_id", foreignKey = @ForeignKey(name = "FK_template_ruleset_id"))
    private Ruleset ruleset;

    @ManyToOne
    @JoinColumn(name = "workflow_id", foreignKey = @ForeignKey(name = "FK_template_workflow_id"))
    private Workflow workflow;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Process> processes;

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks;

    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(name = "project_x_template", joinColumns = {
        @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "FK_project_x_template_template_id")) },
            inverseJoinColumns = {
                @JoinColumn(name = "project_id", foreignKey = @ForeignKey(name = "FK_project_x_template_project_id")) })
    private List<Project> projects;

    @Column(name = "ocrd_workflow_id")
    private String ocrdWorkflowId;

    /**
     * Constructor.
     */
    public Template() {
        this.title = "";
        this.tasks = new ArrayList<>();
        this.creationDate = new Date();
    }

    /**
     * Returns whether this production template is active. Production templates
     * that are no not to be used (anymore) can be deactivated. If processes
     * exist from them, they cannot be deleted.
     *
     * @return whether this production template is active
     */
    public boolean isActive() {
        if (Objects.isNull(this.active)) {
            this.active = true;
        }
        return this.active;
    }

    /**
     * Sets whether this production template is active.
     *
     * @param active
     *            whether this production template is active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Get client.
     *
     * @return the client bean
     */
    public Client getClient() {
        return client;
    }

    /**
     * Set client.
     *
     * @param client
     *            bean
     */
    public void setClient(Client client) {
        this.client = client;
    }

    /**
     * Returns the docket generation statement to use when creating dockets for
     * processes derived from this process template.
     *
     * @return the docket generation statement
     */
    public Docket getDocket() {
        return docket;
    }

    /**
     * Sets the docket generation statement to use when creating dockets for
     * processes derived from this process template.
     *
     * @param docket
     *            the docket generation statement
     */
    public void setDocket(Docket docket) {
        this.docket = docket;
    }

    /**
     * Returns the business domain specification derived from this process
     * template template shall be using.
     *
     * @return the business domain specification
     */
    public Ruleset getRuleset() {
        return this.ruleset;
    }

    /**
     * Sets the business domain specification derived from this process template
     * template shall be using.
     *
     * @param ruleset
     *            the business domain specification
     */
    public void setRuleset(Ruleset ruleset) {
        this.ruleset = ruleset;
    }
    
    /**
     * Returns the workflow from which the production template was created. The
     * tasks of the production template are not created directly in the
     * production template, but are edited using the workflow.
     *
     * @return the workflow
     */
    public Workflow getWorkflow() {
        return workflow;
    }

    /**
     * Sets the workflow from which the production template was created.
     *
     * @param workflow
     *            workflow to set
     */
    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    /**
     * Get OCR-D workflow identifier.
     *
     * @return value of OCR-D workflow identifier
     */
    public String getOcrdWorkflowId() {
        return ocrdWorkflowId;
    }

    /**
     * Set the OCR-D workflow identifier.
     *
     * @param ocrdWorkflowId
     *         The identifier of the OCR-D workflow
     */
    public void setOcrdWorkflowId(String ocrdWorkflowId) {
        this.ocrdWorkflowId = ocrdWorkflowId;
    }

    /**
     * Returns the list of all projects that use this production template. A
     * production template can be used in multiple projects, even across
     * multiple clients. This list is not guaranteed to be in reliable order.
     *
     * @return the list of all projects that use this production template
     */
    public List<Project> getProjects() {
        initialize(new TemplateDAO(), this.projects);
        if (Objects.isNull(this.projects)) {
            this.projects = new ArrayList<>();
        }
        return this.projects;
    }

    /**
     * Sets the list of all projects that use this production template. The list
     * should not contain duplicates, and must not contain {@code null}s.
     *
     * @param projects
     *            projects list to set
     */
    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    /**
     * Get processes.
     *
     * @return value of processes
     */
    public List<Process> getProcesses() {
        initialize(new TemplateDAO(), this.processes);
        if (Objects.isNull(this.processes)) {
            this.processes = new ArrayList<>();
        }
        return this.processes;
    }

    /**
     * Set processes.
     *
     * @param processes as Lis of Process
     */
    public void setProcesses(List<Process> processes) {
        this.processes = processes;
    }

    /**
     * Returns the task list of this process template.
     *
     * @return the task list
     */
    public List<Task> getTasks() {
        initialize(new TemplateDAO(), this.tasks);
        if (Objects.isNull(this.tasks)) {
            this.tasks = new ArrayList<>();
        }
        return this.tasks;
    }

    /**
     * Sets the task list of this process template.
     *
     * @param tasks
     *            the task list
     */
    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    /**
     * Determines whether or not two processes are equal. Two instances of
     * {@code Template} are equal if the values of their {@code Id} member fields are the same.
     *
     * @param object
     *            An object to be compared with this {@code Template}.
     * @return {@code true} if the object to be compared is an instance of
     *         {@code Template} and has the same values; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Template) {
            Template template = (Template) object;
            return Objects.equals(this.getId(), template.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.nonNull(id) ? id : super.hashCode();
    }

    /**
     * Returns whether the production template is valid. To do this, it must
     * contain at least one task and each task must have at least one role
     * assigned to it.
     *
     * @return whether the production template is valid
     */
    public boolean isCanBeUsedForProcess() {
        if (Objects.isNull(tasks)) {
            return false;
        }
        return tasks.stream().allMatch(task -> CollectionUtils.isNotEmpty(task.getRoles()));
    }
}
