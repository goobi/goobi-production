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

import io.reactivex.annotations.CheckReturnValue;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.kitodo.data.database.beans.BaseBean;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.BaseDAO;
import org.primefaces.model.SortOrder;

public abstract class BaseBeanService<T extends BaseBean, S extends BaseDAO<T>> {
    protected static final EnumMap<SortOrder, String> SORT_ORDER_MAPPING;

    static {
        SORT_ORDER_MAPPING = new EnumMap<>(SortOrder.class);
        SORT_ORDER_MAPPING.put(SortOrder.ASCENDING, "ASC");
        SORT_ORDER_MAPPING.put(SortOrder.DESCENDING, "DESC");
    }

    protected S dao;

    /**
     * Constructor necessary to use searcher in child classes.
     *
     * @param dao
     *            for executing queries
     */
    public BaseBeanService(S dao) {
        this.dao = dao;
    }

    /**
     * Provides a window onto the data objects of the implementing type. This
     * makes it possible to navigate through the data objects page by page,
     * without having to load <i>all</i> objects into memory.
     * 
     * <p>
     * <b>API Note:</b><br>
     * This function filters the data according to the client, for which the
     * logged-in user is currently working.
     * 
     * <p>
     * <b>Implementation Requirements:</b><br>
     * This function requires that the thread is assigned to a logged-in user.
     * 
     * <p>
     * <b>Implementation Note:</b><br>
     * For {@code SearchDatabaseServiceInterface<Template>}, this must take into
     * account the value from
     * {@link DatabaseTemplateServiceInterface#setShowInactiveTemplates(boolean)}.
     * For {@link DatabaseProcessServiceInterface} and
     * {@link DatabaseTaskServiceInterface}, this function is unused. They
     * declare their own {@code loadData()} with additional parameters.
     * 
     * @param offset
     *            number of objects to be skipped at the list head
     * @param limit
     *            maximum number of objects to return
     * @param sortField
     *            by which column the data should be sorted. Must not be
     *            {@code null} or empty.<br>
     *            One of:<br>
     *            <ul>
     *            <li>"title.keyword": Title [docket, ruleset, project, process
     *            template, and workflow]</li>
     *            <li>"file.keyword": File [docket, and ruleset]</li>
     *            <li>"metsRightsOwner.keyword": METS rights owner
     *            [project]</li>
     *            <li>"orderMetadataByRuleset": Order metadata as in ruleset
     *            (otherwise alphabetically) [only ruleset]</li>
     *            <li>"ruleset.title.keyword": Ruleset [only process
     *            template]</li>
     *            <li>"active": Active [project, process template]</li>
     *            <li>"status": Status [only workflow]</li>
     *            </ul>
     * @param sortOrder
     *            sort ascending or descending?
     * @param filters
     *            a map with exactly one entry, only the value is important, in
     *            which the content of the filter field is passed
     *
     * @return the data objects to be displayed
     * @throws DAOException
     *             if processes cannot be loaded from search index
     * @throws UnsupportedOperationException
     *             if the function is not offered by the implementing class
     */
    public List<T> loadData(int offset, int limit, String sortField, SortOrder sortOrder, Map<?, String> filters)
            throws DAOException {
        throw new UnsupportedOperationException("optional operation");
    }

    /**
     * Stores an object in the database.
     *
     * @param baseIndexedBean
     *            object to save
     */
    public void save(T baseIndexedBean) throws DAOException {
        dao.save(baseIndexedBean);
    }

    /**
     * Removes an object from the database.
     *
     * @param baseIndexedBean
     *            object to remove
     */
    public void remove(T baseIndexedBean) throws DAOException {
        dao.remove(baseIndexedBean);
    }

    /**
     * Method removes object from database by given id.
     *
     * @param id
     *            of object
     */
    public void remove(Integer id) throws DAOException {
        dao.remove(id);
    }

    /**
     * Returns the number of objects of the implementing type in the database.
     * 
     * <p>
     * <b>API Note:</b><br>
     * This function counts all data records in the database for all clients.
     *
     * @return the number of objects
     */
    public abstract Long count() throws DAOException;

    /**
     * Count rows in database according to given query.
     *
     * @param query
     *            for database search
     * @return amount of rows in database according to given query
     */
    public Long count(String query) throws DAOException {
        return dao.count(query);
    }

    /**
     * Count rows in database according to given query.
     *
     * @param query
     *            for database search
     * @param parameters
     *            for query
     * @return amount of rows in database according to given query
     */
    public Long count(String query, Map<String, Object> parameters) throws DAOException {
        return dao.count(query, parameters);
    }

