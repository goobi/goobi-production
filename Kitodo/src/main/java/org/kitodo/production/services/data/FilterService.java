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

import static java.lang.Character.charCount;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.BaseBean;
import org.kitodo.data.database.beans.Filter;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.FilterDAO;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.services.ServiceManager;
import org.primefaces.model.SortOrder;

/**
 * Service for Filter bean.
 */
public class FilterService extends BaseBeanService<Filter, FilterDAO> {
    private static final Logger logger = LogManager.getLogger(FilterService.class);

    private static final Pattern ID_SEARCH_PATTERN = Pattern.compile("\\s*(\\d+)\\s*(?:-\\s*(\\d+)\\s)?");
    public static final String FILTER_STRING = "filterString";
    private static final String NOT_SEARCH_PREFIX = "-";

    private static volatile FilterService instance = null;

    /**
     * Constructor with Searcher and Indexer assigning.
     */
    private FilterService() {
        super(new FilterDAO());
    }

    /**
     * Return singleton variable of type FilterService.
     *
     * @return unique instance of FilterService
     */
    public static FilterService getInstance() {
        FilterService localReference = instance;
        if (Objects.isNull(localReference)) {
            synchronized (FilterService.class) {
                localReference = instance;
                if (Objects.isNull(localReference)) {
                    localReference = new FilterService();
                    instance = localReference;
                }
            }
        }
        return localReference;
    }

    @Override
    public Long count() throws DAOException {
        return count("SELECT COUNT(*) FROM Filter");
    }

    /**
     * Find filters with exact value.
     *
     * @param value
     *            of the searched filter
     * @param contains
     *            of the searched filter
     * @return list of JSON objects with properties
     */
    @Deprecated
    List<Map<String, Object>> findByValue(String value, boolean contains) throws DataException {
        // TODO delete method stub
        throw new UnsupportedOperationException("no longer used function");
    }

    Set<Integer> collectIds(List<BaseBean> dtos) {
        Set<Integer> ids = new HashSet<>();
        for (BaseBean process : dtos) {
            ids.add(process.getId());
        }
        return ids;
    }

