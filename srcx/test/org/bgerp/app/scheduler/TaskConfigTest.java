package org.bgerp.app.scheduler;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.bgerp.task.MessageExchange;
import org.junit.Assert;
import org.junit.Test;

import ru.bgcrm.util.ParameterMap;

public class TaskConfigTest {
    @Test
    public void testCheckTime() throws Exception {
        var config = new TaskConfig("test", ParameterMap.of(
            "class", MessageExchange.class.getSimpleName(),
            "dw", "1,3,7"
        ));
        Assert.assertTrue(config.checkTime(ZonedDateTime.of(2023, 5, 22, 0, 0, 0, 0, ZoneId.systemDefault())));
        Assert.assertFalse(config.checkTime(ZonedDateTime.of(2023, 5, 23, 0, 0, 0, 0, ZoneId.systemDefault())));
        Assert.assertTrue(config.checkTime(ZonedDateTime.of(2023, 5, 24, 0, 0, 0, 0, ZoneId.systemDefault())));
        Assert.assertFalse(config.checkTime(ZonedDateTime.of(2023, 5, 25, 0, 0, 0, 0, ZoneId.systemDefault())));
        Assert.assertTrue(config.checkTime(ZonedDateTime.of(2023, 5, 28, 0, 0, 0, 0, ZoneId.systemDefault())));

        config = new TaskConfig("test", ParameterMap.of(
            "class", MessageExchange.class.getSimpleName(),
            "dm", "1,3,7"
        ));
        Assert.assertTrue(config.checkTime(ZonedDateTime.of(2023, 5, 1, 0, 0, 0, 0, ZoneId.systemDefault())));
        Assert.assertFalse(config.checkTime(ZonedDateTime.of(2023, 5, 2, 0, 0, 0, 0, ZoneId.systemDefault())));
    }
}
