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

package org.kitodo.production.forms.createprocess;

import static org.kitodo.constants.StringConstants.CREATE;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.api.Metadata;
import org.kitodo.api.dataeditor.rulesetmanagement.RulesetManagementInterface;
import org.kitodo.data.database.beans.ImportConfiguration;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.exceptions.InvalidMetadataValueException;
import org.kitodo.exceptions.NoSuchMetadataFieldException;
import org.kitodo.exceptions.ProcessGenerationException;
import org.kitodo.production.helper.Helper;
import org.kitodo.production.helper.ProcessHelper;
import org.kitodo.production.helper.TempProcess;
import org.kitodo.production.services.ServiceManager;
import org.omnifaces.util.Ajax;
import org.primefaces.PrimeFaces;

public abstract class MetadataImportDialog {

    private static final Logger logger = LogManager.getLogger(MetadataImportDialog.class);
    public final CreateProcessForm createProcessForm;

    static final String FORM_CLIENTID = "editForm";
    static final String GROWL_MESSAGE =
            "PF('notifications').renderMessage({'summary':'SUMMARY','detail':'DETAIL','severity':'SEVERITY'});";
    List<ImportConfiguration> importConfigurations = null;

    /**
     * Standard constructor.
     *
     * @param createProcessForm
     *         CreateProcessForm instance to which this ImportDialog is assigned.
     */
    MetadataImportDialog(CreateProcessForm createProcessForm) {
        this.createProcessForm = createProcessForm;
        this.createProcessForm.numberOfEadElements = 0;
        this.createProcessForm.setXmlString("");
        this.createProcessForm.setFilename("");
    }

    void attachToExistingParentAndGenerateAtstslIfNotExist(TempProcess tempProcess)
            throws ProcessGenerationException, IOException, InvalidMetadataValueException,
            NoSuchMetadataFieldException {
        // if fewer processes are imported than configured in the frontend, it can mean that
        // - the OPAC does not have as many processes in the hierarchy or
        // - one process of the hierarchy was already in the DB and import ended at this point
        int numberOfProcesses = this.createProcessForm.getProcesses().size();

        if (numberOfProcesses < 1) {
            Helper.setErrorMessage("Error: list of processes is empty!");
            return;
        }

        TempProcess parentTempProcess = ServiceManager.getImportService().getParentTempProcess();
        if (numberOfProcesses == 1 && Objects.nonNull(parentTempProcess)) {
            // case 1: only one process was imported => load DB parent into "TitleRecordLinkTab"
            this.createProcessForm.getTitleRecordLinkTab().setParentAsTitleRecord(parentTempProcess.getProcess());
        } else {
            // case 2: multiple processes imported and one ancestor found in DB => add ancestor to list
            if (Objects.nonNull(parentTempProcess)) {
                this.createProcessForm.getProcesses().add(parentTempProcess);
            }
        }

        if (StringUtils.isBlank(tempProcess.getAtstsl())) {
            if (Objects.nonNull(parentTempProcess)) {
                ProcessHelper.generateAtstslFields(tempProcess, Collections.singletonList(parentTempProcess), CREATE,
                        true);
            } else {
                ProcessHelper.generateAtstslFields(tempProcess, null, CREATE, true);
            }
        }
    }

    /**
     * Show growl message.
     *
     * @param summary General message
     * @param detail Message detail
     */
    public void showGrowlMessage(String summary, String detail) {
        String script = GROWL_MESSAGE.replace("SUMMARY", summary).replace("DETAIL", detail)
                .replace("SEVERITY", "info");
        PrimeFaces.current().executeScript(script);
    }

    /**
     * Get list of catalogs.
     *
     * @return list of catalogs
     */
    public List<ImportConfiguration> getImportConfigurations() {
        if (Objects.isNull(importConfigurations)) {
            try {
                importConfigurations = ServiceManager.getImportConfigurationService().getAll();
            } catch (IllegalArgumentException | DAOException e) {
                Helper.setErrorMessage(e.getLocalizedMessage(), logger, e);
                importConfigurations = new LinkedList<>();
            }
        }
        return importConfigurations;
    }

    /**
     * Add not existing metadata fields to metadata table with metadata values of
     * first process in given list "processes" on successful import.
     *
     * @param processes
     *            The linked list of TempProcess instances
     */
    void extendsMetadataTableOfMetadataTab(LinkedList<TempProcess> processes)
            throws InvalidMetadataValueException, NoSuchMetadataFieldException, IOException {

        int countOfAddedMetadata = 0;
        if (!processes.isEmpty()) {
            TempProcess process = processes.getFirst();
            if (process.getMetadataNodes().getLength() > 0) {
                if (createProcessForm.getProcessDataTab().getDocType()
                        .equals(process.getWorkpiece().getLogicalStructure().getType())) {
                    RulesetManagementInterface rulesetManagementInterface = ServiceManager.getRulesetService()
                            .openRuleset(process.getProcess().getRuleset());
                    Collection<Metadata> metadata = ProcessHelper
                            .convertMetadata(process.getMetadataNodes(), rulesetManagementInterface);
                    countOfAddedMetadata = createProcessForm.getProcessMetadata().getProcessDetails()
                            .addMetadataIfNotExists(metadata);
                } else {
                    Helper.setWarnMessage(Helper.getTranslation("errorAdditionalImport"));
                }
            }
        }
        Ajax.update(FORM_CLIENTID);
        String summary = Helper
                .getTranslation("newProcess.catalogueSearch.additionalImportSuccessfulSummary");
        String detail = Helper
                .getTranslation("newProcess.catalogueSearch.additionalImportSuccessfulDetail",
                        String.valueOf(countOfAddedMetadata));
        showGrowlMessage(summary, detail);
    }
}
