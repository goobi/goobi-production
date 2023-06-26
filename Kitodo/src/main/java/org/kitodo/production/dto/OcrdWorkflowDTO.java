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

package org.kitodo.production.dto;

/**
 * OCR-D workflow DTO object.
 */
public class OcrdWorkflowDTO extends BaseDTO {

    private String file;
    private String title;
    private ClientDTO client;

    /**
     * Get file.
     *
     * @return file as String
     */
    public String getFile() {
        return file;
    }

    /**
     * Set file.
     *
     * @param file
     *            as String
     */
    public void setFile(String file) {
        this.file = file;
    }

    /**
     * Get title.
     *
     * @return title as String
     */
    public String getTitle() {
        return title;
    }

    /**
     * Set title.
     *
     * @param title
     *            as String
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get client object.
     *
     * @return value of clientDTO
     */
    public ClientDTO getClient() {
        return client;
    }

    /**
     * Set client object.
     *
     * @param client
     *            as org.kitodo.production.dto.ClientDTO
     */
    public void setClientDTO(ClientDTO client) {
        this.client = client;
    }
}
