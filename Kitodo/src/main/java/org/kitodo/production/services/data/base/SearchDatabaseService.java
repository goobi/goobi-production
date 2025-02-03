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

package org.kitodo.production.services.data.base;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.kitodo.data.database.beans.BaseBean;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.BaseDAO;
import org.kitodo.data.exceptions.DataException;
import org.primefaces.model.SortOrder;

public abstract class SearchDatabaseService<T extends BaseBean, S extends BaseDAO<T>> {

    protected S dao;

    /**
     * Constructor necessary to use searcher in child classes.
     *
     * @param dao
     *            for executing queries
     */
    public SearchDatabaseService(S dao) {
        this.dao = dao;
    }

    /**
     * Load data for frontend lists. Data can be loaded from database or index.
     * 
     * @param first
     *            searched objects
     * @param pageSize
     *            size of page
     * @param sortField
     *            field by which data should be sorted
     * @param sortOrder
     *            order ascending or descending
     * @param filters
     *            for search query
     *
     * @return loaded data
     */
    public abstract List loadData(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters)
            throws DataException;

    /**
     * Method saves object to database.
     *
     * @param baseIndexedBean
     *            object
     */
    public void saveToDatabase(T baseIndexedBean) throws DAOException {
        dao.save(baseIndexedBean);
    }

    /**
     * Method saves objects to database.
     *
     * @param baseIndexedBeans
     *            beans object to store as indexed
     */
    public void saveAsIndexed(List<T> baseIndexedBeans) throws DAOException {
        dao.saveAsIndexed(baseIndexedBeans);
    }

    /**
     * Method removes object from database.
     *
     * @param baseIndexedBean
     *            object
     */
    public void removeFromDatabase(T baseIndexedBean) throws DAOException {
        dao.remove(baseIndexedBean);
    }

    /**
     * Method removes object from database by given id.
     *
     * @param id
     *            of object
     */
    public void removeFromDatabase(Integer id) throws DAOException {
        dao.remove(id);
    }

    /**
     * Count all rows in database.
     *
     * @return amount of all rows
     */
    public abstract Long countDatabaseRows() throws DAOException;

    /**
     * Count rows in database according to given query.
     *
     * @param query
     *            for database search
     * @return amount of rows in database according to given query
     */
    public Long countDatabaseRows(String query) throws DAOException {
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
    public Long countDatabaseRows(String query, Map<String, Object> parameters) throws DAOException {
        return dao.count(query, parameters);
    }

    /**
     * This function is used for count amount of results for frontend lists.
     *
     * @param filters
     *            Map of parameters used for filtering
     * @return amount of results
     * @throws DAOException
     *             that can be caused by Hibernate
     * @throws DataException
     *             that can be caused by ElasticSearch
     */
    public abstract Long countResults(Map filters) throws DAOException, DataException;

    /**
     * Method necessary for get from database object by id. It is used in removeById
     * method.
     *
     * @param id
     *            of object
     * @return object
     */
    public T getById(Integer id) throws DAOException {
        return dao.getById(id);
    }

    /**
     * Method necessary for conversion of JSON objects to exact bean objects called
     * from database.
     *
     * @param query
     *            as String
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

    /**
     * Get list of all objects from database.
     *
     * @return list of all objects from database
     */
    public List<T> getAll() throws DAOException {
        return dao.getAll();
    }

    /**
     * Get list of all objects from database in given range.
     *
     * @param offset
     *            result - important, numeration starts since 0
     * @param size
     *            amount of results
     * @return list of all objects from database in given range
     */
    public List<T> getAll(int offset, int size) throws DAOException {
        return dao.getAll(offset, size);
    }

    /**
     * Executes an HQL query that returns scalar projections (e.g., specific fields or aggregate results)
     * instead of full entity objects.
     *
     * @param hql the HQL query string
     * @return list of scalar projection results
     */
    protected List<Object[]> getProjectionByQuery(String hql) {
        return dao.getProjectionByQuery(hql, null);
    }

    /**
     * Evict given bean object.
     *
     * @param baseBean
     *            bean to evict
     */
    public void evict(T baseBean) {
        this.dao.evict(baseBean);
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
