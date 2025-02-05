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

package org.kitodo.production.forms;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.faces.push.Push;
import javax.faces.push.PushContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.production.enums.IndexStates;
import org.kitodo.production.enums.ObjectType;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.index.IndexingService;
import org.omnifaces.util.Ajax;

@Named
@ApplicationScoped
public class IndexingForm {
    private static final Logger logger = LogManager.getLogger(IndexingForm.class);
    private static final List<ObjectType> objectTypes = ObjectType.getIndexableObjectTypes();
    private static final String POLLING_CHANNEL_NAME = "togglePollingChannel";
    private final IndexingService indexingService = ServiceManager.getIndexingService();
    private static final EnumMap<ObjectType, IndexingRow> indexingRows;
    private String indexingStartedUser = "";
    private LocalDateTime indexingStartedTime = null;

    @Inject
    @Push(channel = POLLING_CHANNEL_NAME)
    private PushContext pollingChannel;
    private boolean progressPolling;

    static {
        indexingRows = new EnumMap<>(ObjectType.class);
        for (ObjectType objectType : ObjectType.values()) {
            if (objectType.isIndexable()) {
                indexingRows.put(objectType, new IndexingRow(objectType.getBeanClass()));
            }
        }
    }

    /**
     * Sets the number of database objects.
     */
    public static void setNumberOfDatabaseObjects() {
        for (Entry<ObjectType, IndexingRow> entry : indexingRows.entrySet()) {
            long count = 0;
            try {
                switch (entry.getKey()) {
                    case BATCH:
                        count = ServiceManager.getBatchService().count();
                        break;
                    case DOCKET:
                        count = ServiceManager.getDocketService().count();
                        break;
                    case FILTER:
                        count = ServiceManager.getFilterService().count();
                        break;
                    case PROCESS:
                        count = ServiceManager.getProcessService().count();
                        break;
                    case PROJECT:
                        count = ServiceManager.getProjectService().count();
                        break;
                    case RULESET:
                        count = ServiceManager.getRulesetService().count();
                        break;
                    case TASK:
                        count = ServiceManager.getTaskService().count();
                        break;
                    case TEMPLATE:
                        count = ServiceManager.getTemplateService().count();
                        break;
                    case WORKFLOW:
                        count = ServiceManager.getWorkflowService().count();
                        break;
                    default:
                        throw new IllegalStateException(entry.getKey().getTranslationSingular()
                                + " is no class for indexing");
                }
            } catch (DAOException e) {
                logger.error(e);
            }
            entry.getValue().setNumberOfDatabaseObjects(count);
        }
    }

    /**
     * Get user which started indexing.
     *
     * @return user which started indexing
     */
    public String getIndexingStartedUser() {
        return indexingStartedUser;
    }

    /**
     * Get time when indexing has started.
     *
     * @return time when indexing has started as LocalDateTime
     */
    public LocalDateTime getIndexingStartedTime() {
        return indexingStartedTime;
    }

    /**
     * Return the total number of all objects that can be indexed.
     *
     * @return long number of all items that can be written to the index
     */
    public long getTotalCount() {
        return indexingRows.values().stream().mapToLong(IndexingRow::getCount).sum();
    }

    /**
     * Return the number of objects for the given ObjectType.
     *
     * @param objectType
     *            ObjectType for which the number of objects is returned
     *
     * @return number of indexed objects
     */
    public String getNumberOfObjects(ObjectType objectType) {
        return indexingRows.get(objectType).getNumberOfObjects();
    }

    /**
     * Return the number of all objects processed during the current indexing
     * progress.
     *
     * @return long number of all currently indexed objects
     */
    public long getAllIndexed() {
        return indexingRows.values().stream().mapToLong(IndexingRow::getIndexed).filter(indexed -> indexed >= 0).sum();
    }

    /**
     * Index all objects of given type 'objectType'.
     *
     * @param type
     *            type objects that get indexed
     */
    public void callIndexing(ObjectType type) {
        indexingRows.get(type).callIndexing();
        pollingChannel.send("indexing_started");
        progressPolling = true;
    }

