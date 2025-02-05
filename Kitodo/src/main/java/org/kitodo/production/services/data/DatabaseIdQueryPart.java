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

package org.kitodo.production.services.data;

import java.util.Map;
import java.util.Objects;

/**
 * A part of the filter searching on the database for an ID or ID range.
 */
class DatabaseIdQueryPart extends DatabaseQueryPart {

    private static final String SECOND_PARAMETER_EXTENSION = "upTo";

    private Integer firstId;
    private Integer upToId;

    /**
     * Constructor, creates a new DatabaseQueryPart.
     * 
     * @param filterField
     *            field to search on
     * @param firstId
     *            first or only ID
     * @param upToId
     *            {@code null} or last ID
     */
    DatabaseIdQueryPart(FilterField filterField, String firstId, String upToId, boolean operand) {
        super(filterField, operand);
        this.firstId = Integer.valueOf(firstId);
        this.upToId = Objects.nonNull(upToId) ? Integer.valueOf(upToId) : null;
    }

    /**
     * Returns the part HQL query. If the query contains the keyword "IN", it
     * must be queried using JOIN, otherwise using WHERE.
     * 
     * @param className
     *            "Task" for a query on the tasks table, else for a query on the
     *            process table.
     * @param varName
     *            variable name to use in the query
     * @param parameterName
     *            parameter name to use in the query
     * @return the part HQL query
     */
    @Override
    String getDatabaseQuery(String className, String varName, String parameterName) {
        String query = Objects.equals(className, "Task") ? filterField.getTaskIdQuery()
                : filterField.getProcessIdQuery();
        return varName + '.' + query + (upToId == null ? (operand ? " = :" : " != :") + parameterName
                : (operand ? " BETWEEN :" : " NOT BETWEEN :") + parameterName + " AND :" + parameterName
                        + SECOND_PARAMETER_EXTENSION);
    }

    /**
     * Puts the parameters necessary for the query.
     * 
     * @param parameterName
     *            parameter name used in the query
     * @param parameters
     *            map to put the parameters into
     */
    @Override
    void addParameters(String parameterName, Map<String, Object> parameters) {
        if (Objects.nonNull(filterField.getQueryObject())) {
            parameters.put("queryObject", filterField.getQueryObject());
        }
        parameters.put(parameterName, firstId);
        if (Objects.nonNull(upToId)) {
            parameters.put(parameterName.concat(SECOND_PARAMETER_EXTENSION), upToId);
        }
    }

    @Override
    public String toString() {
        return filterField + (upToId == null ? (operand ? " = " : " != ") + firstId
                : (operand ? " BETWEEN " : " NOT BETWEEN ") + firstId + " AND " + upToId);
    }
}
