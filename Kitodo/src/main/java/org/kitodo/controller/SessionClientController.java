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

package org.kitodo.controller;

import de.sub.goobi.helper.Helper;

import java.util.List;
import java.util.Objects;

import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Client;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.services.ServiceManager;
import org.primefaces.context.RequestContext;

@Named("SessionClientController")
@RequestScoped
public class SessionClientController {

    private transient ServiceManager serviceManager = new ServiceManager();
    private static final Logger logger = LogManager.getLogger(SessionClientController.class);

    private Client selectedClient;
    protected static final String NO_CLIENT_SELECTED = "clientSelectNone";

    /**
     * Gets the name of the current session client. In case that no session client
     * has been set, an empty string is returned and a dialog to select a client is
     * shown.
     * 
     * @return The current session clients name or empty string case that no session
     *         client has been set.
     */
    public String getCurrentSessionClientName() {
        if (Objects.nonNull(getCurrentSessionClient())) {
            return getCurrentSessionClient().getName();
        } else {
            if (userIsAdmin()) {
                return Helper.getTranslation(NO_CLIENT_SELECTED);
            }
            if (userHasOnlyOneClient()) {
                Client client = getFirstClientOfCurrentUser();
                setSessionClient(client);
                return client.getName();
            }
            showClientSelectDialog();
            return "";
        }
    }

    private Client getFirstClientOfCurrentUser() {
        return getClientsOfCurrentUser().get(0);
    }

    private boolean userIsAdmin() {
        return serviceManager.getSecurityAccessService().isAdmin();
    }

    private boolean userHasOnlyOneClient() {
        return getClientsOfCurrentUser().size() == 1;
    }

    /**
     * The conditions when user need to select a session client is configured in
     * this method.
     * 
     * @return True if the session client select dialog should by displayed to the
     *         current user
     */
    public boolean shouldUserChangeSessionClient() {

        //No change if user is admin.
        if (userIsAdmin()) {
            return false;
        }

        //No change if we have only one client for selection.
        if (userHasOnlyOneClient()) {
            return false;
        }
        return true;
    }

    private void showClientSelectDialog() {
        RequestContext.getCurrentInstance().execute("PF('selectClientDialog').show();");
    }

    private Client getCurrentSessionClient() {
        return serviceManager.getUserService().getSessionClientOfAuthenticatedUser();
    }

    public void setSelectedClientAsSessionClient() {
        setSessionClient(selectedClient);
    }

    public boolean areClientsAvailableForUser() {
        return !getClientsOfCurrentUser().isEmpty();
    }

    /**
     * Gets selectedClient.
     *
     * @return The selectedClient.
     */
    public Client getSelectedClient() {
        return selectedClient;
    }

    /**
     * Sets selectedClient.
     *
     * @param selectedClient
     *            The selectedClient.
     */
    public void setSelectedClient(Client selectedClient) {
        this.selectedClient = selectedClient;
    }

    /**
     * Sets the given client object as new session client.
     * 
     * @param sessionClient
     *            The client object that is to be the new session client.
     */
    public void setSessionClient(Client sessionClient) {
        serviceManager.getUserService().getAuthenticatedUser().setSessionClient(sessionClient);
    }

    /**
     * Gets all clients of user assigned projects.
     *
     * @return The list of clients.
     */
    public List<Client> getClientsOfCurrentUser() {

        if (serviceManager.getSecurityAccessService().hasAnyAuthorityGlobal()) {
            try {
                return serviceManager.getClientService().getAll();
            } catch (DAOException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(),logger,e);
            }
        }
        return getClientsByUsersAuthorities();
    }

    private List<Client> getClientsByUsersAuthorities() {
        List<Client> clients = serviceManager.getClientService().getByIds(serviceManager.getSecurityAccessService().getClientIdListForAnyAuthority());
        List<Project> projects = serviceManager.getProjectService().getByIds(serviceManager.getSecurityAccessService().getProjectIdListForAnyAuthority());
        for (Project project : projects) {
            if (!clients.contains(project.getClient())) {
                clients.add(project.getClient());
            }
        }
        return clients;
    }
}
