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

package org.kitodo.production;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.jar.Manifest;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.config.ConfigCore;
import org.kitodo.config.enums.ParameterCore;
import org.kitodo.production.helper.tasks.TaskManager;
import org.kitodo.production.interfaces.activemq.ActiveMQDirector;
import org.kitodo.production.security.SecurityUserDetails;
import org.kitodo.production.services.ServiceManager;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Kitodo.Production is the workflow management module of the Kitodo suite. It
 * supports the digitization process of various types of materials.
 */
@WebListener
public class KitodoProduction implements ServletContextListener, HttpSessionListener {
    private static final Logger logger = LogManager.getLogger(KitodoProduction.class);
    private static CompletableFuture<KitodoProduction> instance = new CompletableFuture<>();
    private ServletContext context;
    private Optional<Manifest> manifest;
    private KitodoVersion version = new KitodoVersion();
    private ActiveMQDirector activeMQDirector;

    /* This is the main() function. The class is instantiated by the servlet
     * container, which then calls this function. */
    @Override
    public void contextInitialized(ServletContextEvent event) {
        context = event.getServletContext();
        manifest = retrieveManifestFileAsStream(context);
        manifest.ifPresent(version::setupFromManifest);
        instance.complete(this);
        startActiveMQ();
    }

    private static final Optional<Manifest> retrieveManifestFileAsStream(ServletContext context) {
        try (InputStream manifestResource = context.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (Objects.nonNull(manifestResource)) {
                return Optional.of(new Manifest(manifestResource));
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            context.log(e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Start up the active MQ connection.
     */
    private void startActiveMQ() {
        if (ConfigCore.getOptionalString(ParameterCore.ACTIVE_MQ_HOST_URL).isPresent()) {
            activeMQDirector = new ActiveMQDirector();
            Thread connectAsynchronously = new Thread(activeMQDirector);
            connectAsynchronously.setName(ActiveMQDirector.class.getSimpleName());
            connectAsynchronously.setDaemon(true);
            connectAsynchronously.start();
        }
    }

    /**
     * Returns the application’s main class.
     * 
     * @return the main class
     * @throws UndeclaredThrowableException
     *             if the servlet container is shut down before the web
     *             application is started
     */
    public static KitodoProduction getInstance() {
        try {
            return instance.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.fatal(e);
            throw new UndeclaredThrowableException(e);
        }
    }

    /**
     * Returns the servlet context.
     * 
     * @return the servlet context
     */
    public ServletContext getServletContext() {
        return context;
    }

    /**
     * Returns the manifest. If there is one. Otherwise, it can also be empty.
     * 
     * @return the manifest
     */
    public Optional<Manifest> getManifest() {
        return manifest;
    }

    /**
     * Returns application version information.
     * 
     * @return version information
     */
    public KitodoVersion getVersionInformation() {
        return version;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        TaskManager.shutdownNow();
        if (Objects.nonNull(activeMQDirector)) {
            activeMQDirector.shutDown();
        }
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        // nothing is done here
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        Object securityContextObject = se.getSession().getAttribute("SPRING_SECURITY_CONTEXT");
        if (securityContextObject instanceof SecurityContextImpl) {
            SecurityContextImpl securityContext = (SecurityContextImpl) securityContextObject;
            Object principal = securityContext.getAuthentication().getPrincipal();
            if (principal instanceof SecurityUserDetails) {
                ServiceManager.getSessionService().expireSessionsOfUser((SecurityUserDetails) principal);
            }
        }
    }
}
