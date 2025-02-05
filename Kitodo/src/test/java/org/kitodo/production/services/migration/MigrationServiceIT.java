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

package org.kitodo.production.services.migration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.kitodo.MockDatabase;
import org.kitodo.SecurityTestUtils;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.Workflow;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.ProcessService;
import org.kitodo.production.services.data.TaskService;

public class MigrationServiceIT {

    private MigrationService migrationService = ServiceManager.getMigrationService();

    @Before
    public void prepareDatabase() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesFull();
        SecurityTestUtils.addUserDataToSecurityContext(ServiceManager.getUserService().getById(1), 1);
    }

    @After
    public void cleanDatabase() throws Exception {
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
    }

    @Test
    public void testTasksAreEqual() throws DAOException {
        TaskService taskService = ServiceManager.getTaskService();
        List<Task> originalTasks = new ArrayList<>();
        originalTasks.add(taskService.getById(1));
        originalTasks.add(taskService.getById(2));

        List<Task> tasksToCompare = new ArrayList<>();
        Task taskOne = new Task();
        taskOne.setTitle("test");
        tasksToCompare.add(taskOne);

        Assert.assertFalse("Lists should have a different size",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        Task taskTwo = new Task();
        taskTwo.setTitle("testTwo");
        tasksToCompare.add(taskTwo);

        Assert.assertFalse("Tasks should have different Titles",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        tasksToCompare.set(1, null);

        Assert.assertFalse("Null task should fail", migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        Task correctTaskOne = new Task();
        correctTaskOne.setTitle("Finished");
        correctTaskOne.setOrdering(1);

        Task correctTaskTwo = new Task();
        correctTaskTwo.setTitle("Blocking");
        correctTaskTwo.setOrdering(2);

        tasksToCompare.clear();
        tasksToCompare.add(correctTaskOne);
        tasksToCompare.add(correctTaskTwo);

        Assert.assertFalse("scriptPath should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskTwo.setScriptPath("../type/automatic/script/path");

        Assert.assertTrue("Tasks should be equal", migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setBatchStep(false);
        tasksToCompare.clear();
        tasksToCompare.add(correctTaskTwo);
        tasksToCompare.add(correctTaskOne);

        Assert.assertFalse("Tasks are in the wrong order",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

    }

    @Test
    public void testBooleans() throws DAOException {
        TaskService taskService = ServiceManager.getTaskService();
        List<Task> originalTasks = new ArrayList<>();
        originalTasks.add(taskService.getById(1));
        originalTasks.add(taskService.getById(2));

        Task correctTaskOne = new Task();
        correctTaskOne.setTitle("Finished");
        correctTaskOne.setOrdering(1);

        Task correctTaskTwo = new Task();
        correctTaskTwo.setTitle("Blocking");
        correctTaskTwo.setOrdering(2);

        List<Task> tasksToCompare = new ArrayList<>();
        tasksToCompare.add(correctTaskOne);
        tasksToCompare.add(correctTaskTwo);

        correctTaskOne.setTypeMetadata(true);

        Assert.assertFalse("TypeMetadata should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setTypeMetadata(false);
        correctTaskOne.setTypeImagesWrite(true);

        Assert.assertFalse("typeImagesWrite should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setTypeImagesWrite(false);
        correctTaskOne.setTypeImagesRead(true);

        Assert.assertFalse("typeImagesRead should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setTypeImagesRead(false);
        correctTaskOne.setTypeAutomatic(true);

        Assert.assertFalse("typeAutomatic should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setTypeAutomatic(false);
        correctTaskOne.setTypeExportDMS(true);

        Assert.assertFalse("TypeExportDMS should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setTypeExportDMS(false);
        correctTaskOne.setTypeAcceptClose(true);

        Assert.assertFalse("TypeAcceptClose should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setTypeAcceptClose(false);
        correctTaskOne.setTypeCloseVerify(true);

        Assert.assertFalse("TypeCloseVerify should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

        correctTaskOne.setTypeCloseVerify(false);
        correctTaskOne.setBatchStep(true);

        Assert.assertFalse("batchStep should be different",
            migrationService.tasksAreEqual(originalTasks, tasksToCompare));

    }

    @Test
    @Ignore("functionality nowhere used, no longer implemented")
    public void getMatchingTemplatesTest() throws DAOException {
        Template template = new Template();
        template.setDocket(ServiceManager.getDocketService().getById(2));
        template.setRuleset(ServiceManager.getRulesetService().getById(1));
        template.setClient(ServiceManager.getClientService().getById(1));
        template.setWorkflow(ServiceManager.getWorkflowService().getById(1));

        List<Template> existingTemplates = ServiceManager.getTemplateService().getAll();

        HashSet<Template> newTemplates = new HashSet<>();
        newTemplates.add(template);
        Map<Template, Template> matchingTemplates = migrationService.getMatchingTemplates(newTemplates);

        Assert.assertNotNull(matchingTemplates.get(template));
        Assert.assertEquals(existingTemplates.get(0), matchingTemplates.get(template));
    }

    @Test
    public void testAddToTemplate() throws DAOException {
        ProcessService processService = ServiceManager.getProcessService();
        Project project = ServiceManager.getProjectService().getById(1);
        Process firstProcess = new Process();
        firstProcess.setTitle("firstMigrationProcess");
        firstProcess.setProject(project);
        project.getProcesses().add(firstProcess);
        ServiceManager.getProjectService().save(project);
        processService.save(firstProcess);
        Process secondProcess = new Process();
        secondProcess.setTitle("secondMigrationProcess");
        processService.save(secondProcess);

        List<Process> processes = new ArrayList<>();
        processes.add(firstProcess);
        processes.add(secondProcess);

        Template template = new Template();
        template.setTitle("testTemplate");
        ServiceManager.getTemplateService().save(template);
        Assert.assertEquals(0, template.getProcesses().size());
        Assert.assertNull(firstProcess.getTemplate());
        Assert.assertNull(secondProcess.getTemplate());

        migrationService.addProcessesToTemplate(template, processes);

        ServiceManager.getTemplateService().refresh(template);
        Assert.assertEquals(2, template.getProcesses().size());
        Assert.assertEquals(5, (long) firstProcess.getTemplate().getId());
        Assert.assertEquals(5, (long) secondProcess.getTemplate().getId());
    }

    @Test
    public void addProcessesToTemplateTest() throws DAOException {
        Template firstTemplate = ServiceManager.getTemplateService().getById(1);
        Template secondTemplate = ServiceManager.getTemplateService().getById(2);

        List<Process> firstTemplateProcesses = firstTemplate.getProcesses();
        Assert.assertEquals(2, firstTemplateProcesses.size());
        Assert.assertEquals(0, secondTemplate.getProcesses().size());
        Assert.assertEquals(1, (long) firstTemplateProcesses.get(0).getTemplate().getId());
        migrationService.addProcessesToTemplate(secondTemplate, firstTemplateProcesses);

        Assert.assertEquals(2, firstTemplateProcesses.size());
        secondTemplate = ServiceManager.getTemplateService().getById(2);
        Assert.assertEquals(2, secondTemplate.getProcesses().size());
        Assert.assertEquals(2, (long) firstTemplateProcesses.get(0).getTemplate().getId());
    }

    @Test
    public void testCreateTemplatesForProcesses() throws DAOException {
        Workflow workflow = ServiceManager.getWorkflowService().getById(1);
        List<Process> processes = ServiceManager.getProjectService().getById(1).getProcesses();
        Map<Template, List<Process>> templatesForProcesses = migrationService.createTemplatesForProcesses(processes,
            workflow);

        Assert.assertEquals(1, templatesForProcesses.size());
        Assert.assertEquals(processes.get(0).getDocket(), templatesForProcesses.keySet().iterator().next().getDocket());
        Assert.assertEquals(processes.get(0).getRuleset(),
            templatesForProcesses.keySet().iterator().next().getRuleset());
        Assert.assertEquals(2, templatesForProcesses.values().iterator().next().size());

    }

    @Test
    public void testCreateTaskString() throws DAOException {
        Assert.assertEquals("Finished, Closed, Progress, Open, Locked" + MigrationService.SEPARATOR + "9c43055e",
            migrationService.createTaskString(ServiceManager.getProcessService().getById(1).getTasks()));
        List<Task> secondTasks = ServiceManager.getProcessService().getById(2).getTasks();
        Assert.assertEquals("Additional, Processed and Some, Next Open" + MigrationService.SEPARATOR + "848a8483",
            migrationService.createTaskString(secondTasks));
        secondTasks.get(0).setTitle("test/test");
        Assert.assertEquals("test/test, Processed and Some, Next Open" + MigrationService.SEPARATOR + "56f49a2b",
                migrationService.createTaskString(secondTasks));
        Assert.assertEquals(MigrationService.SEPARATOR + "0",
            migrationService.createTaskString(ServiceManager.getProcessService().getById(3).getTasks()));
    }

    @Test
    public void testTitleIsValid() throws DAOException {
        Template newTemplate = new Template();
        newTemplate.setClient(ServiceManager.getClientService().getById(1));
        Assert.assertFalse(migrationService.isTitleValid(newTemplate));
        newTemplate.setTitle("test");
        Assert.assertTrue(migrationService.isTitleValid(newTemplate));
        newTemplate.setTitle("First template");
        Assert.assertFalse(migrationService.isTitleValid(newTemplate));
    }
}
