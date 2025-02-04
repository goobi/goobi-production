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

package org.kitodo.production.services.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kitodo.constants.StringConstants.CREATE;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kitodo.MockDatabase;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.data.database.beans.Ruleset;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.production.forms.createprocess.ProcessDetail;
import org.kitodo.production.services.ServiceManager;

public class MassImportServiceIT {

    /**
     * Prepare database before tests are run.
     *
     * @throws Exception when preparing database fails
     */
    @BeforeAll
    public static void prepareDatabase() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesFull();
    }

    /**
     * Cleanup database after all tests have been completed.
     *
     * @throws Exception when cleaning up database fails
     */
    @AfterAll
    public static void cleanupDatabase() throws Exception {
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
    }

    /**
     * Tests whether preparing addable metadata depending on already entered metadata and divisions defined in ruleset
     * works or not.
     *
     * @throws DAOException when retrieving list of divisions defined in test ruleset fails
     * @throws IOException when loading test ruleset file fails
     */
    @Test
    public void shouldGetAddableMetadataTable() throws DAOException, IOException {
        Collection<Metadata> enteredMetadata = createPresetMetadata();
        List<StructuralElementViewInterface> divisions = retrieveDivisions();
        List<ProcessDetail> addableMetadata = ServiceManager.getMassImportService().getAddableMetadataTable(divisions, enteredMetadata);
        assertFalse(addableMetadata.isEmpty(), "List of addable metadata should not be empty");
        assertTrue(addableMetadata.stream().anyMatch(m -> "TSL/ATS".equals(m.getLabel())), "List of addable metadata should contain 'TSL/ATS'");
    }

    private List<StructuralElementViewInterface> retrieveDivisions() throws DAOException, IOException {
        Ruleset ruleset = ServiceManager.getRulesetService().getById(1);
        RulesetManagementInterface rulesetInterface = ServiceManager.getRulesetService().openRuleset(ruleset);
        List<Locale.LanguageRange> priorityList = Locale.LanguageRange.parse("en");
        return rulesetInterface.getStructuralElements(priorityList).keySet().stream()
                .map(key -> rulesetInterface.getStructuralElementView(key, CREATE, priorityList))
                .collect(Collectors.toList());
    }

    private Collection<Metadata> createPresetMetadata() {
        Collection<Metadata> presetMetadata = new LinkedList<>();
        MetadataEntry titleMetadata = new MetadataEntry();
        titleMetadata.setKey("title");
        titleMetadata.setValue("Historische Zeitung");
        presetMetadata.add(titleMetadata);
        MetadataEntry placeMetadata = new MetadataEntry();
        placeMetadata.setKey("place");
        placeMetadata.setValue("Hamburg");
        presetMetadata.add(placeMetadata);
        MetadataEntry authorMetadata = new MetadataEntry();
        authorMetadata.setKey("author");
        authorMetadata.setValue("Hans Meier");
        presetMetadata.add(authorMetadata);
        return presetMetadata;
    }

}
