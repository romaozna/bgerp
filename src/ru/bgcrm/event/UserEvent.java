package ru.bgcrm.event;

import org.bgerp.app.event.iface.Event;

import ru.bgcrm.model.user.User;
import ru.bgcrm.struts.form.DynActionForm;

/**
 * Use {@link org.bgerp.event.base.UserEvent} instead.
 */
@Deprecated
public class UserEvent implements Event {
    protected final DynActionForm form;

    public UserEvent(DynActionForm form) {
        this.form = form;
    }

    public DynActionForm getForm() {
        return form;
    }

    public User getUser() {
        return form.getUser();
    }
}