    /**
     * Returns the number of objects of the implementing type that the filter
     * matches.
     * 
     * <p>
     * <b>API Note:</b><br>
     * This function counts the data records for the client, for which the
     * logged-in user is currently working.
     * 
     * <p>
     * <b>Implementation Requirements:</b><br>
     * This function requires that the thread is assigned to a logged-in user.
     * 
     * <p>
     * <b>Implementation Note:</b><br>
     * For {@code SearchDatabaseServiceInterface<Template>}, this must take into
     * account the value from
     * {@link DatabaseTemplateServiceInterface#setShowInactiveTemplates(boolean)}.
     * For {@link DatabaseProcessServiceInterface} and
     * {@link DatabaseTaskServiceInterface}, this function is unused. They
     * declare their own {@code countResults()} with additional parameters.
     * 
     * @param filters
     *            a map with exactly one entry, only the value is important, in
     *            which the content of the filter field is passed
     * @return the number of matching objects
     * @throws DAOException
     *             that can be caused by Hibernate
     * @throws UnsupportedOperationException
     *             if the function is not offered by the implementing class
     */
    public Long countResults(Map<?, String> filters) throws DAOException {
        throw new UnsupportedOperationException("optional operation");
    }

    /**
     * Gets an object by its database record number.
     *
     * @param id
     *            record number
     * @return object
     */
    public T getById(Integer id) throws DAOException {
        return dao.getById(id);
    }

    /**
     * Gets a set of objects based on a search query.
     *
     * @param query
     *            query in Hibernate Query Language
     * @return list of exact bean objects
     */
    public List<T> getByQuery(String query) {
        return dao.getByQuery(query);
    }

    /**
     * Retrieves BaseBean objects from database by given query.
     *
     * @param query
     *            as String
     * @param parameters
     *            for query
     * @return list of beans objects
     */
    public List<T> getByQuery(String query, Map<String, Object> parameters) {
        return dao.getByQuery(query, parameters);
    }

    public List<T> getByQuery(String query, Map<String, Object> parameters, int begin, int max) {
        return dao.getByQuery(query, parameters, begin, max);
    }

    /**
     * Gets a set of objects based on a search query.
     *
     * @param query
     *            query in Hibernate Query Language
     * @param parameters
     *            used in query. If the query string contains a placeholder
     *            "{@code :joker}", this mapping must contain a mapping for the
     *            string "{@code joker}" to a value for it. A replacement for
     *            injection into the SQL query, to prevent attacks.
     * @param max
     *            maximum count of beans to return
     * @return list of exact bean objects
     */
    public List<T> getByQuery(String query, Map<String, Object> parameters, int max) {
        return dao.getByQuery(query, parameters, 0, max);
    }

    /**
     * Gets a collection of strings based on a search query.
     *
     * @param query
     *            query in Hibernate Query Language
     * @param parameters
     *            used in query. If the query string contains a placeholder
     *            "{@code :joker}", this mapping must contain a mapping for the
     *            string "{@code joker}" to a value for it. A replacement for
     *            injection into the SQL query, to prevent attacks.
     * @return list of strings
     */
    public List<String> getStringList(String query, Map<String, Object> parameters) {
        return dao.getStringsByQuery(query, parameters);
    }

    /**
     * Returns all objects of the implementing type from the database.
     * 
     * <p>
     * <b>API Note:</b><br>
     * This method actually returns all objects of all clients and is therefore
     * more suitable for operational purposes, rather not for display purposes.
     * 
     * <p>
     * <b>Implementation Requirements:</b><br>
     * Use this with caution, only if the number of objects is manageable.
     *
     * @return all objects of the implementing type
     */
    public List<T> getAll() throws DAOException {
        return dao.getAll();
    }

    /**
     * Returns a range of all objects of the implementing type from the
     * database. In this way the objects can be fetched in groups.
     * 
     * <p>
     * <b>API Note:</b><br>
     * This method actually returns all objects of all clients and is therefore
     * more suitable for operational purposes, rather not for display purposes.
     *
     * @param offset
     *            number of objects to be skipped at the head
     * @param size
     *            maximum number of objects to return
     * @return objects in the given range
     */
    public List<T> getAll(int offset, int size) throws DAOException {
        return dao.getAll(offset, size);
    }

    /**
     * Unlinks the object from the primary Hibernate cache, so it can be garbage
     * collected.
     *
     * @param baseBean
     *            bean to evict
     */
    public void evict(T baseBean) {
        this.dao.evict(baseBean);
    }

    @CheckReturnValue
    public T merge(T baseBean) {
        return this.dao.merge(baseBean);
    }
    
    /**
     * Refresh given bean object.
     *
     * @param baseBean
     *            bean object
     */
    public void refresh(T baseBean) {
        this.dao.refresh(baseBean);
    }

    protected String getSort(String sortField, SortOrder sortOrder) {
        if (!Objects.equals(sortField, null) && Objects.equals(sortOrder, SortOrder.ASCENDING)) {
            return " ORDER BY " + sortField + " ASC";
        } else if (!Objects.equals(sortField, null) && Objects.equals(sortOrder, SortOrder.DESCENDING)) {
            return " ORDER BY " + sortField + " DESC";
        } else {
            return "";
        }
    }
}
