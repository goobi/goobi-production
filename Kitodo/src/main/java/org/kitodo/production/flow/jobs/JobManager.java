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

package org.kitodo.production.flow.jobs;

import de.sub.kitodo.config.ConfigCore;

import java.util.Calendar;
import java.util.Date;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.Logger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.TriggerUtils;

/**
 * JobManager organizes all scheduled jobs
 * 
 * @author Steffen Hankiewicz
 * @author Igor Toker
 * @version 21.10.2009
 */
public class JobManager implements ServletContextListener {
    private static final Logger logger = Logger.getLogger(JobManager.class);

    /**
     * Restarts timed Jobs.
     */
    public static void restartTimedJobs() throws SchedulerException {
        stopTimedJobs();
        startTimedJobs();
    }

    /**
     * Stops timed updates of HistoryManager.
     */
    private static void stopTimedJobs() throws SchedulerException {
        SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory();
        schedFact.getScheduler().shutdown(false);
    }

    /**
     * Starts timed updates of {@link HistoryAnalyserJob}.
     */
    @SuppressWarnings("deprecation")
    private static void startTimedJobs() throws SchedulerException {
        SchedulerFactory schedFact = new org.quartz.impl.StdSchedulerFactory();
        Scheduler sched = schedFact.getScheduler();
        sched.start();

        initializeJob(new HistoryAnalyserJob(), "dailyHistoryAnalyser", sched);
        initializeJobNonConfigured(new HotfolderJob(), 5, sched);
    }

    /**
     * initializes given SimpleKitodoJob at given time.
     */
    private static void initializeJob(IKitodoJob kitodoJob, String configuredStartTimeProperty, Scheduler sched)
            throws SchedulerException {
        logger.debug(kitodoJob.getJobName());
        JobDetail jobDetail = new JobDetail(kitodoJob.getJobName(), null, kitodoJob.getClass());

        if (ConfigCore.getLongParameter(configuredStartTimeProperty, -1) != -1) {
            long msOfToday = ConfigCore.getLongParameter(configuredStartTimeProperty, -1);
            Calendar cal = Calendar.getInstance();
            cal.set(1984, 8, 11, 0, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            cal.setTime(new Date(cal.getTimeInMillis() + msOfToday));
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int min = cal.get(Calendar.MINUTE);

            Trigger trigger = TriggerUtils.makeDailyTrigger(hour, min);
            trigger.setStartTime(new Date());
            trigger.setName(kitodoJob.getJobName() + "_trigger");

            if (logger.isInfoEnabled()) {
                logger.info("daily Job " + kitodoJob.getJobName() + " start time: " + hour + ":" + min);
            }
            sched.scheduleJob(jobDetail, trigger);
        }
    }

    /**
     * initializes given SimpleKitodoJob at given time.
     */
    private static void initializeJobNonConfigured(IKitodoJob kitodoJob, int myTime, Scheduler sched)
            throws SchedulerException {
        logger.debug(kitodoJob.getJobName());
        JobDetail jobDetail = new JobDetail(kitodoJob.getJobName(), null, kitodoJob.getClass());

        // hier alle 60 sek. oder so
        Trigger trigger = TriggerUtils.makeMinutelyTrigger(myTime);
        trigger.setStartTime(new Date());
        trigger.setName(kitodoJob.getJobName() + "_trigger");
        sched.scheduleJob(jobDetail, trigger);
    }

    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        logger.debug("Stop daily JobManager scheduler");
        try {
            stopTimedJobs();
        } catch (SchedulerException e) {
            logger.error("daily JobManager could not be stopped", e);
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent arg0) {
        logger.debug("Start daily JobManager scheduler");
        try {
            startTimedJobs();
        } catch (SchedulerException e) {
            logger.error("daily JobManager could not be started", e);
        }
    }

}
