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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.kitodo.data.database.beans.BaseTemplateBean;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.persistence.HibernateUtil;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.BeanQuery;

public class SearchResultGeneration {

    private final SimpleDateFormat dateFormatter = new SimpleDateFormat(BaseTemplateBean.DATE_FORMAT);
    private String filter;
    private boolean showClosedProcesses;
    private boolean showInactiveProjects;
    private static final Logger logger = LogManager.getLogger(SearchResultGeneration.class);

    /**
     * Constructor.
     *
     * @param filter
     *            String
     * @param showClosedProcesses
     *            boolean
     * @param showInactiveProjects
     *            boolean
     */
    public SearchResultGeneration(String filter, boolean showClosedProcesses, boolean showInactiveProjects) {
        this.filter = filter;
        this.showClosedProcesses = showClosedProcesses;
        this.showInactiveProjects = showInactiveProjects;
    }

    /**
     * Get result.
     *
     * @return HSSFWorkbook
     */
    public HSSFWorkbook getResult() {
        return getWorkbook();
    }

    private List<Process> getResultsWithFilter() {
        BeanQuery query = new BeanQuery(Process.class);
        if (StringUtils.isNotBlank(filter)) {
            query.restrictWithUserFilterString(filter);
        }
        if (!this.showClosedProcesses) {
            query.restrictToNotCompletedProcesses();
        }
        if (!this.showInactiveProjects) {
            query.addBooleanRestriction("project.active", Boolean.FALSE);
        }
        query.performIndexSearches();
        return ServiceManager.getProcessService().getByQuery(query.formQueryForAll(), query.getQueryParameters());
    }

    private HSSFWorkbook getWorkbook() {
        HSSFWorkbook workbook = new HSSFWorkbook();
        HSSFSheet sheet = workbook.createSheet("Search results");

        HSSFRow title = sheet.createRow(0);
        title.createCell(0).setCellValue(this.filter);
        for (int i = 1; i < 8; i++) {
            title.createCell(i).setCellValue("");
        }

        setRowHeader(sheet);

        insertRowData(sheet);

        return workbook;
    }

    private void insertRowData(HSSFSheet sheet) {
        int rowCounter = 2;
        List<Process> resultsWithFilter = getResultsWithFilter();
        for (Process process : resultsWithFilter) {
            prepareRow(rowCounter, sheet, process);
            rowCounter++;
        }
    }

    private void setRowHeader(HSSFSheet sheet) {
        HSSFRow rowHeader = sheet.createRow(1);
        rowHeader.createCell(0).setCellValue(Helper.getTranslation("title"));
        rowHeader.createCell(1).setCellValue(Helper.getTranslation("ID"));
        rowHeader.createCell(2).setCellValue(Helper.getTranslation("Datum"));
        rowHeader.createCell(3).setCellValue(Helper.getTranslation("CountImages"));
        rowHeader.createCell(4).setCellValue(Helper.getTranslation("CountStructuralElements"));
        rowHeader.createCell(5).setCellValue(Helper.getTranslation("CountMetadata"));
        rowHeader.createCell(6).setCellValue(Helper.getTranslation("Project"));
        rowHeader.createCell(7).setCellValue(Helper.getTranslation("Status"));
    }

    private void prepareRow(int rowCounter, HSSFSheet sheet, Process process) {
        HSSFRow row = sheet.createRow(rowCounter);
        row.createCell(0).setCellValue(process.getTitle());
        row.createCell(1).setCellValue(process.getId());
        row.createCell(2).setCellValue(dateFormatter.format(process.getCreationDate()));
        row.createCell(3).setCellValue(process.getNumberOfImages());
        row.createCell(4).setCellValue(process.getNumberOfStructures());
        row.createCell(5).setCellValue(process.getNumberOfMetadata());
        row.createCell(6).setCellValue(process.getProject().getTitle());
        row.createCell(7).setCellValue(process.getSortHelperStatus());
    }
}
