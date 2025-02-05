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

package org.kitodo.production.services.workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.kitodo.ExecutionPermission;
import org.kitodo.MockDatabase;
import org.kitodo.SecurityTestUtils;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.data.database.beans.Comment;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.WorkflowCondition;
import org.kitodo.data.database.enums.CommentType;
import org.kitodo.data.database.enums.TaskEditType;
import org.kitodo.data.database.enums.TaskStatus;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.TaskService;
import org.kitodo.production.services.file.FileService;
import org.kitodo.test.utils.ProcessTestUtils;

public class WorkflowControllerServiceIT {

    private static final File scriptCreateDirUserHome = new File(
            ConfigCore.getParameter(ParameterCore.SCRIPT_CREATE_DIR_USER_HOME));
    private static final File scriptCreateSymLink = new File(
            ConfigCore.getParameter(ParameterCore.SCRIPT_CREATE_SYMLINK));
    private static final File scriptNotWorking = new File("src/test/resources/scripts/not_working_script.sh");
    private static final File scriptWorking = new File("src/test/resources/scripts/working_script.sh");
    private static final File usersDirectory = new File("src/test/resources/users");
    private static final FileService fileService = ServiceManager.getFileService();
    private static final TaskService taskService = ServiceManager.getTaskService();
    private static final WorkflowControllerService workflowService = new WorkflowControllerService();
    private static int workflowTestProcessId = -1;
    private static int workflowTestProcessId2 = -1;
    private static final String METADATA_TEST_FILENAME = "testMetadataForNonBlockingParallelTasksTest.xml";

    @Before
    public void prepareDatabase() throws Exception {
        MockDatabase.startNode();
        MockDatabase.insertProcessesForWorkflowFull();
        workflowTestProcessId = Math.toIntExact(ServiceManager.getProcessService().count());
        ProcessTestUtils.copyTestFiles(workflowTestProcessId, "testmetaNewspaper.xml");
        SecurityTestUtils.addUserDataToSecurityContext(ServiceManager.getUserService().getById(1), 1);
        Task task = ServiceManager.getTaskService().getById(35);
        workflowTestProcessId2 = task.getProcess().getId();
        ProcessTestUtils.copyTestMetadataFile(workflowTestProcessId2, METADATA_TEST_FILENAME);

        usersDirectory.mkdir();

        if (!SystemUtils.IS_OS_WINDOWS) {
            ExecutionPermission.setExecutePermission(scriptCreateDirUserHome);
            ExecutionPermission.setExecutePermission(scriptCreateSymLink);
            ExecutionPermission.setExecutePermission(scriptNotWorking);
            ExecutionPermission.setExecutePermission(scriptWorking);
        }
    }

    @After
    public void cleanDatabase() throws Exception {
        ProcessTestUtils.removeTestProcess(workflowTestProcessId);
        ProcessTestUtils.removeTestProcess(workflowTestProcessId2);
        MockDatabase.stopNode();
        MockDatabase.cleanDatabase();
        SecurityTestUtils.cleanSecurityContext();

        if (!SystemUtils.IS_OS_WINDOWS) {
            ExecutionPermission.setNoExecutePermission(scriptCreateDirUserHome);
            ExecutionPermission.setNoExecutePermission(scriptCreateSymLink);
            ExecutionPermission.setNoExecutePermission(scriptNotWorking);
            ExecutionPermission.setNoExecutePermission(scriptWorking);
        }

        usersDirectory.delete();
    }

    @Test
    public void shouldSetTaskStatusUp() throws Exception {
        Task task = taskService.getById(10);

        workflowService.setTaskStatusUp(task);
        assertEquals("Task '" + task.getTitle() + "' status was not set up!", TaskStatus.OPEN,
            task.getProcessingStatus());

        workflowService.setTaskStatusDown(task);
        taskService.save(task);
    }

    @Test
    public void shouldSetTasksStatusUp() throws Exception {
        Process process = ServiceManager.getProcessService().getById(1);
        List<Task> tasks = process.getTasks();
        assertEquals("Task '" + tasks.get(3).getTitle() + "' status should be OPEN!", TaskStatus.OPEN,
                tasks.get(3).getProcessingStatus());
        assertEquals("Task '" + tasks.get(2).getTitle() + "' status should be INWORK!", TaskStatus.INWORK,
                tasks.get(2).getProcessingStatus());

        workflowService.setTasksStatusUp(process);
        for (Task task : process.getTasks()) {
            if (Objects.equals(task.getId(), 9)) {
                assertEquals("Task '" + task.getTitle() + "' status was not set up!", TaskStatus.INWORK,
                    task.getProcessingStatus());
            } else if (Objects.equals(task.getId(), 10)) {
                assertEquals("Task '" + task.getTitle() + "' status should not be set up!", TaskStatus.LOCKED,
                    task.getProcessingStatus());
            } else {
                assertEquals("Task '" + task.getTitle() + "' status was not set up!", TaskStatus.DONE,
                    task.getProcessingStatus());
            }
        }
    }

