package org.bgerp.action.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionMapping;
import org.bgerp.action.base.BaseAction;

import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.sql.ConnectionSet;

/**
 * Action method invoker.
 *
 * @author Shamil Vakhitov
 */
public class Invoker {
    public static final Class<?>[] TYPES_CONSET_DYNFORM = { DynActionForm.class, ConnectionSet.class };
    public static final Class<?>[] TYPES_CON_DYNFORM = { DynActionForm.class, Connection.class };

    protected final Method method;

    private Invoker(Method method) {
        this.method = method;
        method.setAccessible(true);
        if (Modifier.isStatic(method.getModifiers()))
            throw new IllegalArgumentException("Action method can't be static");
    }

    public Object invoke(BaseAction action, ActionMapping mapping, DynActionForm actionForm, HttpServletRequest request, HttpServletResponse response,
            ConnectionSet conSet) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        return method.invoke(action, actionForm, conSet);
    }


    private static class InvokerCon extends Invoker {
        public InvokerCon(Method method) {
            super(method);
        }

        @Override
        public Object invoke(BaseAction action, ActionMapping mapping, DynActionForm actionForm, HttpServletRequest request,
                HttpServletResponse response, ConnectionSet conSet)
                throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
            return method.invoke(action, actionForm, conSet.getConnection());
        }
    }

    /**
     * Finds invoker method for a class using different signatures
     * @param clazz the class
     * @param method method name
     * @return not null instance
     * @throws NoSuchMethodException invoker wasn't found.
     */
    public static final Invoker find(Class<?> clazz, String method) throws NoSuchMethodException {
        Invoker result = null;

        try {
            result = new Invoker(clazz.getDeclaredMethod(method, TYPES_CONSET_DYNFORM));
        } catch (Exception e) {}

        if (result == null) {
            try {
                result = new InvokerCon(clazz.getDeclaredMethod(method, TYPES_CON_DYNFORM));
            } catch (Exception e) {}
        }

        if (result == null)
            throw new NoSuchMethodException(method);

        return result;
    }
}