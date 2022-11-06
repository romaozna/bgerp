package org.bgerp.plugin.bil.subscription.action;

import static org.bgerp.plugin.bil.invoice.dao.Tables.TABLE_INVOICE;
import static ru.bgcrm.dao.Tables.TABLE_PARAM_LIST;
import static ru.bgcrm.dao.Tables.TABLE_PARAM_LISTCOUNT;
import static ru.bgcrm.dao.Tables.TABLE_PARAM_MONEY;
import static ru.bgcrm.dao.process.Tables.TABLE_PROCESS;
import static ru.bgcrm.dao.process.Tables.TABLE_PROCESS_EXECUTOR;
import static ru.bgcrm.dao.process.Tables.TABLE_PROCESS_LINK;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.struts.action.ActionForward;
import org.bgerp.l10n.Localization;
import org.bgerp.plugin.bil.subscription.Config;
import org.bgerp.plugin.bil.subscription.Plugin;
import org.bgerp.plugin.report.action.ReportActionBase;
import org.bgerp.plugin.report.model.Column;
import org.bgerp.plugin.report.model.Columns;
import org.bgerp.plugin.report.model.Data;
import org.bgerp.util.sql.PreparedQuery;

import javassist.NotFoundException;
import ru.bgcrm.cache.UserCache;
import ru.bgcrm.dao.ParamValueDAO;
import ru.bgcrm.model.customer.Customer;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.servlet.ActionServlet.Action;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.Setup;
import ru.bgcrm.util.TimeUtils;
import ru.bgcrm.util.Utils;
import ru.bgcrm.util.sql.ConnectionSet;

@Action(path = "/user/plugin/report/plugin/subscription/payment")
public class ReportPaymentAction extends ReportActionBase {
    private static final Columns COLUMNS = new Columns(
        new Column.ColumnInteger("process_id", null, "Subscription Process"),
        new Column.ColumnString("customer_title", null, "Customer"),
        new Column.ColumnDecimal("payment_amount", null, "Amount"),
        new Column.ColumnDecimal("service_cost", null, "Service Cost"),
        new Column.ColumnString("service_consultant", null, "Service Consultant"),
        new Column.ColumnDecimal("discount", null, "Discount"),
        new Column.ColumnDecimal("owners_amount", null, "Owners Amount"),
        new Column.ColumnString("product_description", null, "Product Process"),
        new Column.ColumnString("product_owner", null, "Owner"),
        new Column.ColumnDecimal("product_cost", null, "Product Cost")
    );

    @Override
    public ActionForward unspecified(final DynActionForm form, final ConnectionSet conSet) throws Exception {
        return super.unspecified(form, conSet);
    }

    @Override
    public String getTitle() {
        return Localization.getLocalizer(Localization.getSysLang(), Plugin.ID).l("Subscription Payments");
    }

    @Override
    protected String getHref() {
        return "report/subscription/payment";
    }

    @Override
    protected String getJsp() {
        return Plugin.PATH_JSP_USER + "/report/payment.jsp";
    }

    @Override
    public Columns getColumns() {
        return COLUMNS;
    }

