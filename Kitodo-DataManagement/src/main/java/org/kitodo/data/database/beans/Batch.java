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
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import org.kitodo.data.database.enums.BatchType;
import org.kitodo.data.database.persistence.BatchDAO;

/**
 * A user-definable, unordered collection of processes whose batch-type tasks
 * can be taken over and completed with a single operator action. This depicts
 * taking over and completing tasks when the tasks of multiple processes refer
 * to the same physical object, for example, a multi-content box or a
 * multi-journal binding unit.
 */
@Entity
@Table(name = "batch")
public class Batch extends BaseBean {

    /**
     * The batch title. Using titles for batches is optional, the field may be
     * {@code null}. If so, the ID will be shown to the user instead.
     */
    @Column(name = "title")
    private String title;

    /**
     * The field type holds the batch type.
     */
    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private BatchType type;

    /**
     * Holds the processes that belong to the batch.
     */
    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(name = "batch_x_process", joinColumns = {
        @JoinColumn(name = "batch_id", foreignKey = @ForeignKey(name = "FK_batch_x_process_batch_id")) }, inverseJoinColumns = {
            @JoinColumn(name = "process_id", foreignKey = @ForeignKey(name = "FK_batch_x_process_process_id")) })
    private List<Process> processes;

    /**
     * Creates an empty batch.
     */
    public Batch() {
        this.processes = new ArrayList<>();
    }

    /**
     * Creates an empty batch with a given title.
     *
     * @param title
     *            title of the batch
     */
    public Batch(String title) {
        this.processes = new ArrayList<>();
        this.title = title;
    }

    /**
     * Creates a batch that holds the given processes.
     *
     * @param processes
     *            processes in the batch
     */
    public Batch(Collection<? extends Process> processes) {
        this.processes = new ArrayList<>(processes);
    }

    /**
     * Creates a batch with a given title that holds the given processes.
     *
     * @param title
     *            title of the batch
     * @param processes
     *            processes in the batch
     */
    public Batch(String title, Collection<? extends Process> processes) {
        this.title = title;
        this.processes = new ArrayList<>(processes);
    }

    /**
     * Returns the title of the batch. Using titles for batches is optional, the
     * field may be {@code null}. If so, the function returns null.
     *
     * @return the title of the batch
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gives the batch a text-based title.
     *
     * @param title
     *            title to use
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the batch type.
     *
     * @return the batch type
     */
    public BatchType getType() {
        return type;
    }

    /**
     * Returns the processes belonging to the batch. This list is not guaranteed
     * to be in reliable order.
     *
     * @return the processes belonging to the batch
     */
    public List<Process> getProcesses() {
        initialize(new BatchDAO(), this.processes);
        if (Objects.isNull(this.processes)) {
            this.processes = new ArrayList<>();
        }
        return this.processes;
    }

    /**
     * Sets the list of processes belonging to the batch. The list should not
     * contain duplicates, and must not contain {@code null}s.
     *
     * @param processes
     *            contain the list of processes belonging to the batch to be
     *            determined
     */
    public void setProcesses(List<Process> processes) {
        if (this.processes == null) {
            this.processes = processes;
        } else {
            this.processes.clear();
            this.processes.addAll(processes);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Batch) {
            Batch batch = (Batch) object;
            return Objects.equals(this.getId(), batch.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getId();
    }

    @Override
    public String toString() {
        return Objects.isNull(title) ? "Batch ".concat(Integer.toString(getId())) : title;
    }
}
