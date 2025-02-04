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

package org.kitodo.production.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.kitodo.constants.StringConstants.CREATE;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kitodo.MockDatabase;
import org.kitodo.SecurityTestUtils;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.production.forms.createprocess.ProcessFieldedMetadata;
import org.kitodo.production.process.TitleGenerator;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.test.utils.ProcessTestUtils;

public class ProcessHelperIT {

    public static final String DOCTYPE = "Monograph";
    private static final String TEST_PROCESS_TITLE = "Second process";
    private static List<Locale.LanguageRange> priorityList;
    private static int processHelperTestProcessId = -1;
    private static final String metadataTestfile = "testMetadataForKitodoScript.xml";

    /**
     * Function to run before test is executed.
     *
     * @throws Exception
     *         the exception when set up test
     */
    @BeforeAll
    public static void setUp() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesFull();
        MockDatabase.setUpAwaitility();
        User userOne = ServiceManager.getUserService().getById(1);
        SecurityTestUtils.addUserDataToSecurityContext(userOne, 1);
        priorityList = ServiceManager.getUserService().getCurrentMetadataLanguage();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
    }

    @BeforeEach
    public void prepareTestProcess() throws DAOException, DataException, IOException {
        processHelperTestProcessId = MockDatabase.insertTestProcess(TEST_PROCESS_TITLE, 1, 1, 1);
        ProcessTestUtils.copyTestMetadataFile(processHelperTestProcessId, metadataTestfile);
    }

    @AfterEach
    public void removeTestProcess() throws DAOException {
        ProcessTestUtils.removeTestProcess(processHelperTestProcessId);
        processHelperTestProcessId = -1;
    }

    /**
     * Tests the ProcessHelper functions of generating the atstsl fields.
     *
     * @throws DAOException
     *         the database exception.
     * @throws ProcessGenerationException
     *         the exception when process is generated.
     * @throws IOException
     *         the exception when ruleset is opened.
     */
    @Test
    public void generateAtstslFields() throws DAOException, ProcessGenerationException, IOException {
        Process process = new Process();
        process.setProject(ServiceManager.getProjectService().getById(1));
        process.setRuleset(ServiceManager.getRulesetService().getById(1));
        TempProcess tempProcess = new TempProcess(process, new Workpiece());
        RulesetManagementInterface rulesetManagement = ServiceManager.getRulesetService()
                .openRuleset(tempProcess.getProcess().getRuleset());

        testGenerationOfAtstslByCurrentTempProcess(tempProcess, rulesetManagement);

        testForceRegenerationOfAtstsl(tempProcess, rulesetManagement);

        testForceRegenerationByTempProcessParents(tempProcess, rulesetManagement);

        testForceRegenerationByParentProcess(tempProcess, rulesetManagement);
    }

    private void testForceRegenerationByParentProcess(TempProcess tempProcess,
            RulesetManagementInterface rulesetManagement) throws ProcessGenerationException, DAOException {
        ProcessHelper.generateAtstslFields(tempProcess, tempProcess.getProcessMetadata().getProcessDetailsElements(),
                null, DOCTYPE, rulesetManagement, CREATE, priorityList,
                ServiceManager.getProcessService().getById(processHelperTestProcessId), true);
        assertEquals("Secopr", tempProcess.getAtstsl());
    }

    private void testForceRegenerationByTempProcessParents(TempProcess tempProcess,
            RulesetManagementInterface rulesetManagement) throws DAOException, ProcessGenerationException {
        TempProcess tempProcessParent = new TempProcess(ServiceManager.getProcessService().getById(processHelperTestProcessId),
                new Workpiece());
        tempProcess.getProcessMetadata().setProcessDetails(new ProcessFieldedMetadata() {
            {
                treeNode.getChildren()
                        .add(ProcessTestUtils.getTreeNode(TitleGenerator.TSL_ATS, TitleGenerator.TSL_ATS, ""));
            }
        });
        ProcessHelper.generateAtstslFields(tempProcess, tempProcess.getProcessMetadata().getProcessDetailsElements(),
                Collections.singletonList(tempProcessParent), DOCTYPE, rulesetManagement, CREATE, priorityList, null,
                true);
        assertEquals("Secopr", tempProcess.getAtstsl());
    }

    private void testForceRegenerationOfAtstsl(TempProcess tempProcess,
            RulesetManagementInterface rulesetManagement) throws ProcessGenerationException {
        ProcessHelper.generateAtstslFields(tempProcess, tempProcess.getProcessMetadata().getProcessDetailsElements(),
                null, DOCTYPE, rulesetManagement, CREATE, priorityList, null, false);
        assertEquals("test", tempProcess.getAtstsl());
        ProcessHelper.generateAtstslFields(tempProcess, tempProcess.getProcessMetadata().getProcessDetailsElements(),
                null, DOCTYPE, rulesetManagement, CREATE, priorityList, null, true);
        assertEquals("test2", tempProcess.getAtstsl());
    }

    private void testGenerationOfAtstslByCurrentTempProcess(TempProcess tempProcess,
            RulesetManagementInterface rulesetManagement) throws ProcessGenerationException {
        tempProcess.getProcessMetadata().setProcessDetails(new ProcessFieldedMetadata() {
            {
                treeNode.getChildren()
                        .add(ProcessTestUtils.getTreeNode(TitleGenerator.TSL_ATS, TitleGenerator.TSL_ATS, "test"));
            }
        });
        assertNull(tempProcess.getAtstsl());
        ProcessHelper.generateAtstslFields(tempProcess, tempProcess.getProcessMetadata().getProcessDetailsElements(),
                null, DOCTYPE, rulesetManagement, CREATE, priorityList, null, false);
        assertEquals("test", tempProcess.getAtstsl());
        tempProcess.getProcessMetadata().setProcessDetails(new ProcessFieldedMetadata() {
            {
                treeNode.getChildren()
                        .add(ProcessTestUtils.getTreeNode(TitleGenerator.TSL_ATS, TitleGenerator.TSL_ATS, "test2"));
            }
        });
    }

}
