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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;

/**
 * A portion of the filter entered by the user that is resolved through the
 * search index.
 */
class IndexQueryPart implements UserSpecifiedFilter {

    private static final String UNIQUE_PARAMETER_EXTENSION = "query";

    private static final char VALUE_SEPARATOR = 'q';
    private List<String> lookfor = new ArrayList<>();
    private final FilterField filterField;
    private final boolean operand;

    /**
     * Constructor. Creates a new IndexQueryPart.
     * 
     * @param filterField
     *            search field selected by the user
     * @param values
     *            search terms
     * @param operand
     *            whether the search must match
     */
    IndexQueryPart(FilterField filterField, String values, boolean operand) {
        this.filterField = filterField;
        for (String value : splitValues(values)) {
            this.lookfor.add(addOptionalPseudoword(filterField.getPseudoword(), normalize(value)));
        }
        this.operand = operand;
    }

    private final String addOptionalPseudoword(String pseudoword, String value) {
        return Objects.nonNull(pseudoword) ? pseudoword + VALUE_SEPARATOR + value : value;
    }

    /**
     * Constructor. Creates a new IndexQueryPart.
     * 
     * @param key
     *            in the ruleset
     * @param filterField
     *            search field selected by the user
     * @param values
     *            search terms
     * @param operand
     *            whether the search must match
     */
    IndexQueryPart(String key, FilterField filterField, String values, boolean operand) {
        this.filterField = filterField;
        for (String value : splitValues(values)) {
            lookfor.add(normalize(key) + VALUE_SEPARATOR + normalize(value));
        }
        this.operand = operand;
    }

    private List<String> splitValues(String value) {
        String i = value != null ? value : "";
        return Arrays.asList(i.split("[ ,\\-._]+"));
    }

    private String normalize(String string) {
        return string.toLowerCase().replaceAll("[\0-/:-`{-¿]", "");
    }

    @Override
    public FilterField getFilterField() {
        return filterField;
    }

    /**
     * Inserts the search parameters into the database query logic.
     * 
     * @param varName
     *            variable name of the HQL search
     * @param parameterName
     *            name of the search parameter for the results
     * @param indexQueries
     *            puts the prepared tokens for the search queries here
     * @param restrictions
     *            puts the HQL restrictions here
     */
    void putQueryParameters(String varName, String parameterName, Map<String, Pair<FilterField, String>> indexQueries,
            Collection<String> restrictions) {
        if (lookfor.size() == 1) {
            restrictions.add(varName + (operand ? ".id IN (:" : ".id NOT IN (:") + parameterName + ')');
            indexQueries.put(parameterName, Pair.of(filterField, lookfor.get(0)));
        } else {
            int queryCount = 0;
            for (String lookingFor : lookfor) {
                queryCount++;
                String uniqueParameterName = parameterName + UNIQUE_PARAMETER_EXTENSION + queryCount;
                restrictions.add(varName + (operand ? ".id IN (:" : ".id NOT IN (:") + uniqueParameterName + ')');
                indexQueries.put(uniqueParameterName, Pair.of(filterField, lookingFor));
            }
        }
    }

    @Override
    public String toString() {
        return "~(" + filterField.getSearchField() + ')' + String.join(" ", lookfor) + "~";
    }
}
