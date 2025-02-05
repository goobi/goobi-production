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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.kitodo.data.database.converter.TaskEditTypeConverter;
import org.kitodo.data.database.converter.TaskStatusConverter;
import org.kitodo.data.database.enums.TaskEditType;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.persistence.TaskDAO;

@Entity
@Table(name = "task")
public class Task extends BaseBean {

    @Column(name = "title")
    private String title;

    @Column(name = "ordering")
    private Integer ordering;

    @Column(name = "processingStatus")
    @Convert(converter = TaskStatusConverter.class)
    private TaskStatus processingStatus = TaskStatus.LOCKED;

    @Column(name = "processingTime")
    private Date processingTime;

    @Column(name = "processingBegin")
    private Date processingBegin;

    @Column(name = "processingEnd")
    private Date processingEnd;

    @Column(name = "editType")
    @Convert(converter = TaskEditTypeConverter.class)
    private TaskEditType editType = TaskEditType.UNNOWKN;

    @Column(name = "homeDirectory")
    private short homeDirectory;

    @Column(name = "concurrent")
    private boolean concurrent = false;

    @Column(name = "last")
    private boolean last = false;

    @Column(name = "correction")
    private boolean correction = false;

    @Column(name = "typeMetadata")
    private boolean typeMetadata = false;

    @Column(name = "typeAutomatic")
    private boolean typeAutomatic = false;

    @Column(name = "typeImagesRead")
    private boolean typeImagesRead = false;

    @Column(name = "typeImagesWrite")
    private boolean typeImagesWrite = false;

    @Column(name = "typeGenerateImages")
    private boolean typeGenerateImages = false;

    @Column(name = "typeValidateImages")
    private boolean typeValidateImages = false;

    @Column(name = "typeExportDms")
    private boolean typeExportDMS = false;

    @Column(name = "typeAcceptClose")
    private boolean typeAcceptClose = false;

    @Column(name = "scriptName")
    private String scriptName;

    @Column(name = "scriptPath")
    private String scriptPath;

    @Column(name = "typeCloseVerify")
    private boolean typeCloseVerify = false;

    @Column(name = "batchStep")
    private boolean batchStep = false;

    @Column(name = "repeatOnCorrection")
    private boolean repeatOnCorrection = false;

    @Column(name = "workflowId")
    private String workflowId;

    @ManyToOne
    @JoinColumn(name = "workflowCondition_id", foreignKey = @ForeignKey(name = "FK_task_workflowCondition_id"))
    private WorkflowCondition workflowCondition;

