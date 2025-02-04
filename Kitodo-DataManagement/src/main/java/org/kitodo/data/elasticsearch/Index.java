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

package org.kitodo.data.elasticsearch;

import static org.kitodo.constants.StringConstants.KITODO;

import javax.persistence.Table;

import org.kitodo.config.ConfigMain;

/**
 * Super class for Indexer and Searcher.
 */
public abstract class Index {

    protected String index;
    protected String type;

    /**
     * Constructor with type names equal to table names.
     *
     * @param beanClass
     *            as Class
     */
    public Index(Class<?> beanClass) {
        Table table = beanClass.getAnnotation(Table.class);
        this.index = ConfigMain.getParameter("elasticsearch.index", KITODO);
        this.setType(table.name());
    }

    /**
     * Constructor with type names not equal to table names.
     *
     * @param type
     *            as String
     */
    public Index(String type) {
        this.index = ConfigMain.getParameter("elasticsearch.index", KITODO);
        this.setType(type);
    }

    /**
     * Getter for type.
     *
     * @return type name
     */
    public String getType() {
        return type;
    }

    /**
     * Setter for type.
     *
     * @param type
     *            - equal to the name of table in database, but not necessary
     */
    public void setType(String type) {
        this.type = type;
    }
}