    /**
     * Starts the process of indexing all objects to the ElasticSearch index.
     */
    public void startAllIndexing() {
        indexingStartedTime = LocalDateTime.now();
        indexingStartedUser = ServiceManager.getUserService().getAuthenticatedUser().getFullName();
        for (IndexingRow indexingRow : indexingRows.values()) {
            if (!indexingRow.isIndexingInProgress()) {
                indexingRow.callIndexing();
            }
        }
        pollingChannel.send("indexing_started");
        progressPolling = true;
    }

    /**
     * Return the overall progress in percent of the currently running indexing
     * process, incorporating the total number of indexed and all objects.
     *
     * @return the overall progress of the indexing process
     */
    public int getAllIndexingProgress() {
        long numerator = 0;
        long denominator = 0;
        for (IndexingRow indexingRow : indexingRows.values()) {
            if (indexingRow.getIndexed() >= 0) {
                numerator += indexingRow.getIndexed();
                denominator += indexingRow.getCount();
            }
        }
        if (denominator != 0) {
            return (int) (100 * numerator / denominator);
        } else {
            return 0;
        }
    }

    /**
     * Return whether all indexing processes are currently in progress or not.
     *
     * @return boolean Value indicating whether all indexing processes are
     *         currently in progress or not
     */
    public boolean indexingInProgress(boolean some) {
        if (some) {
            for (IndexingRow indexingRow : indexingRows.values()) {
                if (!indexingRow.isIndexingInProgress()) {
                    return false;
                }
            }
            return true;
        } else {
            for (IndexingRow indexingRow : indexingRows.values()) {
                if (indexingRow.isIndexingInProgress()) {
                    return true;
                }
            }
            return false;
        }
    }

    public boolean indexingInProgress(ObjectType type) {
        return indexingRows.get(type).isIndexingInProgress();
    }

    /**
     * Return server information provided by the searchService and gathered by the
     * rest client.
     *
     * @return String information about the server
     */
    public String getServerInformation() {
        return indexingService.getServerInformation();
    }

    /**
     * Return the progress in percent of the currently running indexing process.
     * If the list of entries to be indexed is empty, this will return "0".
     *
     * @param currentType
     *            the ObjectType for which the progress will be determined
     * @return the progress of the current indexing process in percent
     */
    public int getProgress(ObjectType currentType) {
        return indexingRows.get(currentType).getProgress();
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
        return indexingRows.get(objectType).getObjectIndexState();
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
        for (IndexingRow indexingRow : indexingRows.values()) {
            if (Objects.equals(IndexStates.INDEXING_FAILED, indexingRow.objectIndexState)) {
                return IndexStates.INDEXING_FAILED;
            } else if (!Objects.equals(IndexStates.INDEXING_SUCCESSFUL, indexingRow.objectIndexState)) {
                return IndexStates.NO_STATE;
            }
        }
        if (progressPolling) {
            progressPolling = false;
            pollingChannel.send("indexing_finished");
        }
        return IndexStates.INDEXING_SUCCESSFUL;
    }

    /**
     * Return the array of object type values defined in the ObjectType enum.
     *
     * @return array of object type values
     */
    public ObjectType[] getObjectTypes() {
        return objectTypes.toArray(new ObjectType[0]);
    }

    /**
     * Return object types as JSONArray.
     *
     * @return JSONArray containing objects type constants.
     */
    public JsonArray getObjectTypesAsJson() {
        JsonArrayBuilder objectsTypesJson = Json.createArrayBuilder();
        for (ObjectType objectType : objectTypes) {
            objectsTypesJson.add(objectType.toString());
        }
        return objectsTypesJson.build();
    }

    /**
     * Return NONE object type.
     *
     * @return ObjectType NONE object type
     */
    public ObjectType getNoneType() {
        return ObjectType.NONE;
    }

    /**
     * Update the view.
     */
    public void updateView() {
        Ajax.update("@all");
    }
}
