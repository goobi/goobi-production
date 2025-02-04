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

package org.kitodo.production.model.bibliography.course;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.model.bibliography.course.metadata.CountableMetadata;

/**
 * The class Block is a bean class that represents an interval of time in the
 * course of appearance of a newspaper within which it wasn’t suspended. A Block
 * instance handles one or more Issue objects.
 */
public class Block {
    /**
     * The field course holds a reference to the course this block is in.
     */
    private final Course course;

    /**
     * The field variant may hold a variant identifier that can be used to
     * distinguish different blocks during the buildup of a course of appearance
     * from individual issues.
     *
     * <p>
     * Given a newspaper appeared three times a week for a period of time, and
     * then changed to being published six times a week without changing its
     * heading, and this change shall be represented by different blocks, the
     * variant identifier can be used to distinguish the blocks. Otherwise, both
     * time ranges would be represented in one combined block, what would be
     * factual correct but would result in a multitude of exceptions, which
     * could be undesired.
     */
    private final String variant;

    /**
     * The field firstAppearance holds the date representing the first day of
     * the period of time represented by this block. The date is treated as
     * inclusive.
     */
    private LocalDate firstAppearance;

    /**
     * The field lastAppearance holds the date representing the last day of the
     * period of time represented by this block. The date is treated as
     * inclusive.
     */
    private LocalDate lastAppearance;

    /**
     * The field issues holds the issues that have appeared during the period of
     * time represented by this block.
     */
    private List<Issue> issues;

    /**
     * Metadata associated with this block.
     */
    private final List<CountableMetadata> metadata = new ArrayList<>();

    /**
     * Default constructor. Creates a Block object without any data.
     *
     * @param course
     *            course this block is in
     */
    public Block(Course course) {
        this.course = course;
        this.variant = null;
        this.firstAppearance = null;
        this.lastAppearance = null;
        this.issues = new ArrayList<>();
    }

    /**
     * Constructor for a block with a given variant identifier.
     *
     * @param course
     *            course this block is in
     * @param variant
     *            a variant identifier (may be null)
     */
    public Block(Course course, String variant) {
        this.course = course;
        this.variant = variant;
        this.firstAppearance = null;
        this.lastAppearance = null;
        this.issues = new ArrayList<>();
    }

    /**
     * Adds an Issue to this block if it is not already
     * present.
     *
     * @param issue
     *            Issue to add
     * @return true if the set was changed
     */
    public boolean addIssue(Issue issue) {
        clearProcessesIfNecessary(issue);
        return issues.add(issue);
    }

    /**
     * Add a new issue to this block.
     */
    public Issue addIssue() {
        Issue issue = new Issue(course);
        addIssue(issue);
        return issue;
    }

    /**
     * Adds a metadata entry to this block.
     *
     * @param countableMetadata
     *            metadata to add
     */
    public void addMetadata(CountableMetadata countableMetadata) {
        metadata.add(0, countableMetadata);
    }

    /**
     * Adds a metadata entry to this block.
     *
     * @param index
     *            insert position
     * @param countableMetadata
     *            metadata to add
     */
    public void addMetadata(CountableMetadata index, CountableMetadata countableMetadata) {
        metadata.add(metadata.indexOf(index) + 1, countableMetadata);
    }

    /**
     * When a course of appearance has been loaded from a file or the processes
     * list has already been generated, it already contains issues which must be
     * deleted in the case that an issue is added to or removed from the course
     * of appearance which is producing issues in the selected time range. If
     * the time range cannot be evaluated because either of the variables is
     * null, we go the safe way and delete, too.
     *
     * @param issue
     *            issue to add or delete
     */
    private void clearProcessesIfNecessary(Issue issue) {
        try {
            if (issue.countIndividualIssues(firstAppearance, lastAppearance) > 0) {
                course.clearProcesses();
            }
        } catch (RuntimeException e) {
            // if firstAppearance or lastAppearance is null
            course.clearProcesses();
        }
    }

