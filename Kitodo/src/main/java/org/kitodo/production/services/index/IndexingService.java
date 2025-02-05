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

package org.kitodo.production.services.index;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.search.engine.search.projection.SearchProjection;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingMonitor;
import org.kitodo.data.database.beans.BaseBean;
import org.kitodo.data.database.persistence.HibernateUtil;
import org.kitodo.production.helper.Helper;

public class IndexingService {

    private static final Logger logger = LogManager.getLogger(IndexingService.class);

    private static volatile IndexingService instance = null;

    String serverInformation;
    long serverLastCheck;
    long serverCheckThreadId;

    /**
     * Return singleton variable of type IndexingService.
     *
     * @return unique instance of IndexingService
     */
    public static IndexingService getInstance() {
        IndexingService localReference = instance;
        if (Objects.isNull(localReference)) {
            synchronized (IndexingService.class) {
                localReference = instance;
                if (Objects.isNull(localReference)) {
                    localReference = new IndexingService();
                    instance = localReference;
                }
            }
        }
        return localReference;
    }

    /**
     * Standard constructor.
     */
    private IndexingService() {
        new Thread(new ServerConnectionChecker(this)).start();
    }

    /**
     * Returns the server information. This consists of the server service and
     * the version number as returned by the search server.
     * 
     * <!-- A thread to retrieve the server information is started when the
     * IndexingService is constructed. If the server information is still null,
     * the result of this thread is waited for. Otherwise, another thread is
     * started to retrieve the server information (which will only affect the
     * next time this information is retrieved) and then the stored server
     * information is returned. This keeps the comparatively slow HTTP requests
     * out of the GUI thread and a failure of the search server is still
     * displayed very promptly. -->
     * 
     * @return the server information
     */
    public String getServerInformation() {
        if (Objects.isNull(this.serverInformation)) {
            try {
                while (Objects.isNull(this.serverInformation)) {
                    Thread.sleep(25);
                }
            } catch (InterruptedException e) {
                logger.error(e);
                return "";
            }
        } else {
            new Thread(new ServerConnectionChecker(this)).start();
        }
        if (this.serverInformation.isEmpty()) {
            Helper.setErrorMessage("elasticSearchNotRunning");
        }
        return this.serverInformation;
    }

    /**
     * Starts indexing for a bean type.
     * 
     * @param type
     *            class of beans to be indexed
     * @param monitor
     *            object to be notified of progress changes
     * @return a CompletionStage that can react asynchronously when the indexing
     *         ends (including to exceptions)
     */
    public CompletionStage<?> startIndexing(Class<? extends BaseBean> type, MassIndexingMonitor monitor) {
        MassIndexer massIndexer = Search.session(HibernateUtil.getSession()).massIndexer(type);
        massIndexer.dropAndCreateSchemaOnStart(true);
        if (Objects.nonNull(monitor)) {
            massIndexer.monitor(monitor);
        }
        return massIndexer.start();
    }

    /**
     * Searches for a search term in a search field and returns the hit IDs.
     * 
     * @param beanClass
     *            class of beans to search for
     * @param searchField
     *            search field to search on
     * @param value
     *            value to be found in the search field
     * @return ids of the found beans
     */
    public Collection<Integer> searchIds(Class<? extends BaseBean> beanClass, String searchField, String value) {
        SearchSession searchSession = Search.session(HibernateUtil.getSession());
        SearchProjection<Integer> idField = searchSession.scope(beanClass).projection().field("id", Integer.class)
                .toProjection();
        List<Integer> ids = searchSession.search(beanClass).select(idField).where(function -> function.match().field(
            searchField).matching(value)).fetchAll().hits();
        logger.debug("Searching {} IDs in field \"{}\" for \"{}\": {} hits", beanClass.getSimpleName(), searchField,
            value, ids.size());
        return ids;
    }
}
