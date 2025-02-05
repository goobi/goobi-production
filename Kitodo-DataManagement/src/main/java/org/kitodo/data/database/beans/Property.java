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
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import org.kitodo.data.database.converter.PropertyTypeConverter;
import org.kitodo.data.database.enums.PropertyType;
import org.kitodo.data.database.persistence.PropertyDAO;

@Entity
@Table(name = "property")
public class Property extends BaseBean implements Comparable<Property> {

    @Column(name = "title")
    private String title;

    @Column(name = "value", columnDefinition = "longtext")
    private String value;

    @Column(name = "obligatory")
    private Boolean obligatory;

    @Column(name = "dataType")
    @Convert(converter = PropertyTypeConverter.class)
    private PropertyType dataType;

    @Column(name = "choice")
    private String choice;

    @Column(name = "creationDate")
    private Date creationDate;

    @ManyToMany(mappedBy = "properties", cascade = CascadeType.PERSIST)
    private List<Process> processes;

    @ManyToMany(mappedBy = "templates", cascade = CascadeType.PERSIST)
    private List<Process> templates;

    @ManyToMany(mappedBy = "workpieces", cascade = CascadeType.PERSIST)
    private List<Process> workpieces;

    /**
     * Constructor.
     */
    public Property() {
        this.obligatory = false;
        this.dataType = PropertyType.STRING;
        this.creationDate = new Date();
    }

    /**
     * Returns the key of the property's key-value pair.
     *
     * @return the key
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the key of the property's key-value pair.
     *
     * @param title
     *            key to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the value of the property's key-value pair.
     *
     * @return the value
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets the value of the property's key-value pair.
     *
     * @param value
     *            value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Get choice.
     *
     * @return choice as String
     */
    public String getChoice() {
        return this.choice;
    }

    /**
     * Set choice.
     *
     * @param choice
     *            as String
     */
    public void setChoice(String choice) {
        this.choice = choice;
    }

    /**
     * Check if property is obligatory (mandatory).
     *
     * @return value from database or false
     */
    public Boolean isObligatory() {
        if (this.obligatory == null) {
            this.obligatory = false;
        }
        return this.obligatory;
    }

    /**
     * Set obligatory.
     *
     * @param obligatory
     *            as Boolean
     */
    public void setObligatory(Boolean obligatory) {
        this.obligatory = obligatory;
    }

    /**
     * Returns the creation time of the property. {@link Date} is a specific
     * instant in time, with millisecond precision.
     *
     * @return the creation time
     */
    public Date getCreationDate() {
        return this.creationDate;
    }

    /**
     * Sets the creation time of the property.
     *
     * @param creationDate
     *            creation time to set
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Get data type as {@link PropertyType}.
     *
     * @return current data type
     */
    public PropertyType getDataType() {
        if (this.dataType == null) {
            this.dataType = PropertyType.STRING;
        }
        return this.dataType;
    }

    /**
     * Set data type to specific value from {@link PropertyType}.
     *
     * @param inputType
     *            as {@link PropertyType}
     */
    public void setDataType(PropertyType inputType) {
        this.dataType = inputType;
    }

    /**
     * Get container value.
     *
     * @return value from database or 0
     */
    public List<Process> getProcesses() {
        initialize(new PropertyDAO(), this.processes);
        if (Objects.isNull(this.processes)) {
            this.processes = new ArrayList<>();
        }
        return this.processes;
    }

    /**
     * Set processes.
     *
     * @param processes
     *            as List
     */
    public void setProcesses(List<Process> processes) {
        this.processes = processes;
    }

    /**
     * Get templates list or new empty list.
     *
     * @return as list of Process objects or new empty list
     */
    public List<Process> getTemplates() {
        initialize(new PropertyDAO(), this.templates);
        if (Objects.isNull(this.templates)) {
            this.templates = new ArrayList<>();
        }
        return this.templates;
    }

    /**
     * Set templates.
     *
     * @param templates
     *            as List of Process objects
     */
    public void setTemplates(List<Process> templates) {
        this.templates = templates;
    }

    /**
     * Get workpieces list or new empty list.
     *
     * @return as list of Process objects or new empty list
     */
    public List<Process> getWorkpieces() {
        initialize(new PropertyDAO(), this.workpieces);
        if (Objects.isNull(this.workpieces)) {
            this.workpieces = new ArrayList<>();
        }
        return this.workpieces;
    }

    /**
     * Set workpieces.
     *
     * @param workpieces
     *            as List of Process objects
     */
    public void setWorkpieces(List<Process> workpieces) {
        this.workpieces = workpieces;
    }

    /**
     * Compare property to other property object.
     *
     * @param property
     *            object
     * @return int
     */
    @Override
    public int compareTo(Property property) {
        int titleMatch = this.getTitle().toLowerCase().compareTo(property.getTitle().toLowerCase());
        int valueMatch = this.getValue().toLowerCase().compareTo(property.getValue().toLowerCase());
        if (titleMatch == 0 && valueMatch == 0) {
            return 0;
        } else if (valueMatch == 0) {
            return titleMatch;
        } else {
            return valueMatch;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Property) {
            Property property = (Property) object;
            return Objects.equals(this.getId(), property.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, value, obligatory, dataType, choice, creationDate);
    }
}