    /**
     * Creates and returns a copy of this Block.
     *
     * @param course
     *            Course this block belongs to
     * @return a copy of this
     */
    public Block clone(Course course) {
        Block copy = new Block(course);
        copy.firstAppearance = firstAppearance;
        copy.lastAppearance = lastAppearance;
        ArrayList<Issue> copiedIssues = new ArrayList<>(Math.max(issues.size(), 10));
        for (Issue issue : issues) {
            copiedIssues.add(issue.clone(course));
        }
        copy.issues = copiedIssues;
        return copy;
    }

    /**
     * Determines how many stampings of issues physically appeared without
     * generating a list of IndividualIssue objects.
     *
     * @return the count of issues
     */
    public long countIndividualIssues() {
        if (Objects.isNull(firstAppearance) || Objects.isNull(lastAppearance)) {
            return 0;
        }
        long numberOfIndividualIssues = 0;
        for (LocalDate day = firstAppearance; !day.isAfter(lastAppearance); day = day.plusDays(1)) {
            for (Issue issue : getIssues()) {
                if (issue.isMatch(day)) {
                    numberOfIndividualIssues += 1;
                }
            }
        }
        return numberOfIndividualIssues;
    }

    /**
     * Deletes a metadata entry from this block.
     *
     * @param metadata
     *            entry to remove
     */
    public void deleteMetadata(CountableMetadata metadata) {
        this.metadata.remove(metadata);
    }

    /**
     * Returns the list of issues contained in this block.
     *
     * @return the list of issues from this Block
     */
    public List<Issue> getIssues() {
        return new ArrayList<>(issues);
    }

    /**
     * Generates a list of {@code IndividualIssue} objects for a given day, each
     * of them representing a stamping of one physically appeared issue.
     *
     * @param date
     *            date to generate issues for
     * @return a List of IndividualIssue objects, each of them representing one
     *         physically appeared issue
     */
    public List<IndividualIssue> getIndividualIssues(LocalDate date) {
        if (!isMatch(date)) {
            return Collections.emptyList();
        }
        ArrayList<IndividualIssue> result = new ArrayList<>(issues.size());

        List<Issue> issues = new ArrayList<>();
        for (Issue issue : getIssues()) {
            if (issue.isMatch(date)) {
                issues.add(issue);
            }
        }
        Integer sorting = issues.size() > 1 ? 1 : null;
        for (Issue issue : issues) {
            result.add(new IndividualIssue(this, issue, date, Objects.isNull(sorting) ? null : sorting++));
        }
        return result;
    }

    /**
     * Returns an issue from the Block by the issue’s
     * heading, or null if the block doesn’t contain an issue with that heading.
     *
     * @param heading
     *            Heading of the issue to look for
     * @return Issue with that heading
     */
    public Issue getIssue(String heading) {
        for (Issue issue : issues) {
            if (heading.equals(issue.getHeading())) {
                return issue;
            }
        }
        return null;
    }

    /**
     * Returns the date the regularity of this
     * block begins with.
     *
     * @return the date of first appearance
     */
    public LocalDate getFirstAppearance() {
        return firstAppearance;
    }