    /**
     * Prepare list of single filters from given one long filter. Filters are
     * delimited by ".
     *
     * @param filter
     *            as String
     * @return list of single filters
     */
    private List<String> splitFilters(String filter) {
        List<String> filters = new ArrayList<>();
        String delimiter = "\"";
        StringTokenizer tokenizer = new StringTokenizer(filter, delimiter, true);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (!token.equals(delimiter) && !token.equals(" ")) {
                filters.add(token);
            }
        }
        return filters;
    }

    /**
     * Checks the given map for an entry with the key 'FILTER_STRING' and parses the corresponding value of the
     * entry as a filter.
     *
     * @param filters
     *      a Map containing a filter String
     * @return
     *      the only Entry's value as java.lang.String
     */
    String parseFilterString(Map<?, ?> filters) {
        if (Objects.nonNull(filters) && filters.containsKey(FILTER_STRING)
                && filters.get(FILTER_STRING) instanceof String) {
            return (String) filters.get(FILTER_STRING);
        }
        return "";
    }

    /**
     * Parse Map 'filters' and create a map containing filter fields as
     * keys and filter values as values.
     *
     * @param filters Map containing filterString to parse
     * @return HashMap containing filter fields as keys and filter values as values
     */
    public HashMap<String, Object> getSQLFilterMap(Map<?, ?> filters, Class<?> baseClass) throws NoSuchFieldException {
        HashMap<String, Object> filterMap = new HashMap<>();
        List<String> declaredFields = Arrays.stream(baseClass.getDeclaredFields()).map(Field::getName)
                .collect(Collectors.toList());
        for (String filter : splitFilters(parseFilterString(filters))) {
            String[] filterComponents = filter.split(":");
            if (filterComponents.length == 2) {
                String parameterName = filterComponents[0].trim();
                String parameterValue = filterComponents[1].trim();
                if (declaredFields.contains(parameterName)) {
                    // class contains parameter as column
                    filterMap.put(parameterName, parseFilterValue(baseClass.getDeclaredField(parameterName).getType(),
                            parameterValue));
                } else {
                    // otherwise check if parent class contains parameter as column
                    if (Objects.nonNull(baseClass.getSuperclass())) {
                        filterMap.put(parameterName, parseFilterValue(baseClass.getSuperclass()
                                .getDeclaredField(parameterName).getType(), parameterValue));
                    }
                }
            }
        }
        return filterMap;
    }

    private Object parseFilterValue(Class<?> clazz, String parameterValue) {
        if (Objects.equals(clazz.getSuperclass(), Number.class)) {
            return Integer.parseInt(parameterValue);
        } else if (Objects.equals(clazz, boolean.class)) {
            return Boolean.parseBoolean(parameterValue);
        } else {
            return parameterValue;
        }
    }

    /**
     * Create and return an SQL statement to filter users.
     *
     * @param filters
     *          as Set of filter Strings
     * @return SQL statement to filter users
     */
    public String mapToSQLFilterString(Set<String> filters) {
        StringBuilder sqlUserFilter = new StringBuilder();
        for (String filter : filters) {
            sqlUserFilter.append(" AND ").append(filter).append(" = :").append(filter);
        }
        return sqlUserFilter.toString();
    }

    /**
     * Initialise list of process property titles.
     *
     * @return List of String objects containing the process property labels.
     */
    public List<String> initProcessPropertyTitles() {
        return ServiceManager.getPropertyService().findDistinctTitles();
    }

    /**
     * Initialise list of projects.
     *
     * @return List of String objects containing the project
     */
    public List<String> initProjects() {
        List<Project> projectsSortedByTitle = Collections.emptyList();
        try {
            projectsSortedByTitle = ServiceManager.getProjectService().findAllProjectsForCurrentUser();
        } catch (DAOException e) {
            Helper.setErrorMessage("errorInitializingProjects", logger, e);
        }

        return projectsSortedByTitle.stream().map(Project::getTitle).sorted().collect(Collectors.toList());
    }

    /**
     * Initialise list of step statuses.
     *
     * @return List of TaskStatus objects
     */
    public List<TaskStatus> initStepStatus() {
        return List.of(TaskStatus.values());
    }

    /**
     * Initialise list of task titles.
     *
     * @return List of String objects containing the titles of all workflow steps
     */
    public List<String> initStepTitles() {
        List<String> taskTitles = new ArrayList<>();
        try {
            taskTitles = ServiceManager.getTaskService().findTaskTitlesDistinct();
        } catch (DAOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
        return taskTitles;
    }


    /**
     * Initialise list of users.
     *
     * @return List of User objects
     */
    public List<User> initUserList() {
        try {
            return ServiceManager.getUserService().getAllActiveUsersSortedByNameAndSurname();
        } catch (RuntimeException e) {
            logger.warn("RuntimeException caught. List of users could be empty!");
            Helper.setErrorMessage("errorLoadingMany", new Object[] {Helper.getTranslation("activeUsers") }, logger, e);
        }
        return new ArrayList<>();
    }

    /**
     * Parses a filter string of the user search query. The resulting map
     * consists of user filters grouped by filter fields. If there are multiple
     * search queries for the same field, these are to be ORed. One user filter
     * can contain multiple search terms, which are ANDed in the index, or
     * searched in whole in the database.
     * 
     * @param filterString
     *            user input in the filter box
     * @param indexed
     *            whether the object class is indexed. Then, if possible, index
     *            queries are created. {@code true} for {@link Process}es, else
     *            {@code false}
     * @return map of search on filter field
     */
    public Map<FilterField, Collection<UserSpecifiedFilter>> parse(String filterString, boolean indexed) {
        var filters = new EnumMap<FilterField, Collection<UserSpecifiedFilter>>(FilterField.class);
        for (UserSpecifiedFilter filter : parseFilters(filterString, indexed)) {
            filters.computeIfAbsent(filter.getFilterField(), missingFilter -> new ArrayList<>()).add(filter);
        }
        return filters;
    }

    /**
     * Detects whether there are groups enclosed in quotation marks. A closing
     * quotation mark at the end may also be missing. Outside of quotation
     * marks, spaces are a token separator, inside they are not.
     * 
     * @param filter
     *            user-entered filter
     * @param indexed
     *            whether the object class is indexed
     * @return search token
     */
    static List<UserSpecifiedFilter> parseFilters(String filter, boolean indexed) {
        List<UserSpecifiedFilter> queryTokens = new ArrayList<>();
        StringBuilder tokenCollector = new StringBuilder();
        boolean inQuotes = false;
        for (int offset = 0; offset < filter.length(); offset += charCount(filter.codePointAt(offset))) {
            int glyph = filter.codePointAt(offset);
            if (glyph == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && glyph <= ' ') {
                if (tokenCollector.length() > 0) {
                    queryTokens.addAll(parseParentheses(tokenCollector, indexed));
                    tokenCollector = new StringBuilder();
                }
            } else {
                // add characters, but no spaces at the beginning
                if (tokenCollector.length() > 0 || glyph > ' ') {
                    tokenCollector.appendCodePoint(glyph);
                }
            }
        }
        trimRight(tokenCollector);
        if (tokenCollector.length() > 0) {
            UserSpecifiedFilter userSpecifiedFilter = parseQueryPart(tokenCollector.toString(), indexed);
            if (Objects.nonNull(userSpecifiedFilter)) {
                queryTokens.add(userSpecifiedFilter);
            }
        }
        logger.debug("`{}´ -> {}", filter, queryTokens);
        return queryTokens;
    }

    /**
     * Within a sequence marked with quotation marks, there can be inner groups
     * marked with parentheses. Within the sequence delimited by quotation
     * marks, a vertical bar is a separator, but within the parentheses, it is
     * not.
     * 
     * @param input
     *            a group that was marked with quotation marks
     * @param indexed
     *            whether the object class is indexed
     * @return search token
     */
    private static List<UserSpecifiedFilter> parseParentheses(StringBuilder input, boolean indexed) {
        List<UserSpecifiedFilter> queryTokens = new ArrayList<>();
        StringBuilder tokenCollector = new StringBuilder();
        boolean inParentheses = false;
        for (int offset = 0; offset < input.length(); offset += charCount(input.codePointAt(offset))) {
            int glyph = input.codePointAt(offset);
            if (glyph == '(' && !inParentheses) {
                inParentheses = !inParentheses;
            } else if (glyph == ')' && inParentheses) {
                inParentheses = !inParentheses;
            } else if (!inParentheses && glyph == '|') {
                if (tokenCollector.length() > 0) {
                    trimRight(tokenCollector);
                    queryTokens.add(parseQueryPart(tokenCollector.toString(), indexed));
                    tokenCollector = new StringBuilder();
                }
            } else {
                // no spaces at the beginning
                if (tokenCollector.length() > 0 || glyph > ' ') {
                    tokenCollector.appendCodePoint(glyph);
                }
            }
        }
        if (tokenCollector.length() > 0) {
            trimRight(tokenCollector);
            queryTokens.add(parseQueryPart(tokenCollector.toString(), indexed));
        }
        return queryTokens;
    }

    /**
     * Everything that belongs together according to the above rules is now
     * processed as one search token.
     * 
     * <p>
     * Search syntax: A preceding minus sign indicates {@code not}-search. The
     * search query can be either be words, or a fielded search. A search field
     * is preceded by its name and a colon. There are index search fields for
     * metadata, which are searched for using pseudowords in the index, and
     * there are program search fields, which can be on the index or in the
     * database. A special feature is that the program search fields also allow
     * the ID selection of an ID or an ID range.
     * 
     * @param item
     *            item to search
     * @param indexed
     *            whether the object class is indexed
     * @return filter for search item, or {@code null} if it doesn’t make sense
     */
    private static UserSpecifiedFilter parseQueryPart(String item, boolean indexed) {
        boolean substract = item.startsWith(NOT_SEARCH_PREFIX);
        if (substract) {
            item = item.substring(1, item.length());
        }
        boolean operand = !substract;

        int colon = item.indexOf(":");
        if (colon < 0) {
            return new IndexQueryPart(FilterField.SEARCH, item.toString(), operand);
        }
        String fieldName = item.substring(0, colon).toLowerCase();
        String value = item.substring(colon + 1);
        FilterField filterField = FilterField.ofString(fieldName);
        if (Objects.isNull(filterField)) {
            return new IndexQueryPart(fieldName, FilterField.SEARCH, value, operand);
        }

        Matcher idSearch = ID_SEARCH_PATTERN.matcher(value);
        if (idSearch.matches()) {
            return new DatabaseIdQueryPart(filterField, idSearch.group(1), idSearch.group(2), operand);
        }

        if (indexed && Objects.nonNull(filterField.getSearchField())) {
            return new IndexQueryPart(filterField, value, operand);
        } else {
            return new DatabaseQueryPart(filterField, value, operand);
        }

    }

    /**
     * Removes tailing spaces in a string builder.
     * 
     * @param stringBuilder
     *            to modified string builder
     */
    private static void trimRight(StringBuilder stringBuilder) {
        // remove spaces at the end
        int lastPos = stringBuilder.length() - 1;
        do {
            if (lastPos < 0 || stringBuilder.charAt(lastPos) > ' ') {
                break;
            }
            stringBuilder.setLength(lastPos);
            lastPos--;
        } while (lastPos >= 0);
    }
}
