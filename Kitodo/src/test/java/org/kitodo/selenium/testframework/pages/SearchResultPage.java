package org.kitodo.selenium.testframework.pages;

import static org.kitodo.selenium.testframework.Browser.getDriver;
import static org.kitodo.selenium.testframework.Browser.getRowsOfTable;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class SearchResultPage extends Page<SearchResultPage> {

    private static final String SEARCH_RESULT_TABLE = "searchResultTable";

    private static final String SEARCH_RESULT_FORM = "searchResultForm";

    @SuppressWarnings("unused")
    @FindBy(id = SEARCH_RESULT_FORM + ":" + SEARCH_RESULT_TABLE + "_data")
    private WebElement searchResultTable;

    @SuppressWarnings("unused")
    @FindBy(id = SEARCH_RESULT_FORM + ":filterProjects")
    private WebElement filterProjects;

    public SearchResultPage() {
        super("searchResult.jsf");
    }

    @Override
    public SearchResultPage goTo() throws Exception {
        return null;
    }

    public int getNumberOfResults() {
        return getRowsOfTable(searchResultTable).size();
    }

    public String getProjectsForFilter(){
        return filterProjects.getText();
    }

    public void filterByProject(){
        WebElement projectFilter = getDriver().findElementByPartialLinkText("First");
        projectFilter.click();
    }
}
