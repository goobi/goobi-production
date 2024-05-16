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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.kitodo.data.database.beans.BaseBean;
import org.primefaces.model.SortOrder;

public class BeanQuery {

    private String objectClass;
    private String varName;
    private Collection<String> restrictions = new ArrayList<>();
    private Pair<String, String> sorting = Pair.of("id", "ASC");
    private Map<String, Object> parameters = new HashMap<>();

    public BeanQuery(Class<? extends BaseBean> beanClass) {
        objectClass = beanClass.getSimpleName();
        varName = objectClass.toLowerCase();
    }

    public void restrictToClient(int sessionClientId) {
        restrictions.add(varName + ".project.client_id = :sessionClientId");
        parameters.put("sessionClientId", sessionClientId);
    }

    public void restrictWithUserFilterString(String s) {
        // not yet implemented
    }

    public void restrictToProjects(Collection<Integer> projectIDs) {
        restrictions.add(varName + ".project_id IN (:projectIDs)");
        parameters.put("projectIDs", projectIDs);
    }

    public void restrictToNotCompletedProcesses() {
        restrictions.add(varName + ".sortHelperStatus != '100000000000'");
    }

    public void defineSorting(String sortField, SortOrder sortOrder) {
        sorting = Pair.of(sortField, SortOrder.DESCENDING.equals(sortOrder) ? "DESC" : "ASC");
    }

    public String formCountQuery() {
        String query = "SELECT COUNT(*) FROM " + objectClass;
        if (!restrictions.isEmpty()) {
            query += " AS " + varName + " WHERE " + String.join(" AND ", restrictions);
        }
        return query;
    }

    public String formWindowQuery(int offset, int limit) {
        String query = "SELECT COUNT(*) FROM " + objectClass;
        if (!restrictions.isEmpty()) {
            query += " AS " + varName + " WHERE " + String.join(" AND ", restrictions);
        }
        query += " ORDER BY " + sorting.getKey() + ' ' + sorting.getValue()
                + " LIMIT " + limit + " OFFSET " + offset;
        return query;
    }

    public Map<String, Object> getQueryParameters() {
        return parameters;
    }
}