    @Test
    public void shouldSetTasksStatusDown() throws Exception {
        Process process = ServiceManager.getProcessService().getById(1);
        //Due to testszenario there are multiple current tasks, so task with id 2 is set down twice (inwork->open->locked)
        List<Task> tasks = process.getTasks();
        assertEquals("Task '" + tasks.get(3).getTitle() + "' status should be OPEN!", TaskStatus.OPEN,
            tasks.get(3).getProcessingStatus());
        assertEquals("Task '" + tasks.get(2).getTitle() + "' status should be INWORK!", TaskStatus.INWORK,
            tasks.get(2).getProcessingStatus());

        workflowService.setTasksStatusDown(process);
        tasks = process.getTasks();
        assertEquals("Task '" + tasks.get(3).getTitle() + "' status was not set down!", TaskStatus.LOCKED,
            tasks.get(3).getProcessingStatus());
        assertEquals("Task '" + tasks.get(2).getTitle() + "' status was not set down!", TaskStatus.LOCKED,
            tasks.get(2).getProcessingStatus());
    }

    @Test
    public void shouldClose() throws Exception {
        Task task = taskService.getById(9);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        Task nextTask = taskService.getById(10);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());
    }

    @Test
    public void shouldCloseForProcessWithParallelTasks() throws Exception {
        Task task = taskService.getById(19);
        ProcessTestUtils.copyTestMetadataFile(task.getProcess().getId(), ProcessTestUtils.testFileChildProcessToKeep);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        // Task 2 and 4 are set up to open because they are concurrent and conditions
        // were evaluated to true
        Task nextTask = taskService.getById(20);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());

        // Task 3 has XPath which evaluates to false - it gets immediately closed
        nextTask = taskService.getById(21);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to done!", TaskStatus.DONE,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(22);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(23);
        assertEquals("Task '" + nextTask.getTitle() + "' was set up to open!", TaskStatus.LOCKED,
            nextTask.getProcessingStatus());

        ProcessTestUtils.removeTestProcess(task.getProcess().getId());
    }

    @Test
    public void shouldCloseForInWorkProcessWithParallelTasks() throws Exception {
        Task task = taskService.getById(25);
        ProcessTestUtils.copyTestMetadataFile(task.getProcess().getId(), ProcessTestUtils.testFileChildProcessToKeep);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        // Task 3 has XPath which evaluates to false - it gets immediately closed
        Task nextTask = taskService.getById(26);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to done!", TaskStatus.DONE,
            nextTask.getProcessingStatus());

        // Task 3 and 4 are concurrent - 3 got immediately finished, 4 is set to open
        nextTask = taskService.getById(27);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(28);
        assertEquals("Task '" + nextTask.getTitle() + "' was set up to open!", TaskStatus.LOCKED,
            nextTask.getProcessingStatus());
        ProcessTestUtils.removeTestProcess(task.getProcess().getId());
    }

    @Test
    public void shouldCloseForInWorkProcessWithBlockingParallelTasks() throws Exception {
        Task task = taskService.getById(30);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        Task nextTask = taskService.getById(31);
        assertEquals("Task '" + nextTask.getTitle() + "' is not in work!", TaskStatus.INWORK,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(32);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set to locked!", TaskStatus.LOCKED,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(33);
        assertEquals("Task '" + nextTask.getTitle() + "' was set up to open!", TaskStatus.LOCKED,
            nextTask.getProcessingStatus());
    }

    @Test
    public void shouldCloseForInWorkProcessWithNonBlockingParallelTasks() throws Exception {
        Task task = taskService.getById(35);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        Task nextTask = taskService.getById(36);
        assertEquals("Task '" + nextTask.getTitle() + "' is not in work!", TaskStatus.INWORK,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(37);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(38);
        assertEquals("Task '" + nextTask.getTitle() + "' was set up to open!", TaskStatus.LOCKED,
            nextTask.getProcessingStatus());
    }

    @Test
    public void shouldCloseForAlmostFinishedProcessWithParallelTasks() throws Exception {
        Task task = taskService.getById(42);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        Task nextTask = taskService.getById(43);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());
    }

    //TODO: find out why it doesn't work in github ci
    @Ignore("Doesn't work on gitHub ci")
    @Test
    public void shouldCloseAndAssignNextForProcessWithParallelTasks() throws Exception {
        Task task = taskService.getById(44);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        // Task 2 and 4 are set up to open because they are concurrent and conditions
        // were evaluated to true
        Task nextTask = taskService.getById(45);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
                nextTask.getProcessingStatus());

        // Task 3 has XPath which evaluates to false - it gets immediately closed
        nextTask = taskService.getById(46);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to done!", TaskStatus.DONE,
                nextTask.getProcessingStatus());

        nextTask = taskService.getById(47);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
                nextTask.getProcessingStatus());

        nextTask = taskService.getById(48);
        assertEquals("Task '" + nextTask.getTitle() + "' was set up to open!", TaskStatus.LOCKED,
                nextTask.getProcessingStatus());

        fileService.createDirectory(URI.create("9"), "images");

        workflowService.assignTaskToUser(taskService.getById(45));

        fileService.delete(URI.create("9/images"));

        // Task 4 should be kept open
        Task nextConcurrentTask = taskService.getById(47);
        assertEquals("Task '" + nextConcurrentTask.getTitle() + "' was not kept to open!", TaskStatus.OPEN,
                nextConcurrentTask.getProcessingStatus());
    }

    @Test
    public void shouldCloseForProcessWithScriptParallelTasks() throws Exception {
        assumeTrue(!SystemUtils.IS_OS_WINDOWS);
        // if you want to execute test on windows change sh to bat in
        // gateway-test5.bpmn20.xml

        Task task = taskService.getById(54);

        workflowService.close(task);
        assertEquals("Task '" + task.getTitle() + "' was not closed!", TaskStatus.DONE, task.getProcessingStatus());

        // Task 2 and 4 are set up to open because they are concurrent and conditions
        // were evaluated to true
        Task nextTask = taskService.getById(55);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());

        // Task 3 has Script which evaluates to false - it gets immediately closed
        nextTask = taskService.getById(56);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to done!", TaskStatus.DONE,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(57);
        assertEquals("Task '" + nextTask.getTitle() + "' was not set up to open!", TaskStatus.OPEN,
            nextTask.getProcessingStatus());

        nextTask = taskService.getById(58);
        assertEquals("Task '" + nextTask.getTitle() + "' was set up to open!", TaskStatus.LOCKED,
            nextTask.getProcessingStatus());
    }

    @Test
    public void shouldCloseForProcessWithSkippedTask() throws DAOException, IOException {
        int processId = MockDatabase.insertTestProcess("Test process", 1, 1, 1);
        Process process = ServiceManager.getProcessService().getById(processId);
        process.getTasks().clear();
        ProcessTestUtils.copyTestMetadataFile(processId, ProcessTestUtils.testFileForHierarchyParent);
        WorkflowCondition workflowCondition = new WorkflowCondition("xpath", "/mets:nothing");
        ServiceManager.getWorkflowConditionService().save(workflowCondition);
        Task taskToClose =  createAndSaveTask(TaskStatus.INWORK, 1, process, null);
        Task skippedTask = createAndSaveTask(TaskStatus.LOCKED, 2, process, workflowCondition);
        Task secondSkippedTask = createAndSaveTask(TaskStatus.LOCKED, 2, process, workflowCondition);
        Task thirdTaskToSkip = createAndSaveTask(TaskStatus.LOCKED, 3, process, workflowCondition);
        Task taskToOpen = createAndSaveTask(TaskStatus.LOCKED, 3, process, null);
        Task secondTaskToOpen = createAndSaveTask(TaskStatus.LOCKED, 3, process, null);

        List<Task> tasks = process.getTasks();
        tasks.add(taskToClose);
        tasks.add(skippedTask);
        tasks.add(secondSkippedTask);
        tasks.add(thirdTaskToSkip);
        tasks.add(taskToOpen);
        tasks.add(secondTaskToOpen);

        ServiceManager.getProcessService().save(process);

        workflowService.close(taskToClose);

        assertEquals("Task '" + taskToClose.getTitle() + "' was not closed!", TaskStatus.DONE,
            taskToClose.getProcessingStatus());
        taskService.refresh(skippedTask);
        assertEquals("Task '" + skippedTask.getTitle() + "' was not skipped!", TaskStatus.DONE,
                skippedTask.getProcessingStatus());
        taskService.refresh(secondSkippedTask);
        assertEquals("Task '" + secondSkippedTask.getTitle() + "' was not skipped!", TaskStatus.DONE,
                secondSkippedTask.getProcessingStatus());
        taskService.refresh(taskToOpen);
        assertEquals("Task '" + taskToOpen.getTitle() + "' was not opened!", TaskStatus.OPEN,
                taskToOpen.getProcessingStatus());
        taskService.refresh(secondTaskToOpen);
        assertEquals("Task '" + secondTaskToOpen.getTitle() + "' was not opened!", TaskStatus.OPEN,
                secondTaskToOpen.getProcessingStatus());
        taskService.refresh(thirdTaskToSkip);
        assertEquals("Task '" + thirdTaskToSkip.getTitle() + "' was not skipped!", TaskStatus.DONE,
                thirdTaskToSkip.getProcessingStatus());

        process.getTasks().clear();
        ProcessTestUtils.removeTestProcess(processId);
    }

    private Task createAndSaveTask(TaskStatus taskStatus, int ordering, Process process,
            WorkflowCondition workflowCondition) throws DAOException {
        Task task = new Task();
        task.setProcessingStatus(taskStatus);
        task.setOrdering(ordering);
        task.setProcess(process);
        task.setWorkflowCondition(workflowCondition);
        taskService.save(task);
        return task;
    }

    @Test
    public void shouldAssignTaskToUser() throws Exception {
        fileService.createDirectory(URI.create(""), "1");
        fileService.createDirectory(URI.create("1"), "images");

        Task task = taskService.getById(6);

        workflowService.assignTaskToUser(task);
        assertEquals("Incorrect user was assigned to the task!", Integer.valueOf(1), task.getProcessingUser().getId());

        fileService.delete(URI.create("1/images"));
        fileService.delete(URI.create("1"));
    }

    @Test
    public void shouldUnassignTaskFromUser() throws Exception {
        Task task = taskService.getById(6);

        workflowService.unassignTaskFromUser(task);
        assertNull("User was not unassigned from the task!", task.getProcessingUser());
        assertEquals("Task was not set up to open after unassing of the user!", TaskStatus.OPEN,
            task.getProcessingStatus());
    }

    @Test
    public void shouldReportProblem() throws Exception {
        Task currentTask = taskService.getById(8);
        Task correctionTask = taskService.getById(6);

        Comment problem = new Comment();
        problem.setMessage("Fix it!");
        problem.setAuthor(ServiceManager.getUserService().getById(1));
        problem.setCurrentTask(currentTask);
        problem.setCorrectionTask(correctionTask);
        problem.setProcess(currentTask.getProcess());
        problem.setType(CommentType.ERROR);
        problem.setCorrected(Boolean.FALSE);
        problem.setCreationDate(new Date());

        ServiceManager.getCommentService().save(problem);

        workflowService.reportProblem(problem, TaskEditType.MANUAL_SINGLE);

        assertEquals(
            "Report of problem was incorrect - task '" + correctionTask.getTitle() + "' is not set up to open!",
            TaskStatus.OPEN, correctionTask.getProcessingStatus());

        assertTrue(
            "Report of problem was incorrect - task '" + correctionTask.getTitle() + "' is not a correction task!",
            correctionTask.isCorrection());

        Process process = problem.getCurrentTask().getProcess();
        for (Task task : process.getTasks()) {
            if (correctionTask.getOrdering() < task.getOrdering() && task.getOrdering() < currentTask.getOrdering()) {
                assertEquals("Report of problem was incorrect - tasks between were not set up to locked!",
                    TaskStatus.LOCKED, task.getProcessingStatus());
            }
        }
    }

    @Test
    public void shouldSolveProblem() throws Exception {
        Task currentTask = taskService.getById(8);
        Task correctionTask = taskService.getById(6);

        Comment correctionComment = new Comment();
        correctionComment.setMessage("Fix it!");
        correctionComment.setAuthor(ServiceManager.getUserService().getById(1));
        correctionComment.setCurrentTask(currentTask);
        correctionComment.setCorrectionTask(correctionTask);
        correctionComment.setProcess(currentTask.getProcess());
        correctionComment.setType(CommentType.ERROR);
        correctionComment.setCorrected(Boolean.FALSE);
        correctionComment.setCreationDate(new Date());

        ServiceManager.getCommentService().save(correctionComment);

        workflowService.reportProblem(correctionComment, TaskEditType.MANUAL_SINGLE);
        ServiceManager.getCommentService().refresh(correctionComment);
        workflowService.solveProblem(correctionComment, TaskEditType.MANUAL_SINGLE);

        Process process = ServiceManager.getProcessService().getById(currentTask.getProcess().getId());
        for (Task task : process.getTasks()) {
            if (correctionComment.getCorrectionTask().getOrdering() < task.getOrdering()
                    && task.getOrdering() < correctionComment.getCurrentTask().getOrdering()) {
                assertEquals("Solving reported problem was unsuccessful - tasks between '"
                                + correctionTask.getTitle() + "' and '" + currentTask.getTitle()
                                + "' were not set to processing status DONE!", TaskStatus.DONE,
                    task.getProcessingStatus());
            }
        }

        assertEquals("Solving reported problem was unsuccessful - correction task '" + correctionTask.getTitle()
                        + "' was not set to processing status DONE!", TaskStatus.DONE, correctionComment.getCorrectionTask().getProcessingStatus());

        assertEquals("Solving reported problem was unsuccessful - current task '" + currentTask.getTitle()
                + "' was not set to processing status 'OPEN'!", TaskStatus.OPEN, correctionComment.getCurrentTask().getProcessingStatus());
    }
}
