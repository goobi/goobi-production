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

package org.kitodo.data.database.persistence;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.persistence.PersistenceException;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.exception.SQLGrammarException;
import org.hibernate.query.Query;
import org.kitodo.config.ConfigMain;
import org.kitodo.data.database.beans.BaseBean;
import org.kitodo.data.database.beans.BaseIndexedBean;
import org.kitodo.data.database.enums.IndexAction;
import org.kitodo.data.database.exceptions.DAOException;

/**
 * Base class for DAOs.
 */
public abstract class BaseDAO<T extends BaseBean> implements Serializable {

    private static final Object lockObject = new Object();

    /**
     * Retrieves a BaseBean identified by the given id from the database.
     *
     * @param id
     *            of bean to load
     * @return persisted bean
     * @throws DAOException
     *             if a HibernateException is thrown
     */
    public abstract T getById(Integer id) throws DAOException;

    /**
     * Retrieves all BaseBean objects from the database.
     *
     * @return all persisted beans
     */
    public abstract List<T> getAll() throws DAOException;

    /**
     * Retrieves all BaseBean objects in given range.
     *
     * @param offset
     *            result
     * @param size
     *            amount of results
     * @return constrained list of persisted beans
     */
    public abstract List<T> getAll(int offset, int size) throws DAOException;

    /**
     * Retrieves all not indexed BaseBean objects in given range.
     *
     * @param offset
     *            result
     * @param size
     *            amount of results
     * @return constrained list of persisted beans
     */
    public abstract List<T> getAllNotIndexed(int offset, int size) throws DAOException;

    /**
     * Saves a BaseBean object to the database.
     *
     * @param baseBean
     *            object to persist
     * @throws DAOException
     *             if the current session can't be retrieved or an exception is
     *             thrown while performing the rollback
     */
    public void save(T baseBean) throws DAOException {
        storeObject(baseBean);
    }

    /**
     * Saves base bean objects as indexed.
     *
     * @param baseBeans
     *            list of base beans
     * @throws DAOException
     *             if the current session can't be retrieved or an exception is
     *             thrown while performing the rollback
     */
    public void saveAsIndexed(List<T> baseBeans) throws DAOException {
        storeAsIndexed(baseBeans);
    }

    /**
     * Removes BaseBean object specified by the given id from the database.
     *
     * @param id
     *            of bean to delete
     * @throws DAOException
     *             if the current session can't be retrieved or an exception is
     *             thrown while performing the rollback
     */
    public abstract void remove(Integer id) throws DAOException;

    /**
     * Removes given BaseBean object from the database.
     *
     * @param baseBean
     *            bean to delete
     * @throws DAOException
     *             if the current session can't be retrieved or an exception is
     *             thrown while performing the rollback
     */
    public void remove(T baseBean) throws DAOException {
        if (baseBean.getId() != null) {
            try (Session session = HibernateUtil.getSession()) {
                Transaction transaction = session.beginTransaction();
                synchronized (lockObject) {
                    Object merged = session.merge(baseBean);
                    session.delete(merged);
                    session.flush();
                    transaction.commit();
                }
            } catch (PersistenceException e) {
                throw new DAOException(e);
            }
        }
    }

    /**
     * Refresh given bean object.
     *
     * @param baseBean
     *            bean to refresh
     */
    public void refresh(T baseBean) {
        refreshObject(baseBean);
    }

    /**
     * Evict given bean object.
     *
     * @param baseBean
     *            bean to evict
     */
    public void evict(T baseBean) {
        evictObject(baseBean);
    }

