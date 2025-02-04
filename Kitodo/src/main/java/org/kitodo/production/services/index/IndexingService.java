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

import static java.lang.Math.toIntExact;
import static org.kitodo.constants.StringConstants.KITODO;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import javax.faces.push.PushContext;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.ConfigMain;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.elasticsearch.KitodoRestClient;
import org.kitodo.data.elasticsearch.exceptions.CustomResponseException;
import org.kitodo.data.elasticsearch.index.IndexRestClient;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.enums.IndexStates;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.IndexWorker;
import org.kitodo.production.helper.IndexWorkerStatus;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.base.SearchService;

public class IndexingService {

    private static final Logger logger = LogManager.getLogger(IndexingService.class);

    private static volatile IndexingService instance = null;

    private static final List<ObjectType> objectTypes = ObjectType.getIndexableObjectTypes();
    private final Map<ObjectType, SearchService> searchServices = new EnumMap<>(ObjectType.class);
    private final Map<ObjectType, IndexStates> objectIndexingStates = new EnumMap<>(ObjectType.class);
    private final Map<ObjectType, Integer> countDatabaseObjects = new EnumMap<>(ObjectType.class);

    // messages for web socket communication
    private static final String INDEXING_STARTED_MESSAGE = "indexing_started";
    static final String INDEXING_FINISHED_MESSAGE = "indexing_finished";

    public static final String DELETION_STARTED_MESSAGE = "deletion_started";
    private static final String DELETION_FINISHED_MESSAGE = "deletion_finished";
    private static final String DELETION_FAILED_MESSAGE = "deletion_failed";

    public static final String MAPPING_STARTED_MESSAGE = "mapping_started";
    private static final String MAPPING_FINISHED_MESSAGE = "mapping_finished";
    public static final String MAPPING_FAILED_MESSAGE = "mapping_failed";

    static final int PAUSE = 1000;

    private IndexWorkerStatus indexWorkerStatus = null;
    private IndexManagmentThread indexAllThread = null;
    private boolean indexingAll = false;

    private ObjectType currentIndexState = ObjectType.NONE;
    private IndexStates currentState = IndexStates.NO_STATE;

