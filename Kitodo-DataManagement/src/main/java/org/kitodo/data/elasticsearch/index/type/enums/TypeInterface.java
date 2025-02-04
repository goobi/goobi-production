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

package org.kitodo.data.elasticsearch.index.type.enums;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.kitodo.data.exceptions.DataException;

interface TypeInterface {

    /**
     * Get key for type.
     *
     * @return value of key - actually toString()
     */
    default String getKey() {
        return this.toString();
    }

    /**
     * Get boolean value from given json object.
     * 
     * @param jsonObject
     *            returned from ElasticSearch index
     * @return boolean value for given json
     */
    default boolean getBooleanValue(Map<String, Object> jsonObject) throws DataException {
        try {
            return (Boolean) jsonObject.get(this.toString());
        } catch (ClassCastException | NullPointerException e) {
            throw new DataException("Not possible to retrieve boolean value for key " + this + ". Exception: "
                    + e.getMessage());
        }
    }

    /**
     * Get int value from given json object.
     * 
     * @param jsonObject
     *            returned from ElasticSearch index
     * @return int value for given json
     */
    default int getIntValue(Map<String, Object> jsonObject) throws DataException {
        try {
            return (Integer) jsonObject.get(this.toString());
        } catch (ClassCastException | NullPointerException e) {
            throw new DataException("Not possible to retrieve int value for key " + this + ". Exception: "
                    + e.getMessage());
        }
    }

    /**
     * Get double value from given json object.
     * 
     * @param jsonObject
     *            returned from ElasticSearch index
     * @return double value for given json
     */
    default double getDoubleValue(Map<String, Object> jsonObject) throws DataException {
        try {
            return (double) jsonObject.get(this.toString());
        } catch (ClassCastException | NullPointerException e) {
            throw new DataException("Not possible to retrieve double value for key " + this + ". Exception: "
                    + e.getMessage());
        }
    }

    /**
     * Get String value from given json object.
     * 
     * @param jsonObject
     *            returned from ElasticSearch index
     * @return String value for given json
     */
    default String getStringValue(Map<String, Object> jsonObject) throws DataException {
        try {
            return (String) jsonObject.get(this.toString());
        } catch (ClassCastException | NullPointerException e) {
            throw new DataException("Not possible to retrieve String value for key " + this + ". Exception: "
                    + e.getMessage());
        }
    }

    /**
     * Get json array value from given json object.
     *
     * @param jsonObject
     *            returned from ElasticSearch index
     * @return json array for given json
     */
    @SuppressWarnings("unchecked")
    default List<Map<String, Object>> getJsonArray(Map<String, Object> jsonObject) throws DataException {
        try {
            return (List<Map<String, Object>>) jsonObject.get(this.toString());
        } catch (ClassCastException | NullPointerException e) {
            throw new DataException("Not possible to retrieve JsonArray value for key " + this + ". Exception: "
                    + e.getMessage());
        }
    }

    /**
     * Get size of related objects returned from ElasticSearch index.
     *
     * @param object
     *            JSONObject
     * @return size of array with related objects
     */
    default int getSizeOfProperty(Map<String, Object> object) throws DataException {
        if (Objects.nonNull(object)) {
            try {
                return ((List) object.get(this.toString())).size();
            } catch (ClassCastException | NullPointerException e) {
                throw new DataException("Not possible to retrieve size of array for key " + this + ". Exception: "
                        + e.getMessage());
            }
        }
        return 0;
    }
}
