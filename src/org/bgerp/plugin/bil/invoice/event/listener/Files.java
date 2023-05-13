package org.bgerp.plugin.bil.invoice.event.listener;

import org.bgerp.app.l10n.Localization;
import org.bgerp.event.ProcessFileGetEvent;
import org.bgerp.event.ProcessFilesEvent;
import org.bgerp.model.Pageable;
import org.bgerp.plugin.bil.invoice.Plugin;
import org.bgerp.plugin.bil.invoice.action.InvoiceAction;
import org.bgerp.plugin.bil.invoice.dao.InvoiceSearchDAO;
import org.bgerp.plugin.bil.invoice.model.Invoice;

import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.model.IdStringTitle;
import ru.bgcrm.servlet.CustomHttpServletResponse;
import ru.bgcrm.util.Utils;

/**
 * Provides files with print forms for adding to messages.
 *
 * @author Shamil Vakhitov
 */
public class Files {
    private static final String PREFIX = Plugin.ID + ":";

    public Files() {
        EventProcessor.subscribe((e, conSet) -> {
            var result = new Pageable<Invoice>();
            new InvoiceSearchDAO(conSet.getSlaveConnection())
                .withProcessId(e.getProcessId())
                .withPaid(false)
                .orderFromDate()
                .orderDesc()
                .search(result);

            var l = Localization.getLocalizer(Plugin.ID, e.getForm().getHttpRequest());

            for (var invoice : result.getList()) {
                e.addAnnouncedFile(new IdStringTitle(PREFIX + invoice.getId(), l.l("Счёт") + " " + invoice.getNumber()));
            }
        }, ProcessFilesEvent.class);

        EventProcessor.subscribe((e, conSet) -> {
            if (!e.getFileId().startsWith(PREFIX))
                return;

            int id = Utils.parseInt(e.getFileId().substring(PREFIX.length()));

            var form = e.getForm();

            var type = InvoiceAction.doc(conSet, form, id);
            Invoice invoice = (Invoice) form.getResponse().getData().get("invoice");

            byte[] data = CustomHttpServletResponse.jsp(form, type.getJsp(), 50000);

            e.setFile(invoice.getNumber() + ".html", data);
        }, ProcessFileGetEvent.class);
    }
}
