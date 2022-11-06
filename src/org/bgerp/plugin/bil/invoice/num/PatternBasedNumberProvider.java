package org.bgerp.plugin.bil.invoice.num;

import java.sql.Connection;
import java.text.DecimalFormat;

import org.apache.commons.lang3.StringUtils;
import org.bgerp.plugin.bil.invoice.dao.InvoiceNumberDAO;
import org.bgerp.plugin.bil.invoice.model.Invoice;
import org.bgerp.plugin.bil.invoice.model.InvoiceType;
import org.bgerp.util.Log;

import ru.bgcrm.util.ParameterMap;
import ru.bgcrm.util.PatternFormatter;
import ru.bgcrm.util.TimeUtils;
import ru.bgcrm.util.Utils;

public class PatternBasedNumberProvider extends NumberProvider {
    private static final Log log = Log.getLog();

    private final String pattern;

    protected PatternBasedNumberProvider(ParameterMap config) {
        super(null);
        pattern = config.get("pattern", "");
    }

    @Override
    public void number(Connection con, InvoiceType type, Invoice invoice) throws Exception {
        var cnt = new InvoiceNumberDAO(con, invoice);

        var number = PatternFormatter.processPattern(pattern, var -> {
            try {
                if (var.startsWith("process_id")) {
                    var format = StringUtils.substringAfter(var, ":");
                    if (Utils.notBlankString(format))
                        return new DecimalFormat(format).format(invoice.getProcessId());
                    return String.valueOf(invoice.getProcessId());
                }

                if (var.startsWith("date_from")) {
                    var format = StringUtils.substringAfter(var, ":");
                    if (Utils.notBlankString(format))
                        return TimeUtils.format(invoice.getDateFrom(), format);
                    return TimeUtils.format(invoice.getDateFrom(), "ymd");
                }

                if (var.startsWith("number_in_month_for_process")) {
                    invoice.setNumberCnt(cnt.month().process().next());

                    var format = StringUtils.substringAfter(var, ":");
                    if (Utils.notBlankString(format))
                        return new DecimalFormat(format).format(invoice.getNumberCnt());
                    return String.valueOf(invoice.getNumberCnt());
                }
            } catch (Exception e) {
                log.error(e);
            }

            return "";
        });

        invoice.setNumber(number);
    }

}