    private static final IndexRestClient indexRestClient = IndexRestClient.getInstance();

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
        for (ObjectType objectType : objectTypes) {
            searchServices.put(objectType, getService(objectType));
            objectIndexingStates.put(objectType, IndexStates.NO_STATE);
        }
        indexRestClient.setIndexBase(ConfigMain.getParameter("elasticsearch.index", KITODO));
        try {
            countDatabaseObjects();
        } catch (DAOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
        }
    }

    private SearchService getService(ObjectType objectType) {
        if (!searchServices.containsKey(objectType) || Objects.isNull(searchServices.get(objectType))) {
            switch (objectType) {
                case BATCH:
                    searchServices.put(objectType, ServiceManager.getBatchService());
                    break;
                case DOCKET:
                    searchServices.put(objectType, ServiceManager.getDocketService());
                    break;
                case PROCESS:
                    searchServices.put(objectType, ServiceManager.getProcessService());
                    break;
                case PROJECT:
                    searchServices.put(objectType, ServiceManager.getProjectService());
                    break;
                case RULESET:
                    searchServices.put(objectType, ServiceManager.getRulesetService());
                    break;
                case TASK:
                    searchServices.put(objectType, ServiceManager.getTaskService());
                    break;
                case TEMPLATE:
                    searchServices.put(objectType, ServiceManager.getTemplateService());
                    break;
                case WORKFLOW:
                    searchServices.put(objectType, ServiceManager.getWorkflowService());
                    break;
                case FILTER:
                    searchServices.put(objectType, ServiceManager.getFilterService());
                    break;
                default:
                    return null;
            }
        }
        return searchServices.get(objectType);
    }

    /**
     * Return the total number of all objects that can be indexed.
     *
     * @return long number of all items that can be written to the index
     */
    public long getTotalCount() {
        int totalCount = 0;
        for (ObjectType objectType : objectTypes) {
            totalCount += countDatabaseObjects.get(objectType);
        }
        return totalCount;
    }

    /**
     * Update counts of index and database objects.
     */
    private void updateCounts() throws DAOException {
        countDatabaseObjects();
    }

    public Map<ObjectType, Integer> getCountDatabaseObjects() {
        return countDatabaseObjects;
    }

    public boolean isIndexCorrupted() throws DAOException, DataException {
        updateCounts();
        return getTotalCount() != getAllIndexed();
    }

    /**
     * Return the number of all objects processed during the current indexing
     * progress.
     *
     * @return long number of all currently indexed objects
     * @throws ArithmeticException
     *             if the value will not fit in a {@code long}
     */
    public long getAllIndexed() throws DataException {
        long allIndexed = 0;
        for (ObjectType objectType : objectTypes) {
            allIndexed = Math.addExact(allIndexed, getNumberOfIndexedObjects(objectType));
        }
        return allIndexed;
    }

    /**
     * Return the number of indexed objects for the given ObjectType.
     *
     * @param objectType
     *            ObjectType for which the number of indexed objects is returned
     *
     * @return number of indexed objects
     */
    public long getNumberOfIndexedObjects(ObjectType objectType) throws DataException {
        return searchServices.get(objectType).count();
    }

    /**
     * Count database objects. Execute it on application start and next on button
     * click.
     */
    public void countDatabaseObjects() throws DAOException {
        for (ObjectType objectType : objectTypes) {
            countDatabaseObjects.put(objectType, getNumberOfDatabaseObjects(objectType));
        }
    }

    /**
     * Manage indexing for a given object type.
     * 
     * <p>This method is executed in the `IndexManagementThread`.</p>
     *
     * @param type
     *            type objects that get indexed
     */
    public IndexWorkerStatus runIndexing(ObjectType type, PushContext pushContext, boolean indexAllObjects) 
            throws DataException, CustomResponseException, DAOException {
        SearchService searchService = searchServices.get(type);
        int indexLimit = ConfigCore.getIntParameterOrDefaultValue(ParameterCore.ELASTICSEARCH_INDEXLIMIT);

        if (countDatabaseObjects.get(type) > 0) {
            if (indexAllObjects) {
                // only check for loose index data when indexing full objects
                Long amountInIndex = searchService.count();
                long offset = 0L;

                // remove documents in index that are no longer available in database
                // by iterating over all indexed documents in elastic search
                try {
                    while (offset < amountInIndex) {
                        // TODO: actually iterate over all elastic search documents
                        searchService.removeLooseIndexData(searchService.findAllIDs(offset, indexLimit));
                        offset += indexLimit;
                    }
                } catch (RuntimeException e) {
                    // this is an elastic search exception, but elastic search is not available here
                    logger.info("Cannot check documents beyond elastic search max_result_window, continuing ...");
                }
            }

            return spawnIndexingThreads(type, pushContext, indexAllObjects);
        }

        return null;
    }

    /**
     * Return the number of objects in the database for the given ObjectType.
     *
     * @param objectType
     *            name of ObjectType for which the number of database objects is
     *            returned
     * @return number of database objects
     */
    private int getNumberOfDatabaseObjects(ObjectType objectType) throws DAOException {
        SearchService searchService = searchServices.get(objectType);
        if (Objects.nonNull(searchService)) {
            return toIntExact(searchService.countDatabaseRows());
        }
        return 0;
    }

    private ExecutorService createDeamonizedExecutorService(int threads) {
        return Executors.newFixedThreadPool(threads,
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
            }
        );
    }

    /**
     * Create multiple indexing worker threads and wait until they are finished.
     * 
     * <p>This method is executed in the `IndexAllThread`.</p>
     * 
     * @param type the object type to be indexed
     * @param pollingChannel the UI polling channel for triggering updates
     * @param indexAllObjects whehter all or only remaining objects are indexed
     */
    private IndexWorkerStatus spawnIndexingThreads(ObjectType type, PushContext pollingChannel, boolean indexAllObjects) 
            throws DAOException {
        // declare that indexing for type has started
        currentIndexState = type;
        currentState = IndexStates.INDEXING_STARTED;
        objectIndexingStates.put(type, IndexStates.INDEXING_STARTED);

        int threads = ConfigCore.getIntParameterOrDefaultValue(ParameterCore.ELASTICSEARCH_THREADS);
        int totalNumberOfObjects = getNumberOfDatabaseObjects(type);
        int batchSize = ConfigCore.getIntParameterOrDefaultValue(ParameterCore.ELASTICSEARCH_BATCH);
        int maxBatch = (int)Math.ceil((double)(totalNumberOfObjects) / (double)(batchSize));

        // create new thread-safe indexing status
        indexWorkerStatus = new IndexWorkerStatus(maxBatch);

        logger.info("start " + threads + " threads for indexing " + type.toString());
        ExecutorService executor = null;
        try {
            executor = createDeamonizedExecutorService(threads);

            List<Future<?>> futures = new LinkedList<Future<?>>();
            for (int i = 0; i < threads; i++) {
                Future<?> future = executor.submit(new IndexWorker(getService(type), type, indexWorkerStatus, indexAllObjects));
                futures.add(future);
            }

            waitWhileIndexing(type, futures, pollingChannel);
        } finally {
            if (Objects.nonNull(executor)) {
                executor.shutdown();
            }
        }

        return indexWorkerStatus;
    }

    /**
     * Wait and check whether index worker threads have finished or failed. 
     * 
     * <p>Also trigger UI updates every second.</p>
     * 
     * <p>This method is executed in the `IndexAllThread`.</p>
     * 
     * @param type the object type currently being indexed
     * @param futures a list of futures allowing to check the status of the worker threads
     * @param pollingChannel the UI polling channel
     */
    private void waitWhileIndexing(ObjectType type, List<Future<?>> futures, PushContext pollingChannel) {
        // send update that indexing has started (activating polling in user interface)
        pollingChannel.send(INDEXING_STARTED_MESSAGE + type);

        while (true) {
            // check whether all jobs are done
            boolean done = true;
            boolean failed = indexWorkerStatus.hasFailed() || indexWorkerStatus.isCanceled();
            for (Future<?> future : futures) {
                if (!future.isDone()) {
                    done = false;
                }
                if (future.isCancelled()) {
                    failed = true;
                }
            }

            // check for failure first (in case all threads have already stopped gracefully and are done)
            if (failed) {
                logger.info("indexing of " + type.toString() + " failed, cleaning up");
                currentIndexState = ObjectType.NONE;
                currentState = IndexStates.INDEXING_FAILED;
                objectIndexingStates.put(type, IndexStates.INDEXING_FAILED);

                // make sure to stop any remaining other worker threads
                for (Future<?> future : futures) {
                    future.cancel(true);
                }
                break;
            }

            if (done) {
                // indexing has completed
                logger.info("indexing of " + type.toString() + " finished successfully");
                currentIndexState = ObjectType.NONE;
                currentState = IndexStates.INDEXING_SUCCESSFUL;
                objectIndexingStates.put(type, IndexStates.INDEXING_SUCCESSFUL);
                break;
            }           

            // wait a bit
            try {
                Thread.sleep(PAUSE);
            } catch (InterruptedException e) {
                logger.trace("Index management sleep interrupted while waiting for worker threads to finish indexing");
            }
        }
    }

    /**
     * Return the progress in percent of the currently running indexing process. If
     * the list of entries to be indexed is empty, this will return "0".
     *
     * @param currentType
     *            the ObjectType for which the progress will be determined
     * @return the progress of the current indexing process in percent
     */
    public int getProgress(ObjectType currentType, PushContext pollingChannel) throws DataException {
        long numberOfObjects = countDatabaseObjects.get(currentType);
        long nrOfIndexedObjects = getNumberOfIndexedObjects(currentType);
        int progress = numberOfObjects > 0 ? (int) ((nrOfIndexedObjects / (float) numberOfObjects) * 100) : 0;
        if (Objects.equals(currentIndexState, currentType) && (numberOfObjects == 0 || progress == 100)) {
            currentIndexState = ObjectType.NONE;
            if (numberOfObjects == 0) {
                objectIndexingStates.put(currentType, IndexStates.NO_STATE);
            } else {
                objectIndexingStates.put(currentType, IndexStates.INDEXING_SUCCESSFUL);
            }
            pollingChannel.send(INDEXING_FINISHED_MESSAGE + currentType + "!");
        }
        return progress;
    }

    /**
     * Create mapping which enables sorting and other aggregation functions.
     */
    public String createMapping() throws IOException, CustomResponseException {
        for (String mappingType : KitodoRestClient.MAPPING_TYPES) {
            String mapping = readMapping(mappingType);
            if (StringUtils.isBlank(mapping)) {
                if (indexRestClient.createIndex(null, mappingType)) {
                    currentState = IndexStates.CREATING_MAPPING_SUCCESSFUL;
                } else {
                    currentState = IndexStates.CREATING_MAPPING_FAILED;
                    return MAPPING_FAILED_MESSAGE;
                }
            } else {
                if (indexRestClient.createIndex(mapping, mappingType)) {
                    if (isMappingValid(mapping, mappingType)) {
                        currentState = IndexStates.CREATING_MAPPING_SUCCESSFUL;
                    } else {
                        currentState = IndexStates.CREATING_MAPPING_FAILED;
                        return MAPPING_FAILED_MESSAGE;
                    }
                } else {
                    currentState = IndexStates.CREATING_MAPPING_FAILED;
                    return MAPPING_FAILED_MESSAGE;
                }
            }
        }
        return MAPPING_FINISHED_MESSAGE;
    }

    /**
     * Delete whole ElasticSearch index.
     */
    public String deleteIndex() {
        try {
            indexRestClient.deleteAllIndexes();
            currentState = IndexStates.DELETING_SUCCESSFUL;
            return DELETION_FINISHED_MESSAGE;
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            currentState = IndexStates.DELETING_FAILED;
            return DELETION_FAILED_MESSAGE;
        }
    }

    private boolean isMappingValid(String mapping, String mappingType) {
        return isMappingEqualTo(mapping, mappingType);
    }


    /**
     * Return server information provided by the searchService and gathered by the
     * rest client.
     *
     * @return String information about the server
     */
    public String getServerInformation() throws IOException {
        return indexRestClient.getServerInformation();
    }

    /**
     * Tests and returns whether the ElasticSearch index has been created or not.
     *
     * @return whether the ElasticSearch index exists or not
     */
    public boolean indexExists() throws IOException, CustomResponseException {
        return indexRestClient.typeIndexesExist();
    }

    /**
     * Return the state of the ES index. -2 = failed deleting the index -1 = failed
     * creating ES mapping 1 = successfully created ES mapping 2 = successfully
     * deleted index
     *
     * @return state of ES index
     */
    public IndexStates getIndexState() {
        return currentState;
    }

    public void setIndexState(IndexStates state) {
        currentState = state;
    }

    /**
     * Return the index state of the given objectType.
     *
     * @param objectType
     *            the objectType for which the IndexState should be returned
     *
     * @return indexing state of the given object type.
     */
    public IndexStates getObjectIndexState(ObjectType objectType) {
        return objectIndexingStates.get(objectType);
    }

    /**
     * Return static variable representing the global state. - return 'indexing
     * failed' state if any object type is in 'indexing failed' state - return 'no
     * state' if any object type is in 'no state' state - return 'indexing
     * successful' state if all object types are in 'indexing successful' state
     *
     * @return static variable for global indexing state
     */
    public IndexStates getAllObjectsIndexingState() {
        for (ObjectType objectType : objectTypes) {
            if (Objects.equals(objectIndexingStates.get(objectType), IndexStates.INDEXING_FAILED)) {
                return IndexStates.INDEXING_FAILED;
            }
            if (Objects.equals(objectIndexingStates.get(objectType), IndexStates.NO_STATE)) {
                return IndexStates.NO_STATE;
            }
        }
        return IndexStates.INDEXING_SUCCESSFUL;
    }

    /**
     * Return whether any indexing process is currently in progress or not.
     *
     * @return boolean Value indicating whether any indexing process is currently in
     *         progress or not
     */
    public boolean indexingInProgress() {
        return !Objects.equals(this.currentIndexState, ObjectType.NONE) || indexingAll;
    }

    /**
     * Check if current mapping is empty.
     *
     * @return true if mapping is empty, otherwise false
     */
    public boolean isMappingEmpty() {
        String emptyMapping = "{\n\"mappings\": {\n\n    }\n}";
        for (String mappingType : KitodoRestClient.MAPPING_TYPES) {
            if (isMappingEqualTo(emptyMapping, mappingType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMappingEqualTo(String mapping, String mappingType) {
        try (JsonReader mappingExpectedReader = Json.createReader(new StringReader(mapping));
             JsonReader mappingCurrentReader = Json.createReader(new StringReader(indexRestClient.getMapping(mappingType)))) {
            JsonObject mappingExpected = mappingExpectedReader.readObject();
            JsonObject mappingCurrent = mappingCurrentReader.readObject().getJsonObject(indexRestClient.getIndexBase() + "_" + mappingType);
            return mappingExpected.equals(mappingCurrent);
        } catch (IOException e) {
            return false;
        }
    }

    private static String readMapping(String mappingType) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classloader.getResourceAsStream("elasticsearch_mappings/" + mappingType + ".json")) {
            if (Objects.nonNull(inputStream)) {
                String mapping = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                try (JsonReader jsonReader = Json.createReader(new StringReader(mapping))) {
                    JsonObject jsonObject = jsonReader.readObject();
                    return jsonObject.toString();
                }
            } else {
                Helper.setErrorMessage("Mapping not found!");
                return "";
            }
        } catch (IOException e) {
            Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
            return "";
        }
    }

    private void startIndexingThread(PushContext context, boolean indexAllObjects, ObjectType objectType) throws IllegalStateException {
        if (Objects.isNull(indexAllThread) || !indexAllThread.isAlive()) {
            indexAllThread = new IndexManagmentThread(context, this, objectType, indexAllObjects);
            indexAllThread.setName("IndexManagementThread");
            indexAllThread.start();
        } else {
            throw new IllegalStateException("indexing already in progress, can not start again");
        }
    }

    /**
     * Start indexing of all objects of specific object type.
     * 
     * @param context the UI context
     * @param objectType the object type to index
     */
    public void startIndexing(PushContext context, ObjectType objectType) {
        startIndexingThread(context, true, objectType);
    }

    /**
     * Start indexing of remaining objects of specific object type.
     * 
     * @param context the UI context
     * @param objectType the object type to index
     */
    public void startIndexingRemaining(PushContext context, ObjectType objectType) {
        startIndexingThread(context, false, objectType);
    }

    /**
     * Start indexing of all database objects independent of object type.
     */
    public void startAllIndexing(PushContext context) {
        startIndexingThread(context, true, null);
    }

    /**
     * Starts indexing all remaining database objects independent of object type.
     */
    public void startAllIndexingRemaining(PushContext pushContext) {
        startIndexingThread(pushContext, false, null);
    }

    void setIndexingAll(boolean indexing) {
        indexingAll = indexing;
    }

    void resetCurrentIndexState() {
        currentIndexState = ObjectType.NONE;
    }

    /**
     * Cancels indexing upon user request.
     */
    public void cancelIndexing() {
        if (Objects.nonNull(indexWorkerStatus)) {
            indexWorkerStatus.markAsCanceled();
        }
    }

    /**
     * Get logger.
     *
     * @return value of logger
     */
    public static Logger getLogger() {
        return logger;
    }
}
