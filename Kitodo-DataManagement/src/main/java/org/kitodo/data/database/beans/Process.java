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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.PostLoad;
import javax.persistence.PostUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.LazyInitializationException;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.kitodo.data.database.converter.ProcessConverter;
import org.kitodo.data.database.enums.CorrectionComments;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.persistence.HibernateUtil;
import org.kitodo.data.database.persistence.ProcessDAO;

@Entity
@Indexed(index = "kitodo-process")
@Table(name = "process")
public class Process extends BaseTemplateBean {

    @Column(name = "sortHelperImages")
    private Integer sortHelperImages;

    @Column(name = "sortHelperArticles")
    private Integer sortHelperArticles;

    @Column(name = "sortHelperMetadata")
    private Integer sortHelperMetadata;

    @Column(name = "sortHelperDocstructs")
    private Integer sortHelperDocstructs;

    @Column(name = "wikiField", columnDefinition = "longtext")
    private String wikiField = "";

    @Column(name = "processBaseUri")
    private String processBaseUri;

    @Column(name = "ordering")
    private Integer ordering;

    @ManyToOne
    @JoinColumn(name = "docket_id", foreignKey = @ForeignKey(name = "FK_process_docket_id"))
    private Docket docket;

    @ManyToOne
    @JoinColumn(name = "project_id", foreignKey = @ForeignKey(name = "FK_process_project_id"))
    private Project project;

