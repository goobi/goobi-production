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

package org.kitodo.production.metadata;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kitodo.constants.StringConstants.EDIT;
import static org.kitodo.test.utils.ProcessTestUtils.METADATA_BASE_DIR;
import static org.kitodo.test.utils.ProcessTestUtils.META_XML;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kitodo.MockDatabase;
import org.kitodo.SecurityTestUtils;
import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.MetadataGroup;
import org.kitodo.api.dataeditor.rulesetmanagement.MetadataViewInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataeditor.rulesetmanagement.StructuralElementViewInterface;
import org.kitodo.api.dataformat.LogicalDivision;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.data.database.beans.User;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.test.utils.ProcessTestUtils;
import org.kitodo.test.utils.TestConstants;


public class MetadataEditorIT {
    private static final ProcessService processService = ServiceManager.getProcessService();
    private static final String firstProcess = "First process";
    private static Map<String, Integer> testProcessIds;
    private static final String TEST_METADATA_CHILD_PROCESS_TO_ADD = "testMetadataForNonBlockingParallelTasksTest.xml";
    private static final String TEST_METADATA_FILE = "testMetadataFileServiceTest.xml";
    private static final String TEST_PROCESS_TITLE = "Test process";

    /**
     * Is running before the class runs.
     */
    @BeforeAll
    public static void prepareDatabase() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesFull();
        testProcessIds = MockDatabase.insertProcessesForHierarchyTests();
        ProcessTestUtils.copyHierarchyTestFiles(testProcessIds);
        ProcessTestUtils.copyTestFiles(testProcessIds.get(MockDatabase.HIERARCHY_CHILD_TO_ADD), TEST_METADATA_CHILD_PROCESS_TO_ADD);
        MockDatabase.setUpAwaitility();
        User userOne = ServiceManager.getUserService().getById(1);
        SecurityTestUtils.addUserDataToSecurityContext(userOne, 1);
        await().until(() -> {
            SecurityTestUtils.addUserDataToSecurityContext(userOne, 1);
            return !processService.findByTitle(firstProcess).isEmpty();
        });
    }

    /**
     * Is running after the class has run.
     */
    @AfterAll
    public static void cleanDatabase() throws Exception {
        ProcessTestUtils.removeTestProcess(testProcessIds.get(MockDatabase.HIERARCHY_PARENT));
        ProcessTestUtils.removeTestProcess(testProcessIds.get(MockDatabase.HIERARCHY_CHILD_TO_ADD));
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
    }

    @Test
    public void shouldAddLink() throws Exception {
        int parentId = testProcessIds.get(MockDatabase.HIERARCHY_PARENT);
        int childId = testProcessIds.get(MockDatabase.HIERARCHY_CHILD_TO_ADD);
        File metaXmlFile = new File(METADATA_BASE_DIR + parentId + META_XML);
        List<String> metaXmlContentBefore = FileUtils.readLines(metaXmlFile, StandardCharsets.UTF_8);

        MetadataEditor.addLink(ServiceManager.getProcessService().getById(parentId), "0", childId);

        assertTrue(isInternalMetsLink(FileUtils.readLines(metaXmlFile, StandardCharsets.UTF_8).get(36), childId), "The link was not added correctly!");

        FileUtils.writeLines(metaXmlFile, StandardCharsets.UTF_8.toString(), metaXmlContentBefore);
        FileUtils.deleteQuietly(new File(METADATA_BASE_DIR + parentId + "/meta.xml.1"));
    }

    @Test
    public void shouldAddMultipleStructuresWithoutMetadata() throws Exception {
        int testProcessId = MockDatabase.insertTestProcess(TEST_PROCESS_TITLE, 1, 1, 1);
        ProcessTestUtils.copyTestMetadataFile(testProcessId, TEST_METADATA_FILE);
        File metaXmlFile = new File(METADATA_BASE_DIR + testProcessId + META_XML);
        Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metaXmlFile.toURI());

        int oldNrLogicalDivisions = workpiece.getAllLogicalDivisions().size();
        int addedDivisions = 2;
        int newNrDivisions = oldNrLogicalDivisions + addedDivisions;

        MetadataEditor.addMultipleStructures(addedDivisions, "section", workpiece, workpiece.getLogicalStructure(),
            InsertionPosition.FIRST_CHILD_OF_CURRENT_ELEMENT);

        List<LogicalDivision> logicalDivisions = workpiece.getAllLogicalDivisions();
        assertTrue(logicalDivisions.get(newNrDivisions - 1).getMetadata().isEmpty(), "Metadata should be empty");
        ProcessTestUtils.removeTestProcess(testProcessId);
    }

    @Test
    public void shouldAddMultipleStructuresWithMetadataGroup() throws Exception {

        RulesetManagementInterface ruleset = ServiceManager.getRulesetManagementService().getRulesetManagement();
        ruleset.load(new File(TestConstants.TEST_RULESET));
        StructuralElementViewInterface divisionView = ruleset.getStructuralElementView("Monograph", EDIT,
            Locale.LanguageRange.parse("en"));
        String metadataKey = "TitleDocMain";

        int testProcessId = MockDatabase.insertTestProcess(TEST_PROCESS_TITLE, 1, 1, 1);
        ProcessTestUtils.copyTestMetadataFile(testProcessId, TEST_METADATA_FILE);
        File metaXmlFile = new File(METADATA_BASE_DIR + testProcessId + META_XML);
        Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metaXmlFile.toURI());

        int oldNrLogicalDivisions = workpiece.getAllLogicalDivisions().size();
        int addedDivisions = 2;
        int newNrDivisions = oldNrLogicalDivisions + addedDivisions;

        MetadataViewInterface mvi = divisionView.getAllowedMetadata().stream()
                .filter(metaDatum -> metaDatum.getId().equals(metadataKey)).findFirst().orElse(null);

        MetadataEditor.addMultipleStructuresWithMetadata(addedDivisions, "Monograph", workpiece,
            workpiece.getLogicalStructure(), InsertionPosition.FIRST_CHILD_OF_CURRENT_ELEMENT, mvi, "value");
        LogicalDivision newSectionOne = workpiece.getAllLogicalDivisions().get(newNrDivisions - 2);
        List<Metadata> metadataListOne = new ArrayList<>(newSectionOne.getMetadata());
        LogicalDivision newSectionTwo = workpiece.getAllLogicalDivisions().get(newNrDivisions - 1);
        List<Metadata> metadataListTwo = new ArrayList<>(newSectionTwo.getMetadata());
        Metadata metadatumOne = metadataListOne.get(0);
        Metadata metadatumTwo = metadataListTwo.get(0);

        assertInstanceOf(MetadataEntry.class, metadatumOne, "Metadata should be of type MetadataEntry");
        assertTrue(((MetadataEntry) metadatumOne).getValue().equals("value 1")
                && ((MetadataEntry) metadatumTwo).getValue().equals("value 2"), "Metadata value was incorrectly added");
        ProcessTestUtils.removeTestProcess(testProcessId);
    }

    @Test
    public void shouldAddMultipleStructuresWithMetadataEntry() throws Exception {
        RulesetManagementInterface ruleset = ServiceManager.getRulesetManagementService().getRulesetManagement();
        ruleset.load(new File(TestConstants.TEST_RULESET));
        StructuralElementViewInterface divisionView = ruleset.getStructuralElementView("Monograph", EDIT,
            Locale.LanguageRange.parse("en"));
        String metadataKey = "Person";

        int testProcessId = MockDatabase.insertTestProcess(TEST_PROCESS_TITLE, 1, 1, 1);
        ProcessTestUtils.copyTestMetadataFile(testProcessId, TEST_METADATA_FILE);
        File metaXmlFile = new File(METADATA_BASE_DIR + testProcessId + META_XML);
        Workpiece workpiece = ServiceManager.getMetsService().loadWorkpiece(metaXmlFile.toURI());

        int oldNrLogicalDivisions = workpiece.getAllLogicalDivisions().size();
        int addedDivisions = 2;
        int newNrDivisions = oldNrLogicalDivisions + addedDivisions;

        MetadataViewInterface mvi = divisionView.getAllowedMetadata().stream()
                .filter(metaDatum -> metaDatum.getId().equals(metadataKey)).findFirst().orElse(null);

        MetadataEditor.addMultipleStructuresWithMetadata(addedDivisions, "Monograph", workpiece,
            workpiece.getLogicalStructure(), InsertionPosition.FIRST_CHILD_OF_CURRENT_ELEMENT, mvi, "value");
        LogicalDivision newSectionOne = workpiece.getAllLogicalDivisions().get(newNrDivisions - 2);
        List<Metadata> metadataListOne = new ArrayList<>(newSectionOne.getMetadata());
        LogicalDivision newSectionTwo = workpiece.getAllLogicalDivisions().get(newNrDivisions - 1);
        List<Metadata> metadataListTwo = new ArrayList<>(newSectionTwo.getMetadata());
        Metadata metadatumOne = metadataListOne.get(0);
        Metadata metadatumTwo = metadataListTwo.get(0);

        assertTrue(metadatumOne instanceof MetadataGroup && metadatumTwo instanceof MetadataGroup, "Metadata should be of type MetadataGroup");
        assertTrue(metadatumOne.getKey().equals("Person") && metadatumTwo.getKey().equals("Person"), "Metadata value was incorrectly added");
        ProcessTestUtils.removeTestProcess(testProcessId);
    }

    private boolean isInternalMetsLink(String lineOfMets, int recordNumber) {
        // Order of <mptr> attributes varies
        return lineOfMets.contains("mptr ") && lineOfMets.contains("LOCTYPE=\"OTHER\"")
                && lineOfMets.contains("OTHERLOCTYPE=\"Kitodo.Production\"")
                && lineOfMets.contains("href=\"database://?process.id=" + recordNumber + "\"");
    }
}
