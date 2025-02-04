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

package org.kitodo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kitodo.constants.StringConstants.KITODO;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kitodo.FileLoader;
import org.kitodo.exceptions.DoctypeMissingException;

public class ConfigProjectTest {

    private static ConfigProject configProject;

    @BeforeAll
    public static void setUp() throws Exception {
        FileLoader.createConfigProjectsFile();
        configProject = new ConfigProject("default");
    }

    @AfterAll
    public static void tearDown() throws IOException {
        FileLoader.deleteConfigProjectsFile();
    }

    @Test
    public void shouldGetConfigProjectsForItems() {
        List<String> items = configProject.getParamList("createNewProcess.itemlist.item");
        assertEquals(10, items.size(), "Incorrect amount of items!");
    }

    @Test
    public void shouldGetConfigProjectsForSelectItems() {
        List<String> items = configProject.getParamList("createNewProcess.itemlist.item(1).select");
        assertEquals(3, items.size(), "Incorrect amount of select items for second element!");
    }

    @Test
    public void shouldGetConfigProjectsForProcessTitles() {
        List<String> processTitles = configProject.getParamList("createNewProcess.itemlist.processtitle");
        assertEquals(5, processTitles.size(), "Incorrect amount of process titles!");
    }

    @Test
    public void shouldGetDocType() throws DoctypeMissingException {
        assertEquals("monograph", configProject.getDocType(), "Document type is incorrect!");
    }

    @Test
    public void shouldGetTifDefinition() throws DoctypeMissingException {
        assertEquals(KITODO, configProject.getTifDefinition(), "Tif definition is incorrect!");
    }

    @Test
    public void shouldGetTitleDefinition() throws DoctypeMissingException {
        String titleDefinition = configProject.getTitleDefinition();
        String expected = "TSL_ATS+'_'+CatalogIDDigital";
        assertEquals(expected, titleDefinition, "Title definition is incorrect!");
    }
}
