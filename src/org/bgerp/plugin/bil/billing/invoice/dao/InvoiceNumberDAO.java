package org.bgerp.plugin.bil.billing.invoice.dao;

import java.sql.Connection;
import java.sql.SQLException;

import org.bgerp.plugin.bil.billing.invoice.model.Invoice;

import ru.bgcrm.dao.CommonDAO;
import ru.bgcrm.util.sql.PreparedDelay;

/**
 * Builder DAO for retrieving next invoice counter number.
 * At the end of uniqueness selection functions must be called {@link #get()},
 * performing the SQL query and returning required value.
 * Used from scripts.
 *
 * @author Shamil Vakhitov
 */
public class InvoiceNumberDAO extends CommonDAO {
    private final Invoice invoice;
    private final PreparedDelay pd;

    public InvoiceNumberDAO(Connection con, Invoice invoice) {
        super(con);
        this.invoice = invoice;
        this.pd = new PreparedDelay(con, SQL_SELECT + "MAX(number_cnt)" + SQL_FROM + Tables.TABLE_INVOICE + SQL_WHERE + "1>0");
    }

    /**
     * Selects for the current process.
     * @return
     */
    public InvoiceNumberDAO process() {
        pd.addQuery(SQL_AND + "process_id=?");
        pd.addInt(invoice.getProcessId());
        return this;
    }

    /**
     * Selects for the current month.
     * @return
     */
    public InvoiceNumberDAO month() {
        pd.addQuery(SQL_AND + "date_from=?");
        pd.addDate(invoice.getDateFrom());
        return this;
    }

    /**
     * Selects the next counter value.
     * Terminating function.
     * @return
     * @throws SQLException
     */
    public int next() throws SQLException {
        int cnt = 0;

        try (pd) {
            var rs = pd.executeQuery();
            if (rs.next())
                cnt = rs.getInt(1);
        }

        return cnt + 1;
    }
}