    /**
     * Retrieves BaseBean objects from database by given query.
     *
     * @param query
     *            as String
     * @param parameters
     *            for query
     * @param first
     *            result
     * @param max
     *            amount of results
     * @return list of beans objects
     */
    @SuppressWarnings("unchecked")
    public List<T> getByQuery(String query, Map<String, Object> parameters, int first, int max) {
        try (Session session = HibernateUtil.getSession()) {
            Query<T> q = session.createQuery(query);
            q.setFirstResult(first);
            q.setMaxResults(max);
            addParameters(q, parameters);
            return q.list();
        } catch (SQLGrammarException e) {
            return Collections.emptyList();
        }
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
    @SuppressWarnings("unchecked")
    public List<T> getByQuery(String query, Map<String, Object> parameters) {
        try (Session session = HibernateUtil.getSession()) {
            Query<T> q = session.createQuery(query);
            addParameters(q, parameters);
            return q.list();
        }
    }

    /**
     * Retrieves BaseBean objects from database by given query.
     *
     * @param query
     *            as String
     * @return list of beans objects
     */
    @SuppressWarnings("unchecked")
    public List<T> getByQuery(String query) {
        try (Session session = HibernateUtil.getSession()) {
            List<T> baseBeanObjects = session.createQuery(query).list();
            if (Objects.isNull(baseBeanObjects)) {
                baseBeanObjects = new ArrayList<>();
            }
            return baseBeanObjects;
        }
    }

    /**
     * Count all rows in database.
     *
     * @param query
     *            for counting objects
     * @param parameters
     *            for query
     * @return amount of rows in database according to given query
     */
    public Long count(String query, Map<String, Object> parameters) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            Query<?> q = session.createQuery(query);
            addParameters(q, parameters);
            return (Long) q.uniqueResult();
        } catch (PersistenceException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Count all rows in database.
     *
     * @param query
     *            for counting objects
     * @return amount of rows in database according to given query
     */
    public Long count(String query) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            return (Long) session.createQuery(query).uniqueResult();
        } catch (PersistenceException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Executes an HQL query that returns scalar projections (e.g., specific fields or aggregate results)
     * instead of full entity objects.
     *
     * @param hql the HQL query string
     * @param parameters query parameters
     * @return list of scalar projection results
     */
    public List<Object[]> getProjectionByQuery(String hql, Map<String, Object> parameters) {
        try (Session session = HibernateUtil.getSession()) {
            Query<Object[]> query = session.createQuery(hql, Object[].class);
            addParameters(query, parameters);
            return query.getResultList();
        }
    }

    /**
     * Removes the object from the database with with specified class type and
     * {@code id}.
     *
     * @param cls
     *            the class type to remove
     * @param objectId
     *            the id of the class type
     * @throws DAOException
     *             if a HibernateException is thrown
     */
    static void removeObject(Class<?> cls, Integer objectId) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            Transaction transaction = session.beginTransaction();
            synchronized (lockObject) {
                Object object = session.load(cls, objectId);
                session.delete(object);
                session.flush();
                transaction.commit();
            }
        } catch (PersistenceException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Initialize child list of objects for given base bean.
     *
     * @param object
     *            for update
     * @param list
     *            child list for initialize
     */
    public void initialize(T object, List<? extends BaseBean> list) {
        try (Session session = HibernateUtil.getSession()) {
            session.update(object);
            Hibernate.initialize(list);
        }
    }

    /**
     * Retrieves an object of the class type specified by {@code cls}, and
     * having the given {@code id}.
     *
     * @param cls
     *            the class to load
     * @param id
     *            object id
     * @return Object may be null if object with ID doesn't exist
     */
    T retrieveObject(Class<T> cls, Integer id) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            return session.get(cls, id);
        } catch (PersistenceException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Retrieve objects by given query for maximum number of objects.
     *
     * @param query
     *            string
     * @param first
     *            result
     * @param max
     *            amount of results
     * @return constrained list of results
     */
    @SuppressWarnings("unchecked")
    List<T> retrieveObjects(String query, int first, int max) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            Query<T> sessionQuery = session.createQuery(query);
            sessionQuery.setFirstResult(first);
            sessionQuery.setMaxResults(max);
            return sessionQuery.list();
        } catch (PersistenceException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Retrieve all objects fro given class.
     *
     * @param cls
     *            class
     * @return List of all objects
     */
    @SuppressWarnings("unchecked")
    List<T> retrieveAllObjects(Class<T> cls) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            Query<T> query = session.createQuery(String.format("FROM %s ORDER BY id ASC", cls.getSimpleName()));
            return query.list();
        } catch (PersistenceException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Store given object.
     *
     * @param object
     *            to persist
     */
    void storeObject(T object) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            Transaction transaction = session.beginTransaction();
            session.saveOrUpdate(object);
            session.flush();
            transaction.commit();
        } catch (PersistenceException e) {
            throw new DAOException(e);
        }
    }

    void storeAsIndexed(List<T> baseBeans) throws DAOException {
        for (BaseBean baseBean : baseBeans) {
            BaseIndexedBean entity = (BaseIndexedBean) getById(baseBean.getId());
            entity.setIndexAction(IndexAction.DONE);
            storeObject((T) entity);
        }
    }

    /**
     * Store given list of objects.
     *
     * @param list
     *            of objects
     */
    void storeList(List<T> list) throws DAOException {
        try (Session session = HibernateUtil.getSession()) {
            Transaction transaction = session.beginTransaction();
            for (Object obj : list) {
                session.saveOrUpdate(obj);
            }
            session.flush();
            transaction.commit();
        } catch (RuntimeException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Evict object associated with the session.
     *
     * @param object
     *            associated with the session
     */
    private void evictObject(T object) {
        try (Session session = HibernateUtil.getSession()) {
            session.evict(object);
        }
    }

    /**
     * Refresh object associated with the session.
     *
     * @param object
     *            associated with the session
     */
    private void refreshObject(T object) {
        try (Session session = HibernateUtil.getSession()) {
            session.refresh(object);
        }
    }

    /**
     * Update of the object.
     *
     * @param object
     *            to update
     */
    void updateObject(T object) {
        try (Session session = HibernateUtil.getSession()) {
            session.update(object);
        }
    }

    private void addParameters(Query<?> query, Map<String, Object> parameters) {
        if (Objects.nonNull(parameters)) {
            for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
                if (parameter.getValue() instanceof List) {
                    query.setParameterList(parameter.getKey(), (List<?>) parameter.getValue());
                } else {
                    query.setParameter(parameter.getKey(), parameter.getValue());
                }
            }
        }
    }

    /**
     * Query part of date filter. Filter is configured by "database.subset.dates"
     * parameter in kitodo_config.properties.
     *
     * @param column
     *            The column to filter.
     * @return The query part to filter for dates.
     */
    public static String getDateFilter(String column) {
        List<String> dates = getDatesFromConfig();
        if (!dates.isEmpty()) {
            return " ( "
                    + dates.stream().map(date -> column + " LIKE '" + date + "%' ").collect(Collectors.joining(" OR "))
                    + " )";
        }
        return " 1=1 ";
    }

    /*
     * Parameter "database.subset.dates" filters the database to a subset. Atm, only
     * the largest data tables of tasks by processingBegin and process by creation
     * date are considered. The dates can be defined & separated in the format YYYY,
     * YYYY-MM or YYYY-MM-DD e.g. 2017-05-10,2018-06,2022
     */
    private static List<String> getDatesFromConfig() {
        final String[] databaseSubsetDates = ConfigMain.getStringArrayParameter("database.subset.dates");
        // sanitize entries of parameter
        return Arrays.stream(databaseSubsetDates)
                .filter(Pattern.compile("\\d{4}|\\d{4}-\\d{2}|\\d{4}-\\d{2}-\\d{2}").asMatchPredicate())
                .collect(Collectors.toList());
    }

}
