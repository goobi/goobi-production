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

package org.kitodo.production.helper.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.production.helper.tasks.EmptyTask.Behaviour;

/**
 * The class TaskManager serves to handle the execution of threads. It can be
 * user controlled by the “Long running task manager”, backed by
 * {@link org.kitodo.production.forms.TaskManagerForm}.
 */
public class TaskManager {
    private static final Logger logger = LogManager.getLogger(TaskManager.class);

    /**
     * The field singletonInstance holds the singleton instance of the
     * TaskManager. Tough the method signatures of TaskManager are static, it is
     * implemented as singleton internally. All accesses to the instance must be
     * done by calling the synchronized function singleton() or concurrency
     * issues may arise.
     */
    private static TaskManager singletonInstance;

    /**
     * The field taskSitter holds a scheduled executor to repeatedly run the
     * TaskSitter task which will remove old threads and start new ones as
     * configured to do.
     */
    private final ScheduledExecutorService taskSitter;

    /**
     * The field taskList holds the list of threads managed by the task manager.
     */
    final LinkedList<EmptyTask> taskList = new LinkedList<>();

    /**
     * TaskManager is a singleton so its constructor is private. It will be
     * called once and just once by the synchronized function singleton() and
     * set up a housekeeping thread.
     */
    private TaskManager() {
        taskSitter = Executors.newSingleThreadScheduledExecutor();
        long delay = ConfigCore.getLongParameterOrDefaultValue(ParameterCore.TASK_MANAGER_INSPECTION_INTERVAL_MILLIS);
        taskSitter.scheduleWithFixedDelay(new TaskSitter(), delay, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds a task thread to the task list.
     *
     * @param task
     *            task to add
     */
    public static void addTask(EmptyTask task) {
        LinkedList<EmptyTask> tasks = singleton().taskList;
        synchronized (tasks) {
            tasks.addLast(task);
        }
    }

    /**
     * The procedure will add a task to the task list if it
     * has not yet been added right after the last task that is currently
     * executing. If this fails for some reason (i.e. the list got concurrently
     * modified) it will be added in the end.
     *
     * <p>
     * This is a fallback method that is called by the overloaded start() method
     * of AbstractTask. Do not use it. Use TaskManager.addTask() to properly add
     * the tasks you created.
     *
     * @param task
     *            task to add
     */
    static void addTaskIfMissing(EmptyTask task) {
        LinkedList<EmptyTask> tasks = singleton().taskList;
        synchronized (tasks) {
            if (!tasks.contains(task)) {
                int pos = lastIndexOf(TaskState.WORKING) + 1;
                try {
                    tasks.add(pos, task);
                } catch (IndexOutOfBoundsException e) {
                    logger.catching(e);
                    tasks.addLast(task);
                }
            }
        }
    }

    /**
     * Returns a copy of the task list usable for
     * displaying. The result object cannot be used to modify the list. Use
     * removeAllFinishedTasks() to clean up the list or stopAndDeleteAllTasks()
     * if you wish to do so. To get rid of one specific task, call
     * abstractTask.interrupt(Behaviour.DELETE_IMMEDIATELY) which will cause it
     * to be removed by the TaskSitter as soon as it has terminated
     * successfully.
     *
     * @return a copy of the task list
     */
    public static List<EmptyTask> getTaskList() {
        LinkedList<EmptyTask> tasks = singleton().taskList;
        synchronized (tasks) {
            return new ArrayList<>(tasks);
        }
    }

    /**
     * Returns the index of the last task in the task
     * list that is in the given TaskState.
     *
     * @param state
     *            state of tasks to look for
     * @return the index of the last task in that state
     */
    private static int lastIndexOf(TaskState state) {
        int lastIndex = -1;
        int pos = -1;
        LinkedList<EmptyTask> tasks = singleton().taskList;
        synchronized (tasks) {
            for (EmptyTask task : tasks) {
                pos++;
                if (task.getTaskState().equals(state)) {
                    lastIndex = pos;
                }
            }
        }
        return lastIndex;
    }

    /**
     * Can be called to remove all
     * terminated threads from the list.
     */
    public static void removeAllFinishedTasks() {
        boolean redo;
        do {
            redo = false;
            try {
                LinkedList<EmptyTask> tasks = singleton().taskList;
                synchronized (tasks) {
                    tasks.removeIf(emptyTask -> emptyTask.getState().equals(Thread.State.TERMINATED));
                }
            } catch (ConcurrentModificationException listModifiedByAnotherThreadWhileIterating) {
                logger.catching(listModifiedByAnotherThreadWhileIterating);
                redo = true;
            }
        } while (redo);
    }

    /**
     * Can be called to move a task by one forwards on
     * the queue.
     *
     * @param task
     *            task to move forwards
     */
    public static void runEarlier(EmptyTask task) {
        LinkedList<EmptyTask> tasks = singleton().taskList;
        synchronized (tasks) {
            int index = tasks.indexOf(task);
            if (index > 0) {
                Collections.swap(tasks, index - 1, index);
            }
        }
    }

    /**
     * Can be called to move a task by one backwards on
     * the queue.
     *
     * @param task
     *            task to move backwards
     */
    public static void runLater(EmptyTask task) {
        LinkedList<EmptyTask> tasks = singleton().taskList;
        synchronized (tasks) {
            int index = tasks.indexOf(task);
            if (index > -1 && index + 1 < tasks.size()) {
                Collections.swap(tasks, index, index + 1);
            }
        }
    }

    /**
     * The synchronized function singleton() must be used to obtain singleton
     * access to the TaskManager instance.
     *
     * @return the singleton TaskManager instance
     */
    static synchronized TaskManager singleton() {
        if (Objects.isNull(singletonInstance)) {
            singletonInstance = new TaskManager();
        }
        return singletonInstance;
    }

    /**
     * The function will be called by the TaskSitter to gracefully
     * exit the task manager as well as its managed threads during container
     * shutdown.
     */
    public static void shutdownNow() {
        stopAndDeleteAllTasks();
        singleton().taskSitter.shutdownNow();
    }

    /**
     * Can be called to both request
     * interrupt and immediate deletion for all threads that are alive and at
     * the same time remove all threads that aren’t alive anyhow.
     */
    public static void stopAndDeleteAllTasks() {
        boolean redo;
        do {
            redo = false;
            try {
                LinkedList<EmptyTask> tasks = singleton().taskList;
                synchronized (tasks) {
                    Iterator<EmptyTask> inspector = tasks.iterator();
                    while (inspector.hasNext()) {
                        EmptyTask task = inspector.next();
                        if (task.isAlive()) {
                            task.interrupt(Behaviour.DELETE_IMMEDIATELY);
                        } else {
                            inspector.remove();
                        }
                    }
                }
            } catch (ConcurrentModificationException listModifiedByAnotherThreadWhileIterating) {
                logger.catching(listModifiedByAnotherThreadWhileIterating);
                redo = true;
            }
        } while (redo);
    }
}
