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

import java.text.ParseException;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

/**
 * This bean contains properties common for Template and Process.
 */
@MappedSuperclass
public abstract class BaseTemplateBean extends BaseBean {

    @Column(name = "title")
    protected String title;

    @Column(name = "creationDate")
    protected Date creationDate;

    @Column(name = "sortHelperStatus")
    private String sortHelperStatus;

    /**
     * Returns the process or process template name.
     *
     * @return the process or process template name
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the process or process template name. Since the process name is used
     * in file paths, it should only contain characters compatible with the
     * operating file system. Also, for scripting, there should be no spaces in
     * the process name.
     *
     * @param title
     *            the process or process template name
     */
    public void setTitle(String title) {
        this.title = title.trim();
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
    public String getSortHelperStatus() {
        return this.sortHelperStatus;
    }

    /**
     * Sets the coded overview of the processing status of the process. This
     * should only be set manually if this information comes from a third-party
     * source. Typically, sorting progress is determined from the progress
     * properties of the tasks in the process. The numeric code consists of
     * twelve digits, each three digits from 000 to 100 indicate the percentage
     * of tasks completed, currently in progress, ready to start and not yet
     * ready, in that order. The sum of the four groups of numbers must be 100.
     * 
     * @param sortHelperStatus
     *            coded overview of the progress with pattern
     *            <code>([01]\d{2}){4}</code>
     */
    public void setSortHelperStatus(String sortHelperStatus) {
        this.sortHelperStatus = sortHelperStatus;
    }

    /**
     * Returns the time the process or process template was created.
     * {@link Date} is a specific instant in time, with millisecond precision.
     *
     * @return the creation time
     */
    public Date getCreationDate() {
        return this.creationDate;
    }

    /**
     * Sets the time the process or process template was created.
     *
     * @param creationDate
     *            creation time to set
     * @throws ParseException
     *             if the time cannot be converted
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Returns a string that textually represents this object.
     */
    @Override
    public String toString() {
        return title + " [" + id + ']';
    }
}
