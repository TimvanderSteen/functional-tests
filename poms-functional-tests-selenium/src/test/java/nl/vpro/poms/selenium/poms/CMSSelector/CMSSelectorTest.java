package nl.vpro.poms.selenium.poms.CMSSelector;

import nl.vpro.poms.selenium.pages.CMSMediaSelector;
import nl.vpro.poms.selenium.pages.MediaItemPage;
import nl.vpro.poms.selenium.pages.Search;
import nl.vpro.poms.selenium.poms.AbstractTest;

import nl.vpro.poms.selenium.util.WebDriverFactory;
import org.junit.Test;
import org.openqa.selenium.By;

import javax.annotation.Nonnull;

import static org.assertj.core.api.Assertions.assertThat;

public class CMSSelectorTest extends AbstractTest {


    public CMSSelectorTest(@Nonnull WebDriverFactory.Browser browser) {
        super(browser);
    }

    @Test
    public void SPOMSCMSSELECTOR1(){
        login().speciaalNPOGebruiker();
        logout();

        CMSMediaSelector cms = new CMSMediaSelector(driver);
        cms.openUrlCmsMediaSelector();
        cms.clickButtonSelect();
        cms.switchToPomsWindows();
        cms.checkLoginTextBoxes();
        cms.checkIfNotDisplayedTables();
        cms.loginNPOGebruikerMediaSelector();

        Search search = new Search(driver);
        search.addOrRemoveColumn("MID");
        String MID = search.getMidFromColum(1);
        search.clickRow(0);

        cms.switchToCMSWindow();
        assertThat(cms.getResult()).isEqualTo(MID);
    }

    @Test
    public void SPOMSCMSSELECTOR2(){
        Search search = openCmsPopupAddSearch();
        search.selectOptionFromMenu("MediaType","Uitzending");
        search.clickOnColum("Type");
        search.getMultibleRowsAndCheckTextEquals(By.xpath("//td[@class='column-type']/child::*/child::*"), "Uitzending");
        search.removeSelectedOption("Uitzending");

        search.selectOptionFromMenu("Omroepen","TROS");
        search.addOrRemoveColumn("Omroep");
        search.getMultibleRowsAndCheckTextEquals(By.xpath("//td[@class='column-broadcasters']/child::*/child::*"), "TROS");
        search.removeSelectedOption("TROS");
    }

    private Search openCmsPopupAddSearch() {
        CMSMediaSelector cms = new CMSMediaSelector(driver);
        cms.openUrlCmsMediaSelector();
        cms.clickButtonSelect();
        cms.switchToPomsWindows();
        cms.checkLoginTextBoxes();
        cms.checkIfNotDisplayedTables();
        cms.loginNPOGebruikerMediaSelector();

        Search search = new Search(driver);
        return search;
    }


}
