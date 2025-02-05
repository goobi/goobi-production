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

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.kitodo.data.database.enums.CommentType;

@Entity
@Table(name = "comment")
public class Comment extends BaseBean {
    /**
     * The field message holds the comment message.
     */
    @Column(name = "message", columnDefinition = "longtext")
    private String message;

    /**
     * The field type holds the comment type.
     */
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private CommentType type;

    /**
     * The field isCorrected contains the information if the correction
     * task is corrected or not.
     */
    @Column(name = "isCorrected")
    private boolean corrected;

    /**
     * The field creationDate holds the comment's creation date.
     */
    @Column(name = "creationDate")
    private Date creationDate;

    /**
     * The field correctionDate holds the date of correcting the task.
     */
    @Column(name = "correctionDate")
    private Date correctionDate;

    /**
     * This field contains information about user, which create the comment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "FK_comment_user_id"))
    private User author;

    /**
     * This field contains information about the currentTask, when the comment is created.
     */
    @ManyToOne
    @JoinColumn(name = "currentTask_id", foreignKey = @ForeignKey(name = "FK_comment_currentTask_id"))
    private Task currentTask;

    /**
     * This field contains information about the correctionTask, where the user can correct the error.
     */
    @ManyToOne
    @JoinColumn(name = "correctionTask_id", foreignKey = @ForeignKey(name = "FK_comment_correctionTask_id"))
    private Task correctionTask;


    /**
     * The field process holds the process of the comment.
     */
    @ManyToOne
    @JoinColumn(name = "process_id", foreignKey = @ForeignKey(name = "FK_comment_process_id"))
    private Process process;

    /**
     * Get message.
     *
     * @return value of message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set message.
     *
     * @param message as java.lang.String
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Get message with HTML compatible line breaks.
     * @return value of message with line break tags
     */
    public String getMessageFormatted() {
        return message.replace("\n", "<br/>");
    }

    /**
     * Get type.
     *
     * @return value of type
     */
    public CommentType getType() {
        return type;
    }

    /**
     * Set type.
     *
     * @param type as org.kitodo.data.database.enums.CommentType
     */
    public void setType(CommentType type) {
        this.type = type;
    }

    /**
     * Get corrected.
     *
     * @return value of corrected
     */
    public boolean isCorrected() {
        return corrected;
    }

    /**
     * Set corrected.
     *
     * @param corrected as boolean
     */
    public void setCorrected(boolean corrected) {
        this.corrected = corrected;
    }

    /**
     * Get creationDate.
     *
     * @return value of creationDate
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Set creationDate.
     *
     * @param creationDate as java.util.Date
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Get correctionDate.
     *
     * @return value of correctionDate
     */
    public Date getCorrectionDate() {
        return correctionDate;
    }

    /**
     * Set correctionDate.
     *
     * @param correctionDate as java.util.Date
     */
    public void setCorrectionDate(Date correctionDate) {
        this.correctionDate = correctionDate;
    }

    /**
     * Get author.
     *
     * @return value of author
     */
    public User getAuthor() {
        return author;
    }

    /**
     * Set author.
     *
     * @param author as org.kitodo.data.database.beans.User
     */
    public void setAuthor(User author) {
        this.author = author;
    }

    /**
     * Get currentTask.
     *
     * @return value of currentTask
     */
    public Task getCurrentTask() {
        return currentTask;
    }

    /**
     * Set currentTask.
     *
      * @param currentTask as org.kitodo.data.database.beans.Task
     */
    public void setCurrentTask(Task currentTask) {
        this.currentTask = currentTask;
    }

    /**
     * Get correctionTask.
     *
     * @return value of correctionTask
     */
    public Task getCorrectionTask() {
        return correctionTask;
    }

    /**
     * Set correctionTask.
     *
     * @param correctionTask as org.kitodo.data.database.beans.Task
     */
    public void setCorrectionTask(Task correctionTask) {
        this.correctionTask = correctionTask;
    }

    /**
     * Get process.
     *
     * @return value of process
     */
    public Process getProcess() {
        return process;
    }

    /**
     * Set process.
     *
     * @param process as org.kitodo.data.database.beans.Process
     */
    public void setProcess(Process process) {
        this.process = process;
    }
}
