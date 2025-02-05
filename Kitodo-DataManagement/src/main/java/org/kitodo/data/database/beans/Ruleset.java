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
@Table(name = "ruleset")
public class Ruleset extends BaseBean {

    @Column(name = "title")
    private String title;

    @Column(name = "file")
    private String file;

    @Column(name = "orderMetadataByRuleset")
    private Boolean orderMetadataByRuleset = false;

    @Column(name = "active")
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", foreignKey = @ForeignKey(name = "FK_ruleset_client_id"))
    private Client client;

    /**
     * Returns the display name of the business domain model. It is displayed to
     * the user in a selection dialog.
     *
     * @return the display name
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the display name of the business domain model.
     *
     * @param title
     *            the display name
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the name of the configuration file, without a path. The file must
     * exist in the ruleset directory. The directory is set in the configuration
     * file.
     *
     * @return the XML file name
     */
    public String getFile() {
        return this.file;
    }

    /**
     * Sets the name of the configuration file. The file must exist in the
     * ruleset directory. The file name must be specified without a path.
     *
     * @param file
     *            XML file name
     */
    public void setFile(String file) {
        this.file = file;
    }

    /**
     * Returns whether the elements of the ruleset should be displayed in the
     * declared order. If not, they are displayed alphabetically. It varies at
     * which points this sorting takes effect and what is sorted on.
     *
     * @return whether the elements should be in declared order
     */
    public boolean isOrderMetadataByRuleset() {
        if (this.orderMetadataByRuleset == null) {
            this.orderMetadataByRuleset = false;
        }
        return this.orderMetadataByRuleset;
    }

    /**
     * Sets whether the elements of the ruleset should be displayed in the
     * declared order.
     *
     * @param orderMetadataByRuleset
     *            whether the elements should be in declared order
     */
    public void setOrderMetadataByRuleset(boolean orderMetadataByRuleset) {
        this.orderMetadataByRuleset = orderMetadataByRuleset;
    }

    /**
     * Determines whether the ruleset is active. A deactivated rule set is not
     * offered for selection, but can continue to be used where it is already in
     * use.
     *
     * @return whether the ruleset is active
     */
    public Boolean isActive() {
        if (Objects.isNull(this.active)) {
            this.active = true;
        }
        return this.active;
    }

    /**
     * Sets whether the ruleset is active.
     *
     * @param active
     *            whether the ruleset is active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the client that this ruleset is associated with. Technically,
     * multiple client can use the same docket generator configuration (file),
     * but they must be made available independently for each client using one
     * configuration object each. This determines which rulesets are visible to
     * a client at all, and they can be named differently.
     *
     * @return client that this ruleset is associated with
     */
    public Client getClient() {
        return this.client;
    }

    /**
     * Sets the client to which this ruleset is associated.
     *
     * @param client
     *            client to which this ruleset is associated
     */
    public void setClient(Client client) {
        this.client = client;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof Ruleset) {
            Ruleset ruleset = (Ruleset) object;
            return Objects.equals(this.getId(), ruleset.getId());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, file, orderMetadataByRuleset, active);
    }
}