    @Override
    protected Selector getSelector() {
        return new Selector() {
            @Override
            protected void select(final ConnectionSet conSet, final Data data) throws Exception {
                final var con = conSet.getSlaveConnection();

                final var form = data.getForm();

                final int userId = form.getUserId();
                final Date date = form.getParamDate("dateFrom");
                if (date == null)
                    return;

                final int subscriptionId = form.getParamInt("subscriptionId", Utils::isPositive);

                final var config = Setup.getSetup().getConfig(Config.class);
                final var subscription = config.getSubscription(subscriptionId);
                if (subscription == null)
                    throw new NotFoundException("Not found subscription with ID: " + subscriptionId);

                BigDecimal incomingTaxPercent = null;
                if (config.getParamUserIncomingTaxPercentId() > 0)
                    incomingTaxPercent = new ParamValueDAO(con).getParamMoney(userId, config.getParamUserIncomingTaxPercentId());

                form.setResponseData("incomingTaxPercent", incomingTaxPercent);

                final String query = SQL_SELECT_COUNT_ROWS +
                    "invoice.amount, invoice.process_id, invoice_customer.object_title, discount.value, service_cost.value, service_consulter.user_id, product.description, product_owner.user_id, product_cost.count" +
                    SQL_FROM +
                    TABLE_INVOICE + "AS invoice " +
                    SQL_LEFT_JOIN + TABLE_PROCESS_LINK + "AS invoice_customer ON invoice.process_id=invoice_customer.process_id AND invoice_customer.object_type=?" +
                    SQL_INNER_JOIN + TABLE_PARAM_LIST + "AS param_subscription ON invoice.process_id=param_subscription.id AND param_subscription.param_id=? AND param_subscription.value=?" +
                    //
                    SQL_INNER_JOIN + TABLE_PARAM_LIST + "AS param_limit ON invoice.process_id=param_limit.id AND param_limit.param_id=?" +
                    SQL_LEFT_JOIN + TABLE_PARAM_MONEY + "AS discount ON invoice.process_id=discount.id AND discount.param_id=?" +
                    SQL_LEFT_JOIN + TABLE_PARAM_MONEY + "AS service_cost ON invoice.process_id=service_cost.id AND service_cost.param_id=?" +
                    SQL_LEFT_JOIN + TABLE_PROCESS_EXECUTOR + "AS service_consulter ON invoice.process_id=service_consulter.process_id" +
                    //
                    SQL_INNER_JOIN + TABLE_PROCESS_LINK + "AS subscription_product ON subscription_product.object_id=invoice.process_id AND subscription_product.object_type=?" +
                    SQL_INNER_JOIN + TABLE_PROCESS + "AS product ON subscription_product.process_id=product.id" +
                    SQL_INNER_JOIN + TABLE_PROCESS_EXECUTOR + "AS product_owner ON product.id=product_owner.process_id" +
                    SQL_INNER_JOIN + TABLE_PARAM_LISTCOUNT + "AS product_cost ON product.id=product_cost.id AND product_cost.param_id=? AND param_limit.value=product_cost.value" +
                    //
                    SQL_WHERE + "invoice.payment_user_id=? AND ?<=invoice.payment_date AND invoice.payment_date<=?" +
                    SQL_ORDER_BY + "invoice.payment_date";

                // key - user ID, value - amount
                final Map<Integer, BigDecimal> userAmounts = new TreeMap<>();
                form.setResponseData("userAmounts", userAmounts);

                // subscription process ID, for that added service costs
                final Set<Integer> serviceCostAddedProcessIds = new TreeSet<>();

                // primary data: payed invoices, amounts, subscription costs
                try (var pq = new PreparedQuery(con, query)) {
                    pq.addString(Customer.OBJECT_TYPE);
                    pq.addInt(config.getParamSubscriptionId());
                    pq.addInt(subscriptionId);

                    pq.addInt(config.getParamLimitId());
                    pq.addInt(config.getParamDiscountId());
                    pq.addInt(config.getParamServiceCostId());

                    pq.addString(Process.LINK_TYPE_DEPEND);
                    pq.addInt(subscription.getParamLimitPriceId());

                    pq.addInt(userId);
                    pq.addDate(date);
                    pq.addDate(TimeUtils.getEndMonth(date));

                    final var rs = pq.executeQuery();
                    while (rs.next()) {
                        final var amount = rs.getBigDecimal("invoice.amount");
                        final int subscriptionProcessId = rs.getInt("invoice.process_id");
                        final var discount = Utils.maskNullDecimal(rs.getBigDecimal("discount.value"));
                        final var serviceCost = Utils.maskNullDecimal(rs.getBigDecimal("service_cost.value"));
                        final int serviceConsulterId = rs.getInt("service_consulter.user_id");
                        final var productCost = Utils.maskNullDecimal(rs.getBigDecimal("product_cost.count"));
                        final int productOwnerId = rs.getInt("product_owner.user_id");

                        final var record = data.addRecord();

                        record.add(subscriptionProcessId);
                        record.add(rs.getString("invoice_customer.object_title"));
                        record.add(amount);
                        record.add(serviceCost);
                        record.add(UserCache.getUser(serviceConsulterId).getTitle());
                        record.add(discount);

                        // add consulter's part
                        if (serviceCostAddedProcessIds.add(subscriptionProcessId)) {
                            var serviceCostPart = userAmounts
                                .computeIfAbsent(productOwnerId, unused -> BigDecimal.ZERO)
                                .add(incomingTax(incomingTaxPercent, serviceCost));
                            userAmounts.put(serviceConsulterId, serviceCostPart);
                        }

                        var ownersAmount = amount.subtract(serviceCost);

                        record.add(ownersAmount);
                        record.add(rs.getString("product.description"));
                        record.add(UserCache.getUser(productOwnerId).getTitle());
                        record.add(productCost);

                        // add owner's part
                        if (productOwnerId != userId) {
                            final var fullCost = ownersAmount.add(discount);

                            var ownerPart = productCost
                                .divide(fullCost, RoundingMode.HALF_UP)
                                .multiply(ownersAmount)
                                .setScale(2, RoundingMode.HALF_UP);

                            ownerPart = userAmounts
                                .computeIfAbsent(productOwnerId, unused -> BigDecimal.ZERO)
                                .add(ownerPart);
                            userAmounts.put(productOwnerId, incomingTax(incomingTaxPercent, ownerPart));
                        }
                    }
                }
            }

            private BigDecimal incomingTax(BigDecimal incomingTaxPercent, BigDecimal value) {
                if (incomingTaxPercent == null)
                    return value;

                return value.multiply(
                    BigDecimal.ONE.subtract(
                        incomingTaxPercent.divide(new BigDecimal("100"))
                    )
                ).setScale(2, RoundingMode.HALF_UP);
            }
        };
    }
}