    @ManyToOne
    @JoinColumn(name = "ruleset_id", foreignKey = @ForeignKey(name = "FK_process_ruleset_id"))
    private Ruleset ruleset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "FK_process_template_id"))
    private Template template;

    @ManyToOne
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "FK_process_parent_id"))
    private Process parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<Process> children;

    @Transient
    private boolean hasChildren = true;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordering")
    private List<Task> tasks;

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "process", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private List<Comment> comments;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "process_x_property", joinColumns = {
        @JoinColumn(name = "process_id", foreignKey = @ForeignKey(name = "FK_process_x_property_process_id")) }, inverseJoinColumns = {
            @JoinColumn(name = "property_id", foreignKey = @ForeignKey(name = "FK_process_x_property_property_id")) })
    private List<Property> properties;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "template_x_property", joinColumns = {
        @JoinColumn(name = "process_id", foreignKey = @ForeignKey(name = "FK_template_x_property_process_id")) }, inverseJoinColumns = {
            @JoinColumn(name = "property_id", foreignKey = @ForeignKey(name = "FK_template_x_property_property_id")) })
    private List<Property> templates;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "workpiece_x_property", joinColumns = {
        @JoinColumn(name = "process_id", foreignKey = @ForeignKey(name = "FK_workpiece_x_property_process_id")) },
            inverseJoinColumns = {
                @JoinColumn(name = "property_id", foreignKey = @ForeignKey(name = "FK_workpiece_x_property_property_id")) })
    private List<Property> workpieces;

    @LazyCollection(LazyCollectionOption.FALSE)
    @ManyToMany(mappedBy = "processes")
    private List<Batch> batches = new ArrayList<>();

    @Column(name = "exported")
    private boolean exported;

    @Column(name = "inChoiceListShown")
    Boolean inChoiceListShown;

    @Column(name = "ocrd_workflow_id")
    private String ocrdWorkflowId;

    @Transient
    private User blockedUser;

    @Transient
    private List<Map<String, Object>> metadata;

    @Transient
    private int numberOfMetadata;

    @Transient
    private int numberOfImages;

    @Transient
    private int numberOfStructures;

    @Transient
    private String baseType;

    @Transient
    private transient ProcessKeywords processKeywords;

    /**
     * Constructor.
     */
    public Process() {
        this.title = "";
        this.properties = new ArrayList<>();
        this.workpieces = new ArrayList<>();
        this.templates = new ArrayList<>();
        this.children = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.inChoiceListShown = false;
        this.creationDate = new Date();
    }

    @PostLoad
    @PostUpdate
    private void onPostLoad() {
        this.hasChildren = CollectionUtils.isNotEmpty(children);
    }

    /**
     * Returns the number of media in a process. This is a business statistical
     * characteristic. In case of {@code null}, it returns 0.
     *
     * @return the number of media in a process
     */
    public Integer getSortHelperImages() {
        if (this.sortHelperImages == null) {
            this.sortHelperImages = 0;
        }
        return this.sortHelperImages;
    }

    /**
     * Sets the number of media in a process. Since counting all media files on
     * the file system for many processes is slow, the number can be stored here
     * when saving, so that statistics on the number of media can be obtained
     * with an acceptable response time.
     *
     * @param sortHelperImages
     *            the number of media in a process
     */
    public void setSortHelperImages(Integer sortHelperImages) {
        this.sortHelperImages = sortHelperImages;
    }

    /**
     * Returns the sort count. Sort counting is applicable to a process, if it
     * is a child process and is a counted item of a series. This allows to sort
     * the children according to their count. Can be {@code null} if there is no
     * count. In case of {@code null}, it returns 0.
     *
     * @return the sort count
     */
    public Integer getSortHelperArticles() {
        if (this.sortHelperArticles == null) {
            this.sortHelperArticles = 0;
        }
        return this.sortHelperArticles;
    }

    /**
     * Sets the sort count.
     *
     * @param sortHelperArticles
     *            the sort count
     */
    public void setSortHelperArticles(Integer sortHelperArticles) {
        this.sortHelperArticles = sortHelperArticles;
    }

    /**
     * Returns the number of outline elements in a process. This is a business
     * statistical characteristic. In case of {@code null}, it returns 0.
     *
     * @return the number of outline elements in a process
     */
    public Integer getSortHelperDocstructs() {
        if (this.sortHelperDocstructs == null) {
            this.sortHelperDocstructs = 0;
        }
        return this.sortHelperDocstructs;
    }

    /**
     * Sets the number of outline elements in a process. Since the detailed
     * business objects are in a file, the number can be stored here when
     * saving, so that statistics on the number of outline elements can be
     * obtained with an acceptable response time.
     *
     * @param sortHelperDocstructs
     *            the number of outline elements in a process
     */
    public void setSortHelperDocstructs(Integer sortHelperDocstructs) {
        this.sortHelperDocstructs = sortHelperDocstructs;
    }

    /**
     * Returns the number of metadata entries in a process. This is a business
     * statistical characteristic. In case of {@code null}, it returns 0.
     *
     * @return the number of media in a process
     */
    public Integer getSortHelperMetadata() {
        if (this.sortHelperMetadata == null) {
            this.sortHelperMetadata = 0;
        }
        return this.sortHelperMetadata;
    }

    /**
     * Sets the number of metadata entries in a process. Since the detailed
     * business objects are in a file, the number can be stored here when
     * saving, so that statistics on the number of metadata entries can be
     * obtained with an acceptable response time.
     *
     * @param sortHelperMetadata
     *            the number of metadata entries in a process
     */
    public void setSortHelperMetadata(Integer sortHelperMetadata) {
        this.sortHelperMetadata = sortHelperMetadata;
    }

    /**
     * Returns the contents of the wiki field as HTML. Wiki means that something
     * can be changed quickly by anyone. It is a kind of sticky note on which
     * editors can exchange information about a process.
     *
     * @return wiki field as HTML
     */
    public String getWikiField() {
        return this.wikiField;
    }

    /**
     * Sets the content of the wiki field. Primitive HTML tags formatting may be
     * used.
     *
     * @param wikiField
     *            wiki field as HTML
     */
    public void setWikiField(String wikiField) {
        this.wikiField = wikiField;
    }

    /**
     * Returns a process identifier URI. Internally, this is the record number
     * of the process in the processes table of the database, but for external
     * data it can also be another identifier that resolves to a directory in
     * the application's processes directory on the file system.
     *
     * @return the union resource identifier of the process
     */
    public URI getProcessBaseUri() {
        return Objects.isNull(processBaseUri) ? null : URI.create(processBaseUri);
    }

    /**
     * Sets the union resource identifier of the process. This should only be
     * set manually if the data comes from a third party source, otherwise, this
     * is the process record number set by the database.
     *
     * @param processBaseUri
     *            the identification URI of the process
     */
    public void setProcessBaseUri(URI processBaseUri) {
        this.processBaseUri = Objects.isNull(processBaseUri) ? null : processBaseUri.toString();
    }

    /**
     * Get ordering.
     *
     * @return value of ordering
     */
    public Integer getOrdering() {
        return ordering;
    }

    /**
     * Set ordering.
     *
     * @param ordering
     *            as java.lang.Integer
     */
    public void setOrdering(Integer ordering) {
        this.ordering = ordering;
    }

    /**
     * Returns the project the process belongs to. Digitization processes are
     * organized in projects.
     *
     * @return the project the process belongs to
     */
    public Project getProject() {
        return this.project;
    }

    /**
     * Specifies the project to which the process belongs.
     *
     * @param project
     *            project to which the process should belong
     */
    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Returns the business domain specification this process is using.
     *
     * @return the business domain specification
     */
    public Ruleset getRuleset() {
        return this.ruleset;
    }

    /**
     * Sets the business domain specification this process is using.
     *
     * @param ruleset
     *            the business domain specification
     */
    public void setRuleset(Ruleset ruleset) {
        this.ruleset = ruleset;
    }

    /**
     * Returns the docket generation statement to use when creating a docket for
     * this process.
     *
     * @return the docket generation statement
     */
    public Docket getDocket() {
        return docket;
    }

    /**
     * Sets the docket generation statement to use when creating a docket for
     * this process.
     *
     * @param docket
     *            the docket generation statement
     */
    public void setDocket(Docket docket) {
        this.docket = docket;
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
     *            as Template object
     */
    public void setTemplate(Template template) {
        this.template = template;
    }

    /**
     * Get parent.
     *
     * @return value of parent
     */
    public Process getParent() {
        return parent;
    }

    /**
     * Set parent.
     *
     * @param parent
     *            as org.kitodo.data.database.beans.Process
     */
    public void setParent(Process parent) {
        this.parent = parent;
    }

    /**
     * Get children.
     *
     * @return value of children
     */
    public List<Process> getChildren() {
        initialize(new ProcessDAO(), this.children);
        if (Objects.isNull(this.children)) {
            this.children = new ArrayList<>();
        }
        return this.children;
    }

    /**
     * Set children.
     *
     * @param children
     *            as List of Process objects
     */
    public void setChildren(List<Process> children) {
        this.children = children;
    }

    /**
     * Returns the task list of this process.
     *
     * @return the task list
     */
    public List<Task> getTasks() {
        initialize(new ProcessDAO(), this.tasks);
        if (Objects.isNull(this.tasks)) {
            this.tasks = new ArrayList<>();
        }
        return this.tasks;
    }

    /**
     * Returns the tasks of the process without forced initialization.
     *
     * @return the task list
     */
    Collection<Task> getTasksUnmodified() {
        return this.tasks;
    }

    /**
     * Sets the task list of this process.
     *
     * @param tasks
     *            the task list
     */
    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    /**
     * Get list of templates.
     *
     * @return list of Property objects or empty list
     */
    public List<Property> getTemplates() {
        initialize(new ProcessDAO(), this.templates);
        if (Objects.isNull(this.templates)) {
            this.templates = new ArrayList<>();
        }
        return this.templates;

    }

    /**
     * Set list of templates.
     *
     * @param templates
     *            as list of Property objects
     */
    public void setTemplates(List<Property> templates) {
        this.templates = templates;
    }

    /**
     * Get list of workpieces.
     *
     * @return list of Property objects or empty list
     */
    public List<Property> getWorkpieces() {
        initialize(new ProcessDAO(), this.workpieces);
        if (Objects.isNull(this.workpieces)) {
            this.workpieces = new ArrayList<>();
        }
        return this.workpieces;
    }

    /**
     * Set list of workpieces.
     *
     * @param workpieces
     *            as list of Property objects
     */
    public void setWorkpieces(List<Property> workpieces) {
        this.workpieces = workpieces;
    }

    /**
     * Specifies the batches to which the process is assigned. A process can
     * belong to several batches, but for batch automation to work, a process
     * must be assigned to exactly one batch.
     *
     * @return the batches to which the process is assigned
     */
    public List<Batch> getBatches() {
        initialize(new ProcessDAO(), this.batches);
        if (Objects.isNull(this.batches)) {
            this.batches = new ArrayList<>();
        }
        return this.batches;
    }

    /**
     * Sets the list that specifies the batches to which the process is
     * associated. A process can belong to several batches, but but for batch
     * automation to work, a process must be assigned to exactly one batch. The
     * list should not contain duplicates, and must not contain {@code null}s.
     *
     * @param batches
     *            list of batches to which the process is associated
     */
    public void setBatches(List<Batch> batches) {
        if (this.batches == null) {
            this.batches = batches;
        } else {
            this.batches.clear();
            this.batches.addAll(batches);
        }
    }

    /**
     * Get comments.
     *
     * @return value of comments
     */
    public List<Comment> getComments() {
        initialize(new ProcessDAO(), this.comments);
        if (Objects.isNull(this.comments)) {
            this.comments = new ArrayList<>();
        }
        return this.comments;
    }

    /**
     * Set comments.
     *
     * @param comments as List of Comment objects
     */
    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    /**
     * Returns the operational properties of the process. Properties are a tool
     * for third-party modules to store operational properties as key-value
     * pairs, that the application has no knowledge of. This list is not
     * guaranteed to be in reliable order.
     *
     * @return list of properties
     */
    public List<Property> getProperties() {
        initialize(new ProcessDAO(), this.properties);
        if (Objects.isNull(this.properties)) {
            this.properties = new ArrayList<>();
        }
        return this.properties;
    }

    /**
     * Sets the list of operational properties of the process. This list is not
     * guaranteed to preserve its order. It must not contain {@code null}s.
     *
     * @param properties
     *            list of properties as Property
     */
    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    /**
     * Get exported.
     *
     * @return value of exported
     */
    public boolean isExported() {
        return exported;
    }

    /**
     * Set exported.
     *
     * @param exported as boolean
     */
    public void setExported(boolean exported) {
        this.exported = exported;
    }

    /**
     * Get metadata.
     *
     * @return value of metadata
     */
    public List<Map<String, Object>> getMetadata() {
        return metadata;
    }

    /**
     * Set metadata.
     *
     * @param metadata as Map
     */
    public void setMetadata(List<Map<String, Object>> metadata) {
        this.metadata = metadata;
    }

    /**
     * Returns the user who is currently blocking the process's business data.
     * Since the business data is in a file, a user can be granted exclusive
     * access to this file, so that several users do not overwrite concurrent
     * changes by each other. Can be {@code null} if the business data is not
     * currently blocked by any user.
     *
     * @return the user blocking the process
     */
    public User getBlockedUser() {
        return blockedUser;
    }

    /**
     * Sets exclusive (write) access to the business data in this process for a
     * user. Or, set {@code null} to release the blockage. This is a transient
     * value that is not persisted.
     *
     * @param blockedUser
     *            user to grant write access to
     */
    public void setBlockedUser(User blockedUser) {
        this.blockedUser = blockedUser;
    }

    /**
     * Returns the media form of the business object at runtime. Can be
     * {@code null} if no runtime value is available.
     *
     * @return the id of the division representing the media form
     */
    public String getBaseType() {
        return baseType;
    }

    /**
     * Sets the media form of the business object in the database. This is a
     * transient value that is not persisted.
     *
     * @param baseType
     *            id of the division representing the media form
     */
    public void setBaseType(String baseType) {
        this.baseType = baseType;
    }

    /**
     * Get inChoiceListShown.
     *
     * @return value of inChoiceListShown
     */
    public Boolean getInChoiceListShown() {
        return this.inChoiceListShown;
    }

    /**
     * Set inChoiceListShown.
     *
     * @param inChoiceListShown as java.lang.Boolean
     */
    public void setInChoiceListShown(Boolean inChoiceListShown) {
        this.inChoiceListShown = inChoiceListShown;
    }

    /**
     * Determines whether or not two processes are equal. Two instances of
     * {@code Process} are equal if the values of their {@code Id}, {@code Title},
     * {@code OutputName} and {@code CreationDate} member fields are the same.
     *
     * @param object
     *            An object to be compared with this {@code Process}.
     * @return {@code true} if the object to be compared is an instance of
     *         {@code Process} and has the same values; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Process) {
            Process process = (Process) object;
            return Objects.equals(this.getId(), process.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId());
    }

    /**
     * Returns the number of outline elements in a process. Can be {@code null}
     * if no runtime value is available.
     *
     * @return the number of outline elements in a process
     */
    public Integer getNumberOfStructures() {
        return numberOfStructures;
    }

    /**
     * Returns the amount of metadata of the process at runtime. Can be
     * {@code null} if no runtime value is available.
     *
     * @return the amount of metadata
     */
    public Integer getNumberOfMetadata() {
        return numberOfMetadata;
    }

    /**
     * Sets the number of metadata entries in a process. This is a transient
     * value that is not persisted.
     *
     * @param numberOfMetadata
     *            the number of metadata entries in a process
     */
    public void setNumberOfMetadata(Integer numberOfMetadata) {
        this.numberOfMetadata = numberOfMetadata;
    }

    /**
     * Returns the number of media in a process at runtime. Can be {@code null}
     * if no runtime value is available.
     *
     * @return the number of media in a process
     */
    public Integer getNumberOfImages() {
        return numberOfImages;
    }

    /**
     * Sets the number of media in a process. This is a transient value that is
     * not persisted.
     *
     * @param numberOfImages
     *            the number of media in a process
     */
    public void setNumberOfImages(Integer numberOfImages) {
        this.numberOfImages = numberOfImages;
    }

    /**
     * Sets the number of outline elements in a process at runtime. This is a
     * transient value that is not persisted.
     *
     * @param numberOfStructures
     *            the number of outline elements in a process
     */
    public void setNumberOfStructures(Integer numberOfStructures) {
        this.numberOfStructures = numberOfStructures;
    }

    /**
     * Get OCR-D workflow identifier.
     *
     * @return The OCR-D workflow identifier
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
     * Returns the percentage of tasks in the process that are completed. The
     * total of tasks awaiting preconditions, startable, in progress, and
     * completed is {@code 100.0d}.
     *
     * @return percentage of tasks completed
     */
    public Double getProgressClosed() {
        if (CollectionUtils.isEmpty(tasks)) {
            return 0.0;
        }
        return getProgressPercentageExact(TaskStatus.DONE);
    }

    /**
     * Returns the percentage of tasks in the process that are currently being
     * processed. The progress total of tasks waiting for preconditions,
     * startable, in progress, and completed is {@code 100.0d}.
     *
     * @return percentage of tasks in progress
     */
    public Double getProgressInProcessing() {
        if (CollectionUtils.isEmpty(tasks)) {
            return 0.0;
        }
        return getProgressPercentageExact(TaskStatus.INWORK);
    }

    /**
     * Returns the percentage of the process's tasks that are now ready to be
     * processed but have not yet been started. The progress total of tasks
     * waiting for preconditions, startable, in progress, and completed is
     * {@code 100.0d}.
     *
     * @return percentage of startable tasks
     */
    public Double getProgressOpen() {
        if (CollectionUtils.isEmpty(tasks)) {
            return 0.0;
        }
        return getProgressPercentageExact(TaskStatus.OPEN);
    }

    private Double getProgressPercentageExact(TaskStatus status) {
        Map<TaskStatus, Double> taskProgress = ProcessConverter.getTaskProgressPercentageOfProcess(this, true);
        return taskProgress.get(status);
    }

    /**
     * Returns a coded overview of the progress of the process. The larger the
     * number, the more advanced the process is, so it can be used to sort by
     * progress. The numeric code consists of twelve digits, each three digits
     * from 000 to 100 indicate the percentage of tasks completed, currently in
     * progress, ready to start and not yet ready, in that order. For example,
     * 000000025075 means that 25% of the tasks are ready to be started and 75%
     * of the tasks are not yet ready to be started because previous tasks have
     * not yet been processed.
     * 
     * @return overview of the processing status
     */
    public String getProgressCombined() {
        Map<TaskStatus, Double> taskProgress = ProcessConverter.getTaskProgressPercentageOfProcess(this, true);
        return ProcessConverter.getCombinedProgressFromTaskPercentages(taskProgress);
    }

    /**
     * Returns the record number of the parent process, if any. Is {@code null}
     * if there is no parent process above.
     *
     * @return record number of the parent process
     */
    public Integer getParentID() {
        return Objects.nonNull(parent) ? parent.getId() : null;
    }

    /**
     * Sets a parent process based on its record number. Or {@code null} to not
     * set a parent process.
     *
     * @param parentID
     *            record number of the parent process
     */
    public void setParentID(Integer parentID) {
        this.parent = HibernateUtil.getSession().get(Process.class, parentID);
    }

    /**
     * Returns whether the process has children.
     *
     * @return whether the process has children
     */
    public boolean hasChildren() {
        try {
            return CollectionUtils.isNotEmpty(children);
        } catch (LazyInitializationException e) {
            return hasChildren;
        }
    }

    /**
     * Sets whether the process is a parent. The setter can be used when
     * representing data from a third-party source. Internally, parenthood
     * results from a parent relationship of the process in the database.
     * Setting this to true cannot insert child processes into the database.
     *
     * @param hasChildren
     *            whether the process has children
     * @throws UnsupportedOperationException
     *             when trying to set this to true for a process without
     *             children
     */
    public void setHasChildren(boolean hasChildren) {
        if (!hasChildren && Objects.nonNull(children)) {
            children.forEach(child -> child.setParent(null));
            children.clear();
        } else if (hasChildren && CollectionUtils.isEmpty(children)) {
            throw new UnsupportedOperationException("cannot insert child processes");
        }
    }

    /**
     * Returns the name of the last user who was involved in the process. This
     * is the user who recently has a task of the process in progress, or who
     * most recently had one in progress. The name is returned comma-separated,
     * last name first. Can be {@code null} if no user has worked on the process
     * yet.
     *
     * @return name of last user handling task
     */
    public String getLastEditingUser() {
        return ProcessConverter.getLastEditingUser(this);
    }

    /**
     * Returns time day on which a task of this process was last started.
     *
     * @return time on which a task of this process was last started
     */
    public Date getProcessingBeginLastTask() {
        return ProcessConverter.getLastProcessingBegin(this);
    }

    /**
     * Returns the time on which a task from this process was last completed.
     *
     * @return time on which a task from this process was last completed
     */
    public Date getProcessingEndLastTask() {
        return ProcessConverter.getLastProcessingEnd(this);
    }

    /**
     * Returns the error corrections processing state of the process. The value
     * is specified as integer of {@link CorrectionComments}.
     * 
     * @return the error corrections processing state
     */
    public Integer getCorrectionCommentStatus() {
        return ProcessConverter.getCorrectionCommentStatus(this).getValue();
    }

    /**
     * Returns whether the process has any comments.
     *
     * @return whether the process has comments
     */
    public boolean hasComments() {
        return CollectionUtils.isNotEmpty(comments);
    }

    /**
     * Sets whether the process has any comments. This should only be set if the
     * data comes from a third party; internally, it is determined in the
     * database.
     *
     * @param hasComments
     *            whether the process has comments
     */
    public void setHasComments(boolean hasComments) {
        if (!hasComments && Objects.nonNull(comments)) {
            comments.forEach(comment -> comment.setProcess(null));
            comments.clear();
        } else if (hasComments && CollectionUtils.isEmpty(comments)) {
            throw new UnsupportedOperationException("cannot insert comments");
        }
    }

    @Override
    public String toString() {
        return title + " [" + id + "]";
    }

    /**
     * When indexing, outputs the index keywords for free search.
     * 
     * @return the index keywords for free search
     */
    @Transient
    @FullTextField(name = "search")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public String getKeywordsForFreeSearch() {
        return initializeKeywords().getSearch();
    }

    /**
     * When indexing, outputs the index keywords for searching in title.
     * 
     * @return the index keywords for searching in title
     */
    @Transient
    @FullTextField(name = "searchTitle")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public String getKeywordsForSearchingInTitle() {
        return initializeKeywords().getSearchTitle();
    }

    /**
     * When indexing, outputs the index keywords for searching by project name.
     * 
     * @return the index keywords for searching by project name
     */
    @Transient
    @FullTextField(name = "searchProject")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public String getKeywordsForSearchingByProjectName() {
        return initializeKeywords().getSearchProject();
    }

    /**
     * When indexing, outputs the index keywords for searching for assignment to
     * batches.
     * 
     * @return the index keywords for searching for assignment to batches
     */
    @Transient
    @FullTextField(name = "searchBatch")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public String getKeywordsForAssignmentToBatches() {
        return initializeKeywords().getSearchBatch();
    }

    /**
     * When indexing, outputs the index keywords for searching for task
     * information.
     * 
     * @return the index keywords for searching for task information
     */
    @Transient
    @FullTextField(name = "searchTask")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public String getKeywordsForSearchingForTaskInformation() {
        return initializeKeywords().getSearchTask();
    }

    private ProcessKeywords initializeKeywords() {
        if (this.processKeywords == null) {
            ProcessKeywords indexingKeyworder = new ProcessKeywords(this);
            this.processKeywords = indexingKeyworder;
            return indexingKeyworder;
        } else {
            return processKeywords;
        }
    }
}