    /**
     * Get the date where this block first appeared.
     * PrimeFaces 7 requires a java.util.Date object for the datePicker components.
     *
     * @return date of first appearance as java.util.Date
     */
    public Date getFirstAppearanceDate() {
        if (Objects.nonNull(firstAppearance)) {
            return Date.from(firstAppearance.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        } else {
            return null;
        }
    }

    /**
     * Returns the index of the issue.
     *
     * @param issue
     *            issue whose index is to be returned
     * @return the index of the issue
     */
    public int getIssueIndex(Issue issue) {
        return issues.indexOf(issue);
    }

    /**
     * Returns the date the regularity of this block ends with.
     *
     * @return the date of last appearance
     */
    public LocalDate getLastAppearance() {
        return lastAppearance;
    }

    /**
     * Get the date where this block last appeared.
     * PrimeFaces 7 requires a java.util.Date object for the datePicker components.
     *
     * @return date of last appearance as java.util.Date
     */

    public Date getLastAppearanceDate() {
        if (Objects.nonNull(lastAppearance)) {
            return Date.from(lastAppearance.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        } else {
            return null;
        }
    }

    /**
     * Returns the metadata assigned to this block.
     *
     * @return the metadata
     */
    public Collection<CountableMetadata> getMetadata() {
        return metadata;
    }

    /**
     * Returns all metadata counters from this block for the given metadataType
     * that starts on the given day.
     *
     * @param metadataType
     *            metadataType to compare
     * @param issue
     *            creation point to compare
     * @param create
     *            if the metadata was created (else deleted)
     * @return true, if there is such a counter
     */
    public CountableMetadata getMetadata(String metadataType, Pair<LocalDate, Issue> issue, boolean create) {
        for (CountableMetadata metaDatum : metadata) {
            if (metaDatum.matches(metadataType, issue, create)) {
                return metaDatum;
            }
        }
        return null;
    }

    /**
     * Returns true, if there is a counter in this block for the given
     * metadataType that starts on the given day.
     *
     * @param issue
     *            creation point to compare
     * @param create
     *            if the metadata was created (else deleted)
     * @return true, if there is such a counter
     */
    public Iterable<CountableMetadata> getMetadata(Pair<LocalDate, Issue> issue, Boolean create) {
        List<CountableMetadata> result = new ArrayList<>();
        for (CountableMetadata metaDatum : metadata) {
            if (metaDatum.matches(null, issue, create)) {
                result.add(metaDatum);
            }
        }
        return result;
    }

    /**
     * Returns whether the block is in an empty state or not.
     *
     * @return whether the block is dataless
     */
    public boolean isEmpty() {
        return Objects.isNull(firstAppearance) && Objects.isNull(lastAppearance) && (Objects.isNull(issues) || issues.isEmpty());
    }

    /**
     * Can be used to find out whether the given variant string equals the
     * variant assigned to this block in a NullPointerException-safe way.
     *
     * @param variant
     *            variant to compare against
     * @return whether the given string is equals to the assigned variant
     */
    public boolean isIdentifiedBy(String variant) {
        return Objects.isNull(variant) && Objects.isNull(this.variant)
                || Objects.nonNull(this.variant) && this.variant.equals(variant);
    }

    /**
     * Returns whether a given LocalDate comes within the
     * limits of this block. Defaults to false if either the argument or one of
     * the fields to compare against is null.
     *
     * @param date
     *            a LocalDate to examine
     * @return whether the date is within the limits of this block
     */
    public boolean isMatch(LocalDate date) {
        if (Objects.isNull(firstAppearance) || Objects.isNull(lastAppearance)) {
            return false;
        }
        try {
            return !date.isBefore(firstAppearance) && !date.isAfter(lastAppearance);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Recalculates for each Issue
     * the daysOfWeek of its regular appearance within the interval of time of
     * the Block. This is especially sensible to detect the underlying
     * regularity after lots of issues whose existence is known have been added
     * one by one as additions to the underlying issue(s).
     */
    public void recalculateRegularityOfIssues() {
        for (Issue issue : issues) {
            issue.recalculateRegularity(firstAppearance, lastAppearance);
        }
    }

    /**
     * Removes the specified Issue from this Block if
     * it is present.
     *
     * @param issue
     *            Issue to be removed from the set
     */
    public void removeIssue(Issue issue) {
        clearProcessesIfNecessary(issue);
        issues.remove(issue);
    }

    /**
     * Check if block has issues with same heading.
     * @return 'true' if duplicates are found anf 'false' if not.
     */
    public boolean checkIssuesWithSameHeading() {
        List<String> issuesTitles = issues.stream().map(Issue::getHeading).collect(Collectors.toList());
        List<String> titles = new ArrayList<>();
        for (String title : issuesTitles) {
            if (titles.contains(title)) {
                Helper.setErrorMessage("duplicatedTitles", " (Block " + (course.indexOf(this) + 1) + ")" );
                return true;
            } else {
                titles.add(title);
            }
        }
        return false;
    }

    /**
     * Sets a LocalDate as day of first
     * appearance for this Block.
     *
     * @param firstAppearance
     *            date of first appearance
     * @throws IllegalArgumentException
     *             if the date would overlap with another block
     */
    public void setFirstAppearance(LocalDate firstAppearance) {
        try {
            prohibitOverlaps(firstAppearance, Objects.nonNull(lastAppearance) ? lastAppearance : firstAppearance);
        } catch (IllegalArgumentException e) {
            Helper.setErrorMessage(e.getMessage());
        }
        try {
            if (!this.firstAppearance.equals(firstAppearance)) {
                course.clearProcesses();
            }
        } catch (NullPointerException e) {
            if (this.firstAppearance == null ^ firstAppearance == null) {
                course.clearProcesses();
            }
        }
        this.firstAppearance = firstAppearance;
        if (Objects.isNull(lastAppearance)) {
            lastAppearance = firstAppearance;
        }
    }

    /**
     * Set the date where this block first appeared.
     * PrimeFaces 7 passes a java.util.Date object from the datePicker components.
     *
     * @param firstAppearance the first date of appearance as java.util.Date
     */
    public void setFirstAppearanceDate(Date firstAppearance) {
        if (Objects.nonNull(firstAppearance)) {
            firstAppearance.setHours(5);
            setFirstAppearance(firstAppearance.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
    }

    /**
     * Sets a LocalDate as day of last appearance
     * for this Block.
     *
     * @param lastAppearance
     *            date of last appearance
     * @throws IllegalArgumentException
     *             if the date would overlap with another block
     */
    public void setLastAppearance(LocalDate lastAppearance) {
        try {
            prohibitOverlaps(Objects.nonNull(firstAppearance) ? firstAppearance : lastAppearance, lastAppearance);
        } catch (IllegalArgumentException e) {
            Helper.setErrorMessage(e.getMessage());
        }
        try {
            if (!this.lastAppearance.equals(lastAppearance)) {
                course.clearProcesses();
            }
        } catch (NullPointerException e) {
            if (this.lastAppearance == null ^ lastAppearance == null) {
                course.clearProcesses();
            }
        }
        this.lastAppearance = lastAppearance;
        if (Objects.isNull(firstAppearance)) {
            firstAppearance = lastAppearance;
        }
    }

    /**
     * Set the date where this block last appeared.
     * PrimeFaces 7 passes a java.util.Date object from the datePicker components.
     *
     * @param lastAppearance the last date of appearance as java.util.Date
     */
    public void setLastAppearanceDate(Date lastAppearance) {
        if (Objects.nonNull(lastAppearance)) {
            lastAppearance.setHours(5);
            setLastAppearance(lastAppearance.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
    }

    /**
     * Sets two LocalDate instances as days of
     * first and last appearance for this Block.
     *
     * @param firstAppearance
     *            date of first appearance
     * @param lastAppearance
     *            date of last appearance
     * @throws IllegalArgumentException
     *             if the date would overlap with another block
     */
    public void setPublicationPeriod(LocalDate firstAppearance, LocalDate lastAppearance) {
        prohibitOverlaps(firstAppearance, lastAppearance);
        try {
            if (!this.firstAppearance.equals(firstAppearance)) {
                course.clearProcesses();
            }
        } catch (NullPointerException e) {
            if (this.firstAppearance == null ^ firstAppearance == null) {
                course.clearProcesses();
            }
        }
        try {
            if (!this.lastAppearance.equals(lastAppearance)) {
                course.clearProcesses();
            }
        } catch (NullPointerException e) {
            if (this.lastAppearance == null ^ lastAppearance == null) {
                course.clearProcesses();
            }
        }
        this.firstAppearance = firstAppearance;
        this.lastAppearance = lastAppearance;
    }

    /**
     * Tests an not yet set time range for this
     * block whether it doesn’t overlap with other titles in this course and can
     * be set. (Because this method is called prior to setting a new value as a
     * field value, it doesn’t take the values from the classes’ fields even
     * though it isn’t static.) If the given dates would cause an overlapping,
     * an IllegalArgumentException will be thrown.
     *
     * @param from
     *            date of first appearance to check
     * @param until
     *            date of last appearance to check
     * @throws IllegalArgumentException
     *             if the check fails
     */
    private void prohibitOverlaps(LocalDate from, LocalDate until) {
        for (Block block : course) {
            if (!block.equals(this)
                    && (Objects.nonNull(from) && Objects.nonNull(until))
                    && (Objects.nonNull(block.getFirstAppearance()) && Objects.nonNull(block.getLastAppearance()))
                    && (block.getFirstAppearance().isBefore(until) && !block.getLastAppearance().isBefore(from)
                    || (block.getLastAppearance().isAfter(from) && !block.getFirstAppearance().isAfter(until)))) {
                throw new IllegalArgumentException(
                        '(' + block.variant + ") " + block.firstAppearance + " - " + block.lastAppearance);
            }
        }
    }

    /**
     * Provides returns a string that contains a concise
     * but informative representation of this block that is easy for a person to
     * read.
     *
     * @return a string representation of the block
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (Objects.nonNull(firstAppearance)) {
            result.append(firstAppearance);
        }
        result.append(" - ");
        if (Objects.nonNull(lastAppearance)) {
            result.append(lastAppearance);
        }
        result.append(" [");
        boolean first = true;
        for (Issue issue : issues) {
            if (!first) {
                result.append(", ");
            }
            result.append(issue.toString());
            first = false;
        }
        result.append("]");
        return result.toString();
    }

    /**
     * Provides returns a string that contains a textual
     * representation of this block that is easy for a person to read.
     *
     * @param dateConverter
     *            a DateTimeFormatter for formatting the local dates
     * @return a string to identify the block
     */
    public String toString(DateTimeFormatter dateConverter) {
        StringBuilder result = new StringBuilder();
        if (Objects.nonNull(firstAppearance)) {
            result.append(dateConverter.format(firstAppearance));
        }
        result.append(" − ");
        if (Objects.nonNull(lastAppearance)) {
            result.append(dateConverter.format(lastAppearance));
        }
        return result.toString();
    }

    /**
     * Returns a hash code for the object which depends on the content of its
     * variables. Whenever Block objects are held in HashSet objects, a
     * hashCode() is essentially necessary.
     *
     * <p>
     * The method was generated by Eclipse using right-click → Source → Generate
     * hashCode() and equals()…. If you will ever change the classes’ fields,
     * just re-generate it.
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;
        hashCode = prime * hashCode + ((firstAppearance == null) ? 0 : firstAppearance.hashCode());
        hashCode = prime * hashCode + ((issues == null) ? 0 : issues.hashCode());
        hashCode = prime * hashCode + ((lastAppearance == null) ? 0 : lastAppearance.hashCode());
        hashCode = prime * hashCode + ((variant == null) ? 0 : variant.hashCode());
        return hashCode;
    }

    /**
     * Returns whether two individual issues are equal; the decision depends on
     * the content of its variables.
     *
     * <p>
     * The method was generated by Eclipse using right-click → Source → Generate
     * hashCode() and equals()…. If you will ever change the classes’ fields,
     * just re-generate it.
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Block) {
            Block other = (Block) obj;

            if (Objects.isNull(firstAppearance)) {
                if (Objects.nonNull(other.firstAppearance)) {
                    return false;
                }
            } else if (!firstAppearance.equals(other.firstAppearance)) {
                return false;
            }

            if (Objects.isNull(issues)) {
                if (Objects.nonNull(other.issues)) {
                    return false;
                }
            } else if (!issues.equals(other.issues)) {
                return false;
            }

            if (Objects.isNull(lastAppearance)) {
                if (Objects.nonNull(other.lastAppearance)) {
                    return false;
                }
            } else if (!lastAppearance.equals(other.lastAppearance)) {
                return false;
            }

            if (Objects.isNull(variant)) {
                return Objects.isNull(other.variant);
            } else {
                return variant.equals(other.variant);
            }
        }
        return false;
    }
}
