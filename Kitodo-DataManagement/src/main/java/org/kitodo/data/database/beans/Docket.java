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

package org.kitodo.data.database.beans;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "docket")
public class Docket extends BaseBean {

    @Column(name = "title")
    private String title;

    @Column(name = "file")
    private String file;

    @Column(name = "active")
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", foreignKey = @ForeignKey(name = "FK_docket_client_id"))
    private Client client;

    /**
     * Returns the display name of the configuration. It is displayed to the
     * user in a selection dialog.
     *
     * @return the display name
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the display name of the configuration.
     *
     * @param title
     *            the display name
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the name of the configuration file, without a path. The file must
     * exist in the XSLT directory. The directory is set in the configuration
     * file.
     *
     * @return the XSLT file name
     */
    public String getFile() {
        return file;
    }

    /**
     * Sets the name of the configuration file. The file must exist in the XSLT
     * directory. The file name must be specified without a path.
     *
     * @param file
     *            XSLT file name
     */
    public void setFile(String file) {
        this.file = file;
    }

    /**
     * Check if docket is active.
     *
     * @return true or false
     */
    public Boolean isActive() {
        if (Objects.isNull(this.active)) {
            this.active = true;
        }
        return this.active;
    }

    /**
     * Set docket as active.
     *
     * @param active as boolean
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the client that this docket generator configuration is associated
     * with. Technically, multiple client can use the same docket generator
     * configuration (file), but they must be made available independently for
     * each client using one configuration object each. This determines which
     * docket generator configurations are visible to a client at all, and they
     * can be named differently.
     *
     * @return client that this docket is associated with
     */
    public Client getClient() {
        return this.client;
    }

    /**
     * Sets the client to which this docket generator configuration is
     * associated.
     *
     * @param client
     *            client to which this docket is associated
     */
    public void setClient(Client client) {
        this.client = client;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Docket) {
            Docket docket = (Docket) object;
            return Objects.equals(this.getId(), docket.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, file, active);
    }
}
