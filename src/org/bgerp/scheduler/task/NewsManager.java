package org.bgerp.scheduler.task;

import static ru.bgcrm.dao.user.Tables.TABLE_USER;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bgerp.scheduler.Scheduler;
import org.bgerp.scheduler.Task;
import org.bgerp.util.Log;

import ru.bgcrm.cache.UserNewsCache;
import ru.bgcrm.dao.Tables;
import ru.bgcrm.util.Setup;
import ru.bgcrm.util.sql.SQLUtils;

public class NewsManager extends Task {
    private static final Log log = Log.getLog();

    private static final AtomicBoolean RUN = new AtomicBoolean(false);

    public NewsManager() {
        super(null);
    }

    @Override
    public void run() {
        if (RUN.get()) {
            log.info("Task already working..");
            return;
        }

        long time = System.currentTimeMillis();
        Connection con = Setup.getSetup().getDBConnectionFromPool();

        synchronized (RUN) {
            RUN.set(true);

            try {
                //Update news: Mark as Read
                String query = " UPDATE " + Tables.TABLE_NEWS_USER + " u " + " INNER JOIN " + Tables.TABLE_NEWS + " n ON u.news_id=n.id SET u.is_read=1 "
                        + " WHERE DATE_ADD(n.create_dt, INTERVAL n.read_time HOUR) < NOW() AND u.is_read=0 ";
                PreparedStatement ps = con.prepareStatement(query);
                ps.executeUpdate();
                ps.close();
                con.commit();

                //Delete news: Removal old news
                query = " DELETE FROM " + Tables.TABLE_NEWS + " WHERE DATE_ADD(create_dt, INTERVAL life_time DAY) < NOW() ";
                ps = con.prepareStatement(query);
                ps.executeUpdate();
                ps.close();
                con.commit();

                //Delete news: deleting news-user links where news points to non-existent
                query = " DELETE u.* FROM " + Tables.TABLE_NEWS_USER + " u " + " LEFT JOIN " + Tables.TABLE_NEWS + " n ON u.news_id=n.id "
                        + " WHERE n.id IS NULL ";
                ps = con.prepareStatement(query);
                ps.executeUpdate();
                ps.close();
                con.commit();

                //Delete news: removing news-user links where the user points to a non-existent or deleted
                query = " DELETE nu.* FROM " + Tables.TABLE_NEWS_USER + " nu " + " LEFT JOIN " + TABLE_USER + " u ON nu.user_id=u.id "
                        + " WHERE u.id IS NULL OR u.deleted=1 ";

                ps = con.prepareStatement(query);
                ps.executeUpdate();
                ps.close();
                con.commit();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            } finally {
                UserNewsCache.flush(con);
                SQLUtils.closeConnection(con);
                RUN.set(false);
                Scheduler.logExecutingTime( this, time );
            }
        }
    }
}
