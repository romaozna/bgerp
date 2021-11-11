package ru.bgcrm.event.listener;

import org.apache.log4j.Logger;

import ru.bgcrm.dao.CustomerDAO;
import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.event.ParamChangedEvent;
import ru.bgcrm.model.BGException;
import ru.bgcrm.model.customer.Customer;
import ru.bgcrm.model.param.Parameter;
import ru.bgcrm.util.sql.ConnectionSet;

public class CustomerSystemListener {
    private static final Logger log = Logger.getLogger(CustomerSystemListener.class);

    public CustomerSystemListener() {
        EventProcessor.subscribe(new EventListener<ParamChangedEvent>() {
            @Override
            public void notify(ParamChangedEvent e, ConnectionSet connectionSet) {
                paramChanged(e, connectionSet);
            }

        }, ParamChangedEvent.class);
    }

    private void paramChanged(ParamChangedEvent e, ConnectionSet connectionSet) {
        Parameter param = e.getParameter();
        if (Customer.OBJECT_TYPE.equals(param.getObject())) {
            try {
                CustomerDAO customerDAO = new CustomerDAO(connectionSet.getConnection());

                Customer customer = customerDAO.getCustomerById(e.getObjectId());
                if (customer == null) {
                    throw new BGException("Customer not found with id: " + e.getObjectId());
                }

                customerDAO.updateCustomerTitle(customer.getTitle(), customer, param.getId(), e.getForm().getResponse());
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }
}