    /**
     * This field contains information about user, which works on this task.
     */
    @ManyToOne
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "FK_task_user_id"))
    private User processingUser;

    @ManyToOne
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "FK_task_template_id"))
    private Template template;

    @ManyToOne
    @JoinColumn(name = "process_id", foreignKey = @ForeignKey(name = "FK_task_process_id"))
    private Process process;

    /**
     * This field contains information about user's roles, which are allowed to
     * work on this task.
     */
    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(name = "task_x_role", joinColumns = {
        @JoinColumn(name = "task_id", foreignKey = @ForeignKey(name = "FK_task_x_role_task_id")) }, inverseJoinColumns = {
            @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "FK_task_x_user_role_id")) })
    private List<Role> roles;

    @Transient
    private String localizedTitle;

    @Transient
    private String processingStatusTitle;

    @Transient
    private String editTypeTitle;

    /**
     * Constructor.
     */
    public Task() {
        this.title = "";
        this.roles = new ArrayList<>();
        this.ordering = 0;
    }

    /**
     * Copy constructor.
     *
     * @param templateTask
     *            task to copy
     */
    public Task(Task templateTask) {
        this.title = templateTask.getTitle();
        this.ordering = templateTask.getOrdering();
        this.typeAutomatic = templateTask.isTypeAutomatic();
        this.scriptName = templateTask.getScriptName();
        this.scriptPath = templateTask.getScriptPath();
        this.batchStep = templateTask.isBatchStep();
        this.concurrent = templateTask.isConcurrent();
        this.last = templateTask.isLast();
        this.typeAcceptClose = templateTask.isTypeAcceptClose();
        this.typeCloseVerify = templateTask.isTypeCloseVerify();
        this.typeExportDMS = templateTask.isTypeExportDMS();
        this.typeImagesRead = templateTask.isTypeImagesRead();
        this.typeImagesWrite = templateTask.isTypeImagesWrite();
        this.typeMetadata = templateTask.isTypeMetadata();
        this.typeGenerateImages = templateTask.isTypeGenerateImages();
        this.typeValidateImages = templateTask.isTypeValidateImages();
        this.repeatOnCorrection = templateTask.isRepeatOnCorrection();
        this.processingStatus = templateTask.getProcessingStatus();
        this.homeDirectory = templateTask.getHomeDirectory();
        this.workflowId = templateTask.getWorkflowId();
        this.workflowCondition = templateTask.getWorkflowCondition();

        // necessary to create new ArrayList in other case session problem!
        this.roles = new ArrayList<>(templateTask.getRoles());
    }

    /**
     * Returns the name of the task. This is usually a human-readable,
     * aphoristic summary of the activity to be performed.
     *
     * @return the name
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the name of the task.
     *
     * @param title
     *            name to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the ordinal number of the task. Tasks can be processed
     * sequentially. When a task with a lower ordinal number is completed, the
     * task with the next higher ordinal number can be processed. If several
     * tasks share the same ordinal number, they can be processed in parallel.
     *
     * @return ordinal number of the task
     */
    public Integer getOrdering() {
        return this.ordering;
    }

    /**
     * Sets the ordinal number of the task. This sets the consecutive execution
     * point relative to the other tasks in the process. May be the same as the
     * number of another task if they are to be processed in parallel. Must not
     * be {@code null}.
     *
     * @param ordering
     *            ordinal number of the task
     */
    public void setOrdering(Integer ordering) {
        this.ordering = ordering;
    }

    /**
     * Returns the processing type of the task. Possible are:
     * <dl>
     * <dt>UNNOWKN</dt>
     * <dd>The processing type of the task is not defined.</dd>
     * <dt>MANUAL_SINGLE</dt>
     * <dd>The task was taken over and carried out by a user.</dd>
     * <dt>MANUAL_MULTI</dt>
     * <dd>The task was taken over and carried out by a user as part of the
     * batch processing functionality.</dd>
     * <dt>ADMIN</dt>
     * <dd>The task status has been changed administratively using the status
     * increase or status decrease functions.</dd>
     * <dt>AUTOMATIC</dt>
     * <dd>The automatic task was carried out by the workflow system.</dd>
     * <dt>QUEUE</dt>
     * <dd>The task status was changed via the Active MQ interface.</dd>
     * </dl>
     *
     * @return the processing type
     */
    public TaskEditType getEditType() {
        return this.editType;
    }

    /**
     * Sets the processing type of the task.
     *
     * @param editType
     *            processing type to set
     */
    public void setEditType(TaskEditType editType) {
        this.editType = editType;
    }

    /**
     * Sets the processing status of the task.
     *
     * @param processingStatus
     *            processing status to set
     */
    public void setProcessingStatus(TaskStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    /**
     * Returns the processing status of the task. A task can:
     * <dl>
     * <dt>LOCKED</dt>
     * <dd>wait for previous tasks to complete to become ready to start</dd>
     * <dt>OPEN</dt>
     * <dd>ready to go and waiting to be taken over</dd>
     * <dt>INWORK</dt>
     * <dd>currently being carried out</dd>
     * <dt>DONE</dt>
     * <dd>be finished</dd>
     * </dl>
     *
     * @return the processing status
     */
    public TaskStatus getProcessingStatus() {
        return this.processingStatus;
    }

    /**
     * Returns the time the task status was last changed. This references
     * <i>any</i> activity on the task that involves a change in status.
     * {@link Date} is a specific instant in time, with millisecond precision.
     *
     * @return the time the task status was last changed
     */
    public Date getProcessingTime() {
        return this.processingTime;
    }

    /**
     * Sets the time the task status was last changed.
     *
     * @param processingTime
     *            time to set
     */
    public void setProcessingTime(Date processingTime) {
        this.processingTime = processingTime;
    }

    /**
     * Returns the time when the task was accepted for processing.
     *
     * @return the time when the task was accepted for processing
     */
    public Date getProcessingBegin() {
        return this.processingBegin;
    }

    /**
     * Sets the time the task was accepted for processing.
     *
     * @param processingBegin
     *            time to set
     */
    public void setProcessingBegin(Date processingBegin) {
        this.processingBegin = processingBegin;
    }

    /**
     * Returns the time when the task was completed.
     *
     * @return the time when the task was completed
     */
    public Date getProcessingEnd() {
        return this.processingEnd;
    }

    /**
     * Sets the time the task was completed.
     *
     * @param processingEnd
     *            time to set
     */
    public void setProcessingEnd(Date processingEnd) {
        this.processingEnd = processingEnd;
    }

    public short getHomeDirectory() {
        return this.homeDirectory;
    }

    public void setHomeDirectory(short homeDirectory) {
        this.homeDirectory = homeDirectory;
    }

    /**
     * Get concurrent.
     *
     * @return value of concurrent
     */
    public boolean isConcurrent() {
        return concurrent;
    }

    /**
     * Set concurrent.
     *
     * @param concurrent
     *            as boolean
     */
    public void setConcurrent(boolean concurrent) {
        this.concurrent = concurrent;
    }

    /**
     * Get information if task is the last task in the workflow.
     *
     * @return information if task is the last task in the workflow
     */
    public boolean isLast() {
        return last;
    }

    /**
     * Set last information if task is the last task in the workflow.
     *
     * @param last
     *            as true or false
     */
    public void setLast(boolean last) {
        this.last = last;
    }

    /**
     * Returns whether the task is in a correction run. In a five-step workflow,
     * if a correction request occurs from the fourth step to the second step,
     * steps two and three are in the correction run.
     *
     * @return whether the task is in a correction run
     */
    public boolean isCorrection() {
        return correction;
    }

    /**
     * Sets whether the task is in a correction run.
     *
     * @param correction
     *            whether the task is in a correction run
     */
    public void setCorrection(boolean correction) {
        this.correction = correction;
    }

    /**
     * Returns the user who last worked on the task.
     *
     * @return the user who last worked on the task
     */
    public User getProcessingUser() {
        return this.processingUser;
    }

    /**
     * Sets the user who last worked on the task.
     *
     * @param processingUser
     *            user to set
     */
    public void setProcessingUser(User processingUser) {
        this.processingUser = processingUser;
    }

    /**
     * Returns the process this task belongs to. Can be {@code null} if the task
     * belongs to a production template, and not a process.
     *
     * @return the process this task belongs to
     */
    public Process getProcess() {
        return this.process;
    }

    /**
     * Sets the process this task belongs to. A task can only ever be assigned
     * to <i>either</i> a process <i>or</i> a production template.
     *
     * @param process
     *            process this task belongs to
     */
    public void setProcess(Process process) {
        this.process = process;
    }

    /**
     * Returns the production template this task belongs to. Can be {@code null}
     * if the task belongs to a process, and not a production template.
     *
     * @return the production template this task belongs to
     */
    public Template getTemplate() {
        return this.template;
    }

    /**
     * Sets the production template this task belongs to. A task can only ever
     * be assigned to <i>either</i> a production template <i>or</i> a process.
     *
     * @param template
     *            template this task belongs to
     */
    public void setTemplate(Template template) {
        this.template = template;
    }

    /**
     * Get list of roles.
     *
     * @return list of Role objects or empty list
     */
    public List<Role> getRoles() {
        initialize(new TaskDAO(), this.roles);
        if (Objects.isNull(this.roles)) {
            this.roles = new ArrayList<>();
        }
        return this.roles;
    }

    /**
     * Returns how many roles are allowed to take on this task.
     *
     * @return how many roles are allowed to take on this task
     */
    public int getRolesSize() {
        List<Integer> roles = getRoleIds();
        return Objects.nonNull(roles) ? roles.size() : 0;
    }

    /**
     * Set list of roles.
     *
     * @param roles
     *            as list of Role objects
     */
    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    /**
     * Get list of folders whose contents are to be generated.
     *
     * @return list of Folder objects or empty list
     */
    public List<Folder> getContentFolders() {
        List<Folder> contentFolders = typeGenerateImages
                ? process.getProject().getFolders().parallelStream()
                        .filter(folder -> folder.getDerivative().isPresent() || folder.getDpi().isPresent()
                                || folder.getImageSize().isPresent())
                        .collect(Collectors.toList())
                : Collections.emptyList();
        return contentFolders;
    }

    /**
     * Get list of folders whose contents are to be validated.
     *
     * @return list of Folder objects or empty list
     */
    public List<Folder> getValidationFolders() {
        List<Folder> validationFolders = typeValidateImages
                ? process.getProject().getFolders().parallelStream()
                        .filter(Folder::isValidateFolder)
                        .collect(Collectors.toList())
                : Collections.emptyList();
        return validationFolders;
    }

    /**
     * Returns whether this task gives the user file system access. They can
     * then access the folder for or containing the digitized files and create,
     * view or edit files.
     *
     * @return whether the user gets file access
     */
    public boolean isTypeImagesRead() {
        return this.typeImagesRead;
    }

    /**
     * Sets whether this task gives the user file system access.
     *
     * @param typeImagesRead
     *            whether the user gets file access
     */
    public void setTypeImagesRead(boolean typeImagesRead) {
        this.typeImagesRead = typeImagesRead;
    }

    /**
     * Returns whether the user is allowed to create or modify files. When
     * digitizing, the user probably needs write access to the file area of the
     * process, but perhaps not for control tasks.
     *
     * @return whether the user gets write access
     */
    public boolean isTypeImagesWrite() {
        return this.typeImagesWrite;
    }

    /**
     * Sets whether the user is allowed to create or modify files. If true, the
     * user is also given file system access.
     *
     * @param typeImagesWrite
     *            whether the user gets write access
     */
    public void setTypeImagesWrite(boolean typeImagesWrite) {
        this.typeImagesWrite = typeImagesWrite;
        if (typeImagesWrite) {
            this.typeImagesRead = true;
        }
    }

    /**
     * Get type generate images.
     *
     * @return value of typeGenerateImages
     */
    public boolean isTypeGenerateImages() {
        return typeGenerateImages;
    }

    /**
     * Set type generate images.
     *
     * @param generateImages as boolean
     */
    public void setTypeGenerateImages(boolean generateImages) {
        this.typeGenerateImages = generateImages;
    }

    /**
     * Get type validate images.
     *
     * @return value of typeValidateImages
     */
    public boolean isTypeValidateImages() {
        return typeValidateImages;
    }

    /**
     * Set type validate images.
     *
     * @param validateImages as boolean
     */
    public void setTypeValidateImages(boolean validateImages) {
        this.typeValidateImages = validateImages;
    }

    public boolean isTypeExportDMS() {
        return this.typeExportDMS;
    }

    public void setTypeExportDMS(boolean typeExportDMS) {
        this.typeExportDMS = typeExportDMS;
    }

    /**
     * Returns whether the editor for the digital copy is made available to the
     * user. The editor offers a UI in which the structure of the previously
     * loosely digitized media can be detailed.
     *
     * @return whether the editor is available
     */
    public boolean isTypeMetadata() {
        return this.typeMetadata;
    }

    /**
     * Sets whether the editor is available in the task.
     *
     * @param typeMetadata
     *            whether the editor is available
     */
    public void setTypeMetadata(boolean typeMetadata) {
        this.typeMetadata = typeMetadata;
    }

    public boolean isTypeAcceptClose() {
        return this.typeAcceptClose;
    }

    public void setTypeAcceptClose(boolean typeAcceptClose) {
        this.typeAcceptClose = typeAcceptClose;
    }

    /**
     * Returns whether the task is of automatic type. Automatic tasks are taken
     * over by the workflow system itself, the actions selected in it are
     * carried out, such as an export or a script call, and then the task is
     * completed.
     *
     * @return whether the task is automatic
     */
    public boolean isTypeAutomatic() {
        return this.typeAutomatic;
    }

    /**
     * Sets whether the task is of automatic type.
     *
     * @param typeAutomatic
     *            whether the task is automatic
     */
    public void setTypeAutomatic(boolean typeAutomatic) {
        this.typeAutomatic = typeAutomatic;
    }

    public boolean isTypeCloseVerify() {
        return this.typeCloseVerify;
    }

    public void setTypeCloseVerify(boolean typeCloseVerify) {
        this.typeCloseVerify = typeCloseVerify;
    }

    public String getScriptName() {
        return this.scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    public String getScriptPath() {
        return this.scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    /**
     * Get workflow id - id of task object in diagram - by this id we can identify
     * change done to task.
     *
     * @return workflow id as String
     */
    public String getWorkflowId() {
        return workflowId;
    }

    /**
     * Set workflow id.
     *
     * @param workflowId
     *            id of task object in diagram - by this id we can identify change
     *            done to task
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    /**
     * Get workflowCondition.
     *
     * @return value of workflowCondition
     */
    public WorkflowCondition getWorkflowCondition() {
        return workflowCondition;
    }

    /**
     * Set workflowCondition.
     *
     * @param workflowCondition
     *            as String
     */
    public void setWorkflowCondition(WorkflowCondition workflowCondition) {
        this.workflowCondition = workflowCondition;
    }

    /**
     * Returns whether accepting or completing this task applies to the batch.
     * If so, with a single UI interaction, the user automatically executes an
     * action for all tasks of all processes in the batch, that have the same
     * name and are in the same state. This saves users from a lot of mouse
     * work.
     *
     * @return whether actions apply to the batch
     */
    public boolean isBatchStep() {
        return this.batchStep;
    }

    /**
     * Sets whether batch automation is possible for this task. The setter can
     * be used when representing data from a third-party source. Internally it
     * depends on, whether the process containing the task is assigned to
     * exactly one batch.
     *
     * @param batchStep
     *            whether batch automation is possible
     * @throws UnsupportedOperationException
     *             when the value doesn't match the background database
     */
    public void setBatchStep(boolean batchStep) {
        this.batchStep = batchStep;
    }

    /**
     * Get information if task should be repeated on correction.
     *
     * @return value of repeatOnCorrection
     */
    public boolean isRepeatOnCorrection() {
        return repeatOnCorrection;
    }

    /**
     * Set information if task should be repeated on correction.
     *
     * @param repeatOnCorrection as boolean
     */
    public void setRepeatOnCorrection(boolean repeatOnCorrection) {
        this.repeatOnCorrection = repeatOnCorrection;
    }

    /**
     * Returns the translated name of the task at runtime. This can be
     * translated at runtime for the user currently performing this task via the
     * application's language resource files. Can be {@code null} if currently
     * no value is available.
     *
     * @return translated name
     */
    public String getLocalizedTitle() {
        return this.localizedTitle;
    }

    /**
     * Sets the translated name of the task at runtime. This is a transient
     * value that is not persisted.
     *
     * @param localizedTitle
     *            translated name to set
     */
    public void setLocalizedTitle(String localizedTitle) {
        this.localizedTitle = localizedTitle;
    }

    // Here will be methods which should be in TaskService but are used by jsp
    // files
    /**
     * Get task title with user full name.
     *
     * @return task title with user full name as String
     */
    public String getTitleWithUserName() {
        String titleWithUserName = this.getTitle();
        if (this.getProcessingUser() != null && this.getProcessingUser().getId() != null
                && this.getProcessingUser().getId() != 0) {
            titleWithUserName += " (" + this.getProcessingUser().getFullName() + ")";
        }
        return titleWithUserName;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Task) {
            Task task = (Task) object;
            return Objects.equals(this.getId(), task.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, processingTime, processingBegin, processingEnd, process, template);
    }

    /**
     * Returns the translated processing status of the task at runtime. This can
     * be translated at runtime for the user currently displaying this task. Can
     * be {@code null} if currently no value is available.
     *
     * @return translated processing status
     */
    public String getProcessingStatusTitle() {
        return processingStatusTitle;
    }

    /**
     * Sets the translated processing status of the task at runtime. This is a
     * transient value that is not persisted.
     *
     * @param processingStatusTitle
     *            translated processing status to set
     */
    public void setProcessingStatusTitle(String processingStatusTitle) {
        this.processingStatusTitle = processingStatusTitle;
    }

    /**
     * Returns the translated processing type of the task at runtime. This can
     * be translated at runtime for the user currently displaying this task. Can
     * be {@code null} if currently no value is available.
     *
     * @return translated processing status
     */
    public String getEditTypeTitle() {
        return editTypeTitle;
    }

    /**
     * Sets the translated processing type of the task at runtime. This is a
     * transient value that is not persisted.
     *
     * @param editTypeTitle
     *            translated processing type to set
     */
    public void setEditTypeTitle(String editTypeTitle) {
        this.editTypeTitle = editTypeTitle;
    }

    /**
     * Returns a list of the IDs of the roles, whose holders are allowed to
     * perform this task. This list is not guaranteed to be in reliable order.
     *
     * @return list of the IDs of the roles
     */
    public List<Integer> getRoleIds() {
        if (Objects.isNull(roles)) {
            return Collections.emptyList();
        }
        return getRoles().stream().map(Role::getId).collect(Collectors.toList());
    }
}
