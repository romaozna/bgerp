package org.bgerp.itest.kernel.param;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bgerp.itest.kernel.db.DbTest;
import org.bgerp.model.Pageable;
import org.bgerp.util.sql.LikePattern;
import org.testng.Assert;
import org.testng.annotations.Test;

import ru.bgcrm.dao.AddressDAO;
import ru.bgcrm.model.param.address.AddressCity;
import ru.bgcrm.model.param.address.AddressCountry;
import ru.bgcrm.model.param.address.AddressHouse;
import ru.bgcrm.model.param.address.AddressItem;

@Test(groups = "address", dependsOnGroups = "config")
public class AddressTest {
    public static volatile AddressCity cityUfa;

    public static volatile AddressHouse houseMuenchen;
    public static volatile AddressHouse houseUfa7f1;
    public static volatile AddressHouse houseUfa6;

    @Test
    public void config() throws Exception {
        // TODO: Add different format types.
    }

    @Test
    public void directory() throws Exception {
        var dao = new AddressDAO(DbTest.conRoot);

        var country = dao.updateAddressCountry(new AddressCountry().withTitle("Bayern"));
        var city = dao.updateAddressCity(new AddressCity().withCountryId(country.getId()).withTitle("München"));
        var area = dao.updateAddressArea(new AddressItem().withCityId(city.getId()).withTitle("Obermenzing"));
        var street = dao.updateAddressStreet(new AddressItem().withCityId(city.getId()).withTitle("Karl-Marx-Ring"));
        street = dao.updateAddressStreet(new AddressItem().withCityId(city.getId()).withTitle("Dorfstraße"));
        houseMuenchen = dao.updateAddressHouse(new AddressHouse().withStreetId(street.getId()).withAreaId(area.getId())
            .withPostIndex("81247").withHouseAndFrac("99a").withComment("Nette Leute"));
        Assert.assertTrue(houseMuenchen.getId() > 0);

        country = dao.updateAddressCountry(new AddressCountry().withTitle("Башкортостан"));
        cityUfa = dao.updateAddressCity(new AddressCity().withCountryId(country.getId()).withTitle("Уфа"));
        area =  dao.updateAddressArea(new AddressItem().withCityId(cityUfa.getId()).withTitle("Кировский район"));
        var quarter = dao.updateAddressQuarter(new AddressItem().withCityId(cityUfa.getId()).withTitle("33"));
        street = dao.updateAddressStreet(new AddressItem().withCityId(cityUfa.getId()).withTitle("Карла Маркса"));
        street = dao.updateAddressStreet(new AddressItem().withCityId(cityUfa.getId()).withTitle("Габдуллы Амантая"));
        houseUfa7f1 = dao.updateAddressHouse(new AddressHouse().withStreetId(street.getId())
            .withAreaId(area.getId()).withQuarterId(quarter.getId())
            .withPostIndex("450103").withHouseAndFrac("7/1").withComment("Код домофона: 666"));
        houseUfa6 = dao.updateAddressHouse(new AddressHouse().withStreetId(street.getId())
            .withAreaId(area.getId()).withQuarterId(quarter.getId())
            .withPostIndex("450103").withHouseAndFrac("6").withComment("Чокнутая консьержка"));
        Assert.assertTrue(houseUfa7f1.getId() > 0 );
    }

    @Test(dependsOnMethods = "directory")
    public void testStreetSearch() throws Exception {
        var dao = new AddressDAO(DbTest.conRoot);

        var result = new Pageable<AddressItem>();
        dao.searchAddressStreetList(result, null, Set.of("ка", "ма").stream().map(LikePattern.SUB::get).collect(Collectors.toList()), false, true);
        Assert.assertEquals(result.getList().size(), 1);
        Assert.assertEquals(result.getList().get(0).getTitle(), "Карла Маркса");

        result = new Pageable<>();
        dao.searchAddressStreetList(result, null, Set.of("уфа", "ма").stream().map(LikePattern.SUB::get).collect(Collectors.toList()), false, true);
        Assert.assertEquals(result.getList().size(), 2);
        Assert.assertEquals(result.getList().get(0).getTitle(), "Габдуллы Амантая");
        Assert.assertEquals(result.getList().get(1).getTitle(), "Карла Маркса");

        result = new Pageable<>();
        dao.searchAddressStreetList(result, Set.of(cityUfa.getId()), List.of(LikePattern.SUB.get("Маркс")), false, true);
        Assert.assertEquals(result.getList().size(), 1);
        Assert.assertEquals(result.getList().get(0).getTitle(), "Карла Маркса");
    }
}
