package org.bgerp.dao.param;

import static org.bgerp.model.param.Parameter.LIST_PARAM_USE_DIRECTORY_KEY;
import static ru.bgcrm.dao.AddressDAO.LOAD_LEVEL_COUNTRY;
import static ru.bgcrm.dao.Tables.TABLE_FILE_DATA;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.bgerp.app.l10n.Localization;
import org.bgerp.model.base.Id;
import org.bgerp.model.base.IdStringTitle;
import org.bgerp.model.base.IdTitle;
import org.bgerp.model.base.IdTitleComment;
import org.bgerp.model.param.Parameter;
import org.bgerp.util.Log;
import org.bgerp.util.sql.PreparedQuery;

import ru.bgcrm.cache.ParameterCache;
import ru.bgcrm.dao.AddressDAO;
import ru.bgcrm.dao.CommonDAO;
import ru.bgcrm.dao.FileDataDAO;
import ru.bgcrm.model.BGException;
import ru.bgcrm.model.FileData;
import ru.bgcrm.model.customer.Customer;
import ru.bgcrm.model.param.ParameterAddressValue;
import ru.bgcrm.model.param.ParameterEmailValue;
import ru.bgcrm.model.param.ParameterListCountValue;
import ru.bgcrm.model.param.ParameterPhoneValue;
import ru.bgcrm.model.param.ParameterPhoneValueItem;
import ru.bgcrm.model.param.ParameterValuePair;
import ru.bgcrm.model.param.address.AddressHouse;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.model.user.User;
import ru.bgcrm.util.AddressUtils;
import ru.bgcrm.util.TimeUtils;
import ru.bgcrm.util.Utils;

/**
 * Parameter values DAO. The primary required public methods are sorted alphabetically.
 * Dependency methods even public, called by those, are placed directly after the first usage.
 *
 * @author Shamil Vakhitov
 */
public class ParamValueDAO extends CommonDAO {
    private static final Log log = Log.getLog();

    public static final String DIRECTORY_TYPE_PARAMETER = "parameter";

    public static final String[] TABLE_NAMES = {
        Tables.TABLE_PARAM_ADDRESS,
        Tables.TABLE_PARAM_BLOB,
        Tables.TABLE_PARAM_DATE,
        Tables.TABLE_PARAM_DATETIME,
        Tables.TABLE_PARAM_EMAIL,
        Tables.TABLE_PARAM_FILE,
        Tables.TABLE_PARAM_LIST,
        Tables.TABLE_PARAM_LISTCOUNT,
        Tables.TABLE_PARAM_MONEY,
        Tables.TABLE_PARAM_PHONE, Tables.TABLE_PARAM_PHONE_ITEM,
        Tables.TABLE_PARAM_TEXT,
        Tables.TABLE_PARAM_TREE
    };

    /** Write param changes history. */
    private boolean history;
    /** User ID for changes history. */
    private int userId;

    public ParamValueDAO(Connection con) {
        super(con);
    }

    public ParamValueDAO(Connection con, boolean history, int userId) {
        this(con);
        this.history = history;
        this.userId = userId;
    }


    /**
     * Копирует параметр с объекта на объект.
     * @param fromObjectId object ID исходного.
     * @param toObjectId object ID целевого.
     * @param paramId коды параметра.
     * @throws SQLException, BGException
     */
    public void copyParam(int fromObjectId, int toObjectId, int paramId) throws SQLException, BGException {
        copyParam(fromObjectId, paramId, toObjectId, paramId);
    }

    /**
     * Копирует параметр с объекта на объект. Параметры должны быть одного типа.
     * @param fromObjectId object ID исходного.
     * @param fromParamId param ID исходного.
     * @param toObjectId object ID целевого
     * @param toParamId param ID целевого.
     * @throws SQLException
     * @throws BGException
     */
    public void copyParam(int fromObjectId, int fromParamId, int toObjectId, int toParamId) throws SQLException, BGException {
        String query = null;
        ArrayList<PreparedStatement> psList = new ArrayList<PreparedStatement>();

        Parameter paramFrom = ParameterCache.getParameter(fromParamId);
        if (paramFrom == null) {
            throw new BGException("Param not found: " + fromParamId);
        }

        Parameter paramTo = ParameterCache.getParameter(toParamId);
        if (paramTo == null) {
            throw new BGException("Param not found: " + toParamId);
        }

        if (!paramFrom.getType().equals(paramTo.getType())) {
            throw new BGException("Different copy param types.");
        }

        final var paramType = Parameter.Type.of(paramFrom.getType());

        switch (paramType) {
            case ADDRESS -> {
                query = "INSERT INTO " + Tables.TABLE_PARAM_ADDRESS
                        + " (id, param_id, n, house_id, flat, room, pod, floor, value, comment, custom) "
                        + "SELECT ?, ?, n, house_id, flat, room, pod, floor, value, comment, custom " + "FROM "
                        + Tables.TABLE_PARAM_ADDRESS + " WHERE id=? AND param_id=?";
                psList.add(con.prepareStatement(query));
            }
            case EMAIL -> {
                query = "INSERT INTO " + Tables.TABLE_PARAM_EMAIL + " (id, param_id, n, value) " + "SELECT ?, ?, n, value "
                        + "FROM " + Tables.TABLE_PARAM_EMAIL + " WHERE id=? AND param_id=?";
                psList.add(con.prepareStatement(query));
            }
            case LIST -> {
                query = SQL_INSERT_IGNORE + Tables.TABLE_PARAM_LIST + "(id, param_id, value, comment)"
                        + SQL_SELECT + "?, ?, value, comment "
                        + SQL_FROM + Tables.TABLE_PARAM_LIST
                        + SQL_WHERE + "id=? AND param_id=?";
                psList.add(con.prepareStatement(query));
            }
            case LISTCOUNT -> {
                query = SQL_INSERT + Tables.TABLE_PARAM_LISTCOUNT + "(id, param_id, value, count, comment)"
                        + SQL_SELECT + "?, ?, value, count, comment"
                        + SQL_FROM + Tables.TABLE_PARAM_LISTCOUNT + SQL_WHERE + "id=? AND param_id=?";
                psList.add(con.prepareStatement(query));
            }
            case TREE -> {
                query = "INSERT INTO " + Tables.TABLE_PARAM_TREE + "(id, param_id, value) " + "SELECT ?, ?, value " + "FROM "
                        + Tables.TABLE_PARAM_TREE + " WHERE id=? AND param_id=?";
                psList.add(con.prepareStatement(query));
            }
            case DATE, DATETIME, MONEY, TEXT, BLOB, PHONE -> {
                String tableName = "param_" + paramType.toString().toLowerCase();

                query = "INSERT INTO " + tableName + " (id, param_id, value) " + "SELECT ?, ?, value " + "FROM "
                        + tableName + " WHERE id=? AND param_id=?";
                psList.add(con.prepareStatement(query));

                if (Parameter.Type.PHONE == paramType) {
                    query = "INSERT INTO " + Tables.TABLE_PARAM_PHONE_ITEM
                            + " (id, param_id, n, phone, comment) "
                            + "SELECT ?, ?, n, phone, comment" + SQL_FROM + Tables.TABLE_PARAM_PHONE_ITEM
                            + " WHERE id=? AND param_id=?";
                    psList.add(con.prepareStatement(query));
                }
            }
            case FILE -> {
                query = "INSERT INTO " + Tables.TABLE_PARAM_FILE + "(id, param_id, n, value) "
                        + "SELECT ?, ?, n, value FROM " + Tables.TABLE_PARAM_FILE
                        + " WHERE id=? AND param_id=?";
                psList.add(con.prepareStatement(query));
            }
        }

        for (PreparedStatement ps : psList) {
            ps.setInt(1, toObjectId);
            ps.setInt(2, toParamId);
            ps.setInt(3, fromObjectId);
            ps.setInt(4, fromParamId);
            ps.executeUpdate();

            ps.close();
        }
    }

    /**
     * Копирует параметры с объекта на другой объект по указанной конфигурации.
     * @param fromObjectId исходный объект.
     * @param toObjectId целевой объект.
     * @param copyMapping конфигурация.
     * @throws SQLException, BGException
     */
    public void copyParams(int fromObjectId, int toObjectId, String copyMapping) throws SQLException, BGException {
        if (Utils.isBlankString(copyMapping)) {
            return;
        }

        StringTokenizer st = new StringTokenizer(copyMapping, ";,");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();

            String[] pair = token.split(":");
            if (pair.length == 2) {
                copyParam(fromObjectId, Utils.parseInt(pair[0]), toObjectId, Utils.parseInt(pair[1]));
            } else if (Utils.parseInt(token) > 0) {
                int paramId = Utils.parseInt(token);
                copyParam(fromObjectId, paramId, toObjectId, paramId);
            } else {
                log.error("Incorrect copy param mapping: " + token);
            }
        }
    }

    /**
     * Копирует параметры с объекта на объект
     * @param fromObjectId object ID исходного.
     * @param toObjectId object ID целевого.
     * @param paramIds коды параметров.
     * @throws SQLException, BGException
     */
    public void copyParams(int fromObjectId, int toObjectId, Collection<Integer> paramIds) throws SQLException, BGException {
        for (int paramId : paramIds) {
            copyParam(fromObjectId, paramId, toObjectId, paramId);
        }
    }

    /**
     * Удаляет параметры объекта.
     * @param objectType тип объекта.
     * @param id object ID.
     * @throws SQLException
     */
    public void deleteParams(String objectType, int id) throws SQLException {
        String query = SQL_DELETE + "pl" + SQL_FROM + Tables.TABLE_PARAM_LOG + "AS pl"
            + SQL_INNER_JOIN + Tables.TABLE_PARAM_PREF + "AS pref ON pl.param_id=pref.id AND pref.object='" + objectType + "'"
            + SQL_WHERE + "pl.object_id=?";
        try (var pq = new PreparedQuery(con, query)) {
            pq.addInt(id);
            pq.executeUpdate();
        }

        for (String tableName : TABLE_NAMES) {
            query =  SQL_DELETE + "pv" + SQL_FROM + tableName + " AS pv"
                + SQL_INNER_JOIN + Tables.TABLE_PARAM_PREF + "AS pref ON pv.param_id=pref.id AND pref.object=?"
                + SQL_WHERE + "pv.id=?";

            try (var ps = con.prepareStatement(query)) {
                ps.setString(1, objectType);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        }
    }

    /**
     * Возвращает адресный параметр объекта.
     * @param id - код объекта.
     * @param paramId - param ID.
     * @param position - позиция, начиная от 1, если в параметре установлены несколько значений.
     * @return
     * @throws SQLException
     */
    public ParameterAddressValue getParamAddress(int id, int paramId, int position) throws SQLException {
        ParameterAddressValue result = null;

        String query = "SELECT * FROM " + Tables.TABLE_PARAM_ADDRESS + "WHERE id=? AND param_id=? AND n=? " + "LIMIT 1";
        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ps.setInt(3, position);

        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            result = getParameterAddressValueFromRs(rs);
        }
        ps.close();

        return result;
    }

    public static ParameterAddressValue getParameterAddressValueFromRs(ResultSet rs) throws SQLException {
        return getParameterAddressValueFromRs(rs, "");
    }

    public static ParameterAddressValue getParameterAddressValueFromRs(ResultSet rs, String prefix) throws SQLException {
        return getParameterAddressValueFromRs(rs, "", false, null);
    }

    /**
     * Возвращает значения адресного параметра объекта.
     * @param id - код объекта.
     * @param paramId - param ID.
     * @return ключ - позиция, значение - значение на позиции.
     * @throws SQLException
     */
    public SortedMap<Integer, ParameterAddressValue> getParamAddress(int id, int paramId) throws SQLException {
        return getParamAddressExt(id, paramId, false, null);
    }

    /**
     * Возвращает значения адресного параметра объекта.
     * @param id - код объекта.
     * @param paramId - param ID.
     * @param loadDirs - признак необходимости загрузить справочники, чтобы был корректно заполнен {@link ParameterAddressValue#getHouse()}/
     * @return ключ - позиция, значение - значение на позиции.
     * @throws SQLException
     */
    public SortedMap<Integer, ParameterAddressValue> getParamAddressExt(int id, int paramId, boolean loadDirs)
            throws SQLException {
        return getParamAddressExt(id, paramId, loadDirs, null);
    }

    /**
     * Возвращает значения адресного параметра объекта.
     * @param id - код объекта.
     * @param paramId - param ID.
     * @param loadDirs - признак необходимости загрузить справочники, чтобы был корректно заполнен {@link ParameterAddressValue#getHouse()}.
     * @param formatName - наименование формата адреса из конфигурации, с помощью которого форматировать значение адреса.
     * @return ключ - позиция, значение - значение на позиции.
     * @throws SQLException
     */
    public SortedMap<Integer, ParameterAddressValue> getParamAddressExt(int id, int paramId, boolean loadDirs,
            String formatName) throws SQLException {
        SortedMap<Integer, ParameterAddressValue> result = new TreeMap<Integer, ParameterAddressValue>();

        StringBuilder query = new StringBuilder(300);
        query.append("SELECT * FROM " + Tables.TABLE_PARAM_ADDRESS + " AS param ");
        if (loadDirs) {
            query.append(" LEFT JOIN " + Tables.TABLE_ADDRESS_HOUSE + " AS house ON param.house_id=house.id ");
            AddressDAO.addHouseSelectQueryJoins(query, LOAD_LEVEL_COUNTRY);
        }
        query.append(" WHERE param.id=? AND param.param_id=? ORDER BY param.n");

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.put(rs.getInt("n"), getParameterAddressValueFromRs(rs, "param.", loadDirs, formatName));
        }
        ps.close();

        return result;
    }

    public static ParameterAddressValue getParameterAddressValueFromRs(ResultSet rs, String prefix, boolean loadDirs, String formatName)
            throws SQLException {
        ParameterAddressValue result = new ParameterAddressValue();

        result.setHouseId(rs.getInt(prefix + "house_id"));
        result.setFlat(rs.getString(prefix + "flat"));
        result.setRoom(rs.getString(prefix + "room"));
        result.setPod(rs.getInt(prefix + "pod"));
        result.setFloor(rs.getInt(prefix + "floor"));
        result.setValue(rs.getString(prefix + "value"));
        result.setComment(rs.getString(prefix + "comment"));
        result.setCustom(rs.getString(prefix + "custom"));

        if (loadDirs) {
            result.setHouse(AddressDAO.getAddressHouseFromRs(rs, "house.", LOAD_LEVEL_COUNTRY));
            if (Utils.notBlankString(formatName)) {
                result.setValue(AddressUtils.buildAddressValue(result, null, formatName));
            }
        }

        return result;
    }

    /**
     * Selects a value for parameter type 'blob'.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    public String getParamBlob(int id, int paramId) throws SQLException {
        return getTextParam(id, paramId, Tables.TABLE_PARAM_BLOB);
    }

    private String getTextParam(int id, int paramId, String table) throws SQLException {
        String result = null;

        StringBuilder query = new StringBuilder();

        query.append(SQL_SELECT);
        query.append("value");
        query.append(SQL_FROM);
        query.append(table);
        query.append(SQL_WHERE);
        query.append("id=? AND param_id=? LIMIT 1");

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            result = rs.getString(1);
        }
        ps.close();

        return result;
    }

    /**
     * Selects a value for parameter type 'date'.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    public Date getParamDate(int id, int paramId) throws SQLException {
        return getParamDate(id, paramId, Tables.TABLE_PARAM_DATE);
    }

    /**
     * Selects a value for parameter type 'datetime'.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    public Date getParamDateTime(int id, int paramId) throws SQLException {
        return getParamDate(id, paramId, Tables.TABLE_PARAM_DATETIME);
    }

    private Date getParamDate(int id, int paramId, String table) throws SQLException {
        Date result = null;

        String query = "SELECT value FROM " + table + " WHERE id=? AND param_id=? LIMIT 1";

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            result = rs.getTimestamp(1);
        }
        ps.close();

        return result;
    }

    /**
     * Selects a value for parameter type 'email'.
     * @param id object ID.
     * @param paramId param ID.
     * @position param value position.
     * @return
     * @throws SQLException
     */
    public ParameterEmailValue getParamEmail(int id, int paramId, int position) throws SQLException {
        ParameterEmailValue emailItem = null;

        String query = "SELECT * FROM " + Tables.TABLE_PARAM_EMAIL + "WHERE id=? AND param_id=? AND n=? " + "LIMIT 1";

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ps.setInt(3, position);

        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            emailItem = new ParameterEmailValue(rs.getString("value"), rs.getString("comment"));
        }
        ps.close();

        return emailItem;
    }

    /**
     * Selects values for parameter type 'email'.
     * @param id object ID.
     * @param paramId param ID.
     * @return key - param value position, value - a value itself.
     * @throws SQLException
     */
    public SortedMap<Integer, ParameterEmailValue> getParamEmail(int id, int paramId) throws SQLException {
        SortedMap<Integer, ParameterEmailValue> emailItems = new TreeMap<Integer, ParameterEmailValue>();

        String query = "SELECT * FROM " + Tables.TABLE_PARAM_EMAIL + "WHERE id=? AND param_id=? " + "ORDER BY n ";

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            emailItems.put(rs.getInt("n"), new ParameterEmailValue(rs.getString("value"), rs.getString("comment")));
        }
        ps.close();

        return emailItems;
    }

    /**
     * Selects a value for parameter type 'file'.
     * @param id object ID.
     * @param paramId param ID.
     * @param position position number for multiple values.
     * @return
     * @throws SQLException
     */
    public FileData getParamFile(int id, int paramId, int position) throws SQLException {
        FileData result = null;

        String query = "SELECT fd.*, pf.n FROM " + Tables.TABLE_PARAM_FILE + " AS pf "
            + "INNER JOIN " + TABLE_FILE_DATA + " AS fd ON pf.value=fd.id "
            + "WHERE pf.id=? AND pf.param_id=? AND pf.n=? LIMIT 1";

        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ps.setInt(3, position);

        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            result = FileDataDAO.getFromRs(rs, "fd.");
        }
        ps.close();

        return result;
    }

    /**
     * Selects values for parameter type 'file'.
     * @param id object ID.
     * @param paramId param ID.
     * @return map with key equals value's position.
     * @throws SQLException
     */
    public SortedMap<Integer, FileData> getParamFile(int id, int paramId) throws SQLException {
        SortedMap<Integer, FileData> fileMap = new TreeMap<>();

        String query = "SELECT fd.*, pf.n FROM " + Tables.TABLE_PARAM_FILE
            + " AS pf INNER JOIN " + TABLE_FILE_DATA + " AS fd ON pf.value=fd.id "
            + "WHERE pf.id=? AND pf.param_id=? ";

        var ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            fileMap.put(rs.getInt("pf.n"), FileDataDAO.getFromRs(rs, "fd."));
        }
        ps.close();

        return fileMap;
    }

    /**
     * Selects a parameter value with type 'list'.
     * @param id object ID.
     * @param paramId
     * @return Set с кодами значений.
     * @throws SQLException
     */
    public Set<Integer> getParamList(int id, int paramId) throws SQLException {
        Set<Integer> result = new HashSet<Integer>();

        String query = "SELECT value FROM " + Tables.TABLE_PARAM_LIST + "WHERE id=? AND param_id=?";

        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.add(rs.getInt(1));
        }
        ps.close();

        return result;
    }

    /**
     * Selects a parameter value with type 'list' с наименованиями значений.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    public List<IdTitle> getParamListWithTitles(int id, int paramId) throws SQLException {
        List<IdTitleComment> values = getParamListWithTitlesAndComments(id, paramId);
        return new ArrayList<IdTitle>(values);
    }

    /**
     * Selects a parameter value with type 'list' с комментариями значений.
     * @param id object ID.
     * @param paramId param ID.
     * @return ключ - код значения, значение - комментарий.
     * @throws SQLException
     */
    public Map<Integer, String> getParamListWithComments(int id, int paramId) throws SQLException {
        Map<Integer, String> result = new LinkedHashMap<Integer, String>();

        String query = "SELECT value, comment FROM " + Tables.TABLE_PARAM_LIST + "WHERE id=? AND param_id=?";

        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.put(rs.getInt(1), rs.getString(2));
        }
        ps.close();

        return result;
    }

    /**
     * Selects a parameter value with type 'list' с наименованиями значений и примечаниями.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    @Deprecated
    public List<IdTitleComment> getParamListWithTitlesAndComments(int id, int paramId) throws SQLException {
        List<IdTitleComment> result = new ArrayList<IdTitleComment>();

        StringBuilder query = new StringBuilder();

        query.append(SQL_SELECT);
        query.append(" val.value, dir.title, val.comment ");
        query.append(SQL_FROM);
        query.append(Tables.TABLE_PARAM_LIST);
        query.append(" AS val ");
        addListParamJoin(query, paramId);
        query.append(SQL_WHERE);
        query.append(" val.id=? AND val.param_id=? ");

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.add(new IdTitleComment(rs.getInt(1), rs.getString(2), rs.getString(3)));
        }
        ps.close();

        return result;
    }

    private void addListParamJoin(StringBuilder query, int paramId) throws SQLException {
        Parameter param = ParameterCache.getParameter(paramId);
        String joinTable = param.getConfigMap().get(LIST_PARAM_USE_DIRECTORY_KEY, Tables.TABLE_PARAM_LIST_VALUE);
        addListTableJoin(query, joinTable);
    }

    private void addListTableJoin(StringBuilder query, String tableName) {
        query.append(SQL_LEFT_JOIN);
        query.append(tableName);
        query.append(" AS dir ON ");
        if (tableName.equals(Tables.TABLE_PARAM_LIST_VALUE)) {
            query.append(" val.param_id=dir.param_id AND val.value=dir.id ");
        } else {
            query.append(" val.value=dir.id ");
        }
    }

    /**
     * Selects a parameter value with type 'listcount'.
     * @param id object ID.
     * @param paramId param ID.
     * @return ключ - код значения, значение - доп. данные.
     * @throws SQLException
     */
    public Map<Integer, ParameterListCountValue> getParamListCount(int id, int paramId) throws SQLException {
        Map<Integer, ParameterListCountValue> result = new HashMap<Integer, ParameterListCountValue>();

        String query = "SELECT value,count,comment FROM " + Tables.TABLE_PARAM_LISTCOUNT + "WHERE id=? AND param_id=?";

        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.put(rs.getInt(1), new ParameterListCountValue(rs.getBigDecimal(2), rs.getString(3)));
        }
        ps.close();

        return result;
    }

    /**
     * Selects a parameter value with type 'listcount' с наименованиями значений.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    public List<IdTitle> getParamListCountWithTitles(int id, int paramId) throws SQLException {
        List<IdTitle> result = new ArrayList<IdTitle>();

        StringBuilder query = new StringBuilder();

        query.append(SQL_SELECT);
        query.append("val.value, dir.title");
        query.append(SQL_FROM);
        query.append(Tables.TABLE_PARAM_LISTCOUNT);
        query.append("AS val");
        addListCountParamJoin(query, paramId);
        query.append(SQL_WHERE);
        query.append("val.id=? AND val.param_id=?");

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.add(new IdTitle(rs.getInt(1), rs.getString(2)));
        }
        ps.close();

        return result;
    }

    private void addListCountParamJoin(StringBuilder query, int paramId) throws SQLException {
        Parameter param = ParameterCache.getParameter(paramId);
        String joinTable = param.getConfigMap().get(LIST_PARAM_USE_DIRECTORY_KEY, Tables.TABLE_PARAM_LISTCOUNT_VALUE);
        addListCountTableJoin(query, joinTable);
    }

    private void addListCountTableJoin(StringBuilder query, String tableName) {
        query.append(SQL_LEFT_JOIN);
        query.append(tableName);
        query.append(" AS dir ON ");
        if (tableName.equals(Tables.TABLE_PARAM_LISTCOUNT_VALUE)) {
            query.append(" val.param_id=dir.param_id AND val.value=dir.id ");
        } else {
            query.append(" val.value=dir.id ");
        }
    }

    /**
     * Selects a parameter value with type 'money'.
     * @param id object ID.
     * @param paramId param ID.
     * @return the value or {@code null}.
     * @throws SQLException
     */
    public BigDecimal getParamMoney(int id, int paramId) throws SQLException {
        return Utils.parseBigDecimal(getTextParam(id, paramId, Tables.TABLE_PARAM_MONEY), null);
    }

    /**
     * Selects a parameter value with type 'phone'.
     * @param id object ID.
     * @param paramId param ID.
     * @return the value or {@code null}.
     * @throws SQLException
     */
    public ParameterPhoneValue getParamPhone(int id, int paramId) throws SQLException {
        ParameterPhoneValue result = new ParameterPhoneValue();

        List<ParameterPhoneValueItem> itemList = new ArrayList<>();

        String query = SQL_SELECT + "phone, comment" + SQL_FROM + Tables.TABLE_PARAM_PHONE_ITEM
                + SQL_WHERE + "id=? AND param_id=?"
                + SQL_ORDER_BY + "n";
        try (var ps = con.prepareStatement(query.toString())) {
            ps.setInt(1, id);
            ps.setInt(2, paramId);
            var rs = ps.executeQuery();
            while (rs.next()) {
                itemList.add(getParamPhoneValueItemFromRs(rs));
            }
            result.setItemList(itemList);
        }

        return result;
    }

    public static ParameterPhoneValueItem getParamPhoneValueItemFromRs(ResultSet rs) throws SQLException {
        ParameterPhoneValueItem item = new ParameterPhoneValueItem();
        item.setPhone(rs.getString("phone"));
        item.setComment(rs.getString("comment"));
        return item;
    }

    /**
     * Selects a value of parameter with type 'text'.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    public String getParamText(int id, int paramId) throws SQLException {
        return getTextParam(id, paramId, Tables.TABLE_PARAM_TEXT);
    }

    /**
     * Selects a parameter value with type 'tree'.
     * @param id object ID.
     * @param paramId param ID.
     * @return набор значений.
     * @throws SQLException
     */
    public Set<String> getParamTree(int id, int paramId) throws SQLException {
        Set<String> result = new HashSet<String>();

        String query = "SELECT value FROM " + Tables.TABLE_PARAM_TREE + "WHERE id=? AND param_id=?";

        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.add(rs.getString(1));
        }
        ps.close();

        return result;
    }

    /**
     * Значения параметра объекта типа 'tree' с текстовыми наименованиями.
     * @param id object ID.
     * @param paramId param ID.
     * @return
     * @throws SQLException
     */
    public List<IdStringTitle> getParamTreeWithTitles(int id, int paramId) throws SQLException {
        List<IdStringTitle> result = new ArrayList<>();

        StringBuilder query = new StringBuilder(200);

        query.append(SQL_SELECT);
        query.append("val.value, dir.title");
        query.append(SQL_FROM);
        query.append(Tables.TABLE_PARAM_TREE);
        query.append("AS val");
        addTreeParamJoin(query, paramId);
        query.append(SQL_WHERE);
        query.append("val.id=? AND val.param_id=?");
        query.append(SQL_ORDER_BY).append("val.value");

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.add(new IdStringTitle(rs.getString(1), rs.getString(2)));
        }
        ps.close();

        return result;
    }

    private void addTreeParamJoin(StringBuilder query, int paramId) throws SQLException {
        Parameter param = ParameterCache.getParameter(paramId);
        String joinTable = param.getConfigMap().get(LIST_PARAM_USE_DIRECTORY_KEY, Tables.TABLE_PARAM_TREE_VALUE);
        addTreeTableJoin(query, joinTable);
    }

    private void addTreeTableJoin(StringBuilder query, String tableName) {
        query.append(SQL_LEFT_JOIN);
        query.append(tableName);
        query.append(" AS dir ON ");
        if (tableName.equals(Tables.TABLE_PARAM_TREE_VALUE)) {
            query.append(" val.param_id=dir.param_id AND val.value=dir.id ");
        } else {
            query.append(" val.value=dir.id ");
        }
    }

    /**
     * Проверяет заполненость параметра для объекта с кодом id.
     * @param id object ID.
     * @param param параметр.
     * @return
     * @throws Exception
     */
    public boolean isParameterFilled(int id, Parameter param) throws Exception {
        String query = "SELECT * FROM param_" + param.getType() + " WHERE id=? AND param_id=? LIMIT 1";
        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, param.getId());

        boolean result = ps.executeQuery().next();

        ps.close();

        return result;
    }

    /**
     * Переносит параметры при с кода объекта на -код объекта.
     * Используется при преобразовании не созданного до конца процесса с отрицательным кодом в созданный.
     * @param objectType
     * @param currentObjectId
     * @throws SQLException
     */
    public void objectIdInvert(String objectType, int currentObjectId) throws SQLException {
        // TODO: Invert records in TABLE_PARAM_LOG

        for (String tableName : TABLE_NAMES) {
            StringBuilder query = new StringBuilder();
            query.append("UPDATE ");
            query.append(tableName);
            query.append(" AS param");
            query.append(SQL_INNER_JOIN);
            query.append(Tables.TABLE_PARAM_PREF);
            query.append("AS pref ON param.param_id=pref.id AND pref.object=? ");
            query.append("SET param.id=?");
            query.append(SQL_WHERE);
            query.append("param.id=?");

            PreparedStatement ps = con.prepareStatement(query.toString());
            ps.setString(1, objectType);
            ps.setInt(2, -currentObjectId);
            ps.setInt(3, currentObjectId);
            ps.executeUpdate();
            ps.close();
        }
    }

    /**
     * Loads parameters for {@link ru.bgcrm.model.customer.Customer}, {@link ru.bgcrm.model.process.Process},
     * {@link ru.bgcrm.model.user.User} or {@link ru.bgcrm.model.param.address.AddressHouse}.
     * @param object customer or process.
     * @return
     * @throws SQLException
     */
    public Map<Integer, ParameterValuePair> parameters(Id object) throws SQLException {
        List<Parameter> parameters = null;
        if (object instanceof Customer) {
            parameters = ParameterCache.getObjectTypeParameterList(Customer.OBJECT_TYPE, ((Customer) object).getParamGroupId());
        } else if (object instanceof Process) {
            var type = ((Process) object).getType();
            parameters = Utils.getObjectList(ParameterCache.getParameterMap(), type.getProperties().getParameterIds());
        } else if (object instanceof User) {
            parameters = ParameterCache.getObjectTypeParameterList(User.OBJECT_TYPE);
        } else if (object instanceof AddressHouse) {
            parameters = ParameterCache.getObjectTypeParameterList(AddressHouse.OBJECT_TYPE);
        } else {
            throw new IllegalArgumentException("Unsupported object type: " + object);
        }

        return loadParameters(parameters, object.getId())
            .stream()
            .collect(Collectors.toMap(pv -> pv.getParameter().getId(), pv -> pv));
    }

    /**
     * Updates, appends and deletes an address parameter value.
     * @param id - entity ID.
     * @param paramId - param ID.
     * @param position - starting from 1 value's position, 0 - appends a value with position MAX+1.
     * @param value - the value, {@code null} - delete value from the position if {@code position} > 0, else delete all the values.
     * @throws SQLException
     */
    public void updateParamAddress(int id, int paramId, int position, ParameterAddressValue value) throws SQLException {
        int index = 1;

        if (value == null) {
            PreparedQuery pq = new PreparedQuery(con);

            pq.addQuery(SQL_DELETE_FROM + Tables.TABLE_PARAM_ADDRESS + SQL_WHERE + "id=? AND param_id=? ");
            pq.addInt(id);
            pq.addInt(paramId);

            if (position > 0) {
                pq.addQuery(" AND n=?");
                pq.addInt(position);
            }

            pq.executeUpdate();
            pq.close();
        } else {
            if (value.getValue() == null)
                value.setValue(AddressUtils.buildAddressValue(value, con));

            try {
                if (position <= 0) {
                    position = 1;

                    String query = "SELECT MAX(n) + 1 FROM " + Tables.TABLE_PARAM_ADDRESS + " WHERE id=? AND param_id=?";
                    PreparedStatement ps = con.prepareStatement(query);
                    ps.setInt(1, id);
                    ps.setInt(2, paramId);

                    ResultSet rs = ps.executeQuery();
                    if (rs.next() && rs.getObject(1) != null) {
                        position = rs.getInt(1);
                    }
                    ps.close();

                    insertParamAddress(id, paramId, position, value);
                } else {
                    String query = "UPDATE " + Tables.TABLE_PARAM_ADDRESS
                            + " SET value=?, house_id=?, flat=?, room=?, pod=?, floor=?, comment=?, custom=?"
                            + " WHERE id=? AND param_id=? AND n=?";
                    PreparedStatement ps = con.prepareStatement(query);

                    ps.setString(index++, value.getValue());
                    ps.setInt(index++, value.getHouseId());
                    ps.setString(index++, value.getFlat());
                    ps.setString(index++, value.getRoom());
                    ps.setInt(index++, value.getPod());
                    ps.setInt(index++, value.getFloor());
                    ps.setString(index++, value.getComment());
                    ps.setString(index++, value.getCustom());
                    ps.setInt(index++, id);
                    ps.setInt(index++, paramId);
                    ps.setInt(index++, position);

                    int cnt = ps.executeUpdate();

                    ps.close();

                    if (cnt == 0)
                        insertParamAddress(id, paramId, position, value);
                }
            } catch (SQLIntegrityConstraintViolationException e) {
                log.debug("Duplicated address value failed to be inserted: {}", value);
            }
        }

        if (history) {
            StringBuffer result = new StringBuffer();
            SortedMap<Integer, ParameterAddressValue> addresses = getParamAddress(id, paramId);
            Iterator<Integer> it = addresses.keySet().iterator();
            while (it.hasNext()) {
                if (result.length() > 0) {
                    result.append("; ");
                }
                Integer key = it.next();
                result.append(addresses.get(key).getValue());
            }
            logParam(id, paramId, userId, result.toString());
        }
    }

    private void insertParamAddress(int id, int paramId, int position, ParameterAddressValue value) throws SQLException {
        int index = 1;

        String query = "INSERT INTO " + Tables.TABLE_PARAM_ADDRESS
                + " SET id=?, param_id=?, n=?, value=?, house_id=?, flat=?, room=?, pod=?, floor=?, comment=?, custom=?";
        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(index++, id);
        ps.setInt(index++, paramId);
        ps.setInt(index++, position);
        ps.setString(index++, value.getValue());
        ps.setInt(index++, value.getHouseId());
        ps.setString(index++, value.getFlat());
        ps.setString(index++, value.getRoom());
        ps.setInt(index++, value.getPod());
        ps.setInt(index++, value.getFloor());
        ps.setString(index++, value.getComment());
        ps.setString(index++, value.getCustom());

        ps.executeUpdate();

        ps.close();
    }

    private void logParam(int id, int paramId, int userId, String newValue) throws SQLException {
        if (Utils.isBlankString(newValue)) {
            newValue = null;
        }

        ParamLogDAO paramLogDAO = new ParamLogDAO(this.con);

        if (newValue == null) {
            paramLogDAO.insertParamLog(id, paramId, userId, "");
        } else {
            paramLogDAO.insertParamLog(id, paramId, userId, newValue);
        }
    }

    /**
     * Обновляет строки адресных параметров для дома. Используется после изменений в адресных справочников,
     * для генерации корректных строк с адресными параметрами.
     * @param houseId код дома.
     * @throws SQLException
     */
    public void updateParamsAddressOnHouseUpdate(int houseId) throws SQLException {
        String query = "SELECT * FROM " + Tables.TABLE_PARAM_ADDRESS + " WHERE house_id=?";
        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, houseId);

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            int id = rs.getInt("id");
            int paramId = rs.getInt("param_id");
            int pos = rs.getInt("n");

            ParameterAddressValue value = getParameterAddressValueFromRs(rs);
            value.setValue(AddressUtils.buildAddressValue(value, con));

            updateParamAddress(id, paramId, pos, value);
        }

        ps.close();
    }

    /**
     * Устанавливает значение параметра типа 'blob'.
     * @param id object ID.
     * @param paramId param ID.
     * @param value значение, null или пустая строка - удалить значение.
     * @throws SQLException
     */
    public void updateParamBlob(int id, int paramId, String value) throws SQLException {
        if (Utils.isBlankString(value)) {
            value = null;
        }

        updateSimpleParam(id, paramId, value, Tables.TABLE_PARAM_BLOB);

        if (history) {
            logParam(id, paramId, userId, value != null ? Localization.getLocalizer().l("Length: {}", value.length()) : null);
        }
    }

    private void updateSimpleParam(int id, int paramId, Object value, String tableName) throws SQLException {
        if (value == null) {
            deleteFromParamTable(id, paramId, tableName);
        } else {
            StringBuilder query = new StringBuilder(200);

            query.append(SQL_UPDATE);
            query.append(tableName);
            query.append("SET value=?");
            query.append(SQL_WHERE);
            query.append("id=? AND param_id=?");

            PreparedStatement ps = con.prepareStatement(query.toString());
            ps.setObject(1, value);
            ps.setInt(2, id);
            ps.setInt(3, paramId);

            if (ps.executeUpdate() == 0) {
                ps.close();

                query.setLength(0);
                query.append(SQL_INSERT);
                query.append(tableName);
                query.append("(id, param_id, value) VALUES (?,?,?)");

                ps = con.prepareStatement(query.toString());
                ps.setInt(1, id);
                ps.setInt(2, paramId);
                ps.setObject(3, value);
                ps.executeUpdate();
            }
            ps.close();
        }
    }

    /**
     * Устанавливает значение параметра типа 'date'.
     * @param id object ID.
     * @param paramId param ID.
     * @param value значение, null - удаление.
     * @throws SQLException
     */
    public void updateParamDate(int id, int paramId, Date value) throws SQLException {
        updateSimpleParam(id, paramId, value, Tables.TABLE_PARAM_DATE);

        if (history) {
            logParam(id, paramId, userId, TimeUtils.format(value, TimeUtils.FORMAT_TYPE_YMD));
        }
    }

    /**
     * Устанавливает значение параметра типа 'datetime'.
     * @param id object ID.
     * @param paramId param ID.
     * @param value значение, null - удаление.
     * @throws SQLException
     */
    public void updateParamDateTime(int id, int paramId, Date value) throws SQLException {
        updateSimpleParam(id, paramId, value, Tables.TABLE_PARAM_DATETIME);

        if (history) {
            logParam(id, paramId, userId, TimeUtils.format(value, TimeUtils.FORMAT_TYPE_YMDHMS));
        }
    }

    /**
     * Обновляет/добавляет/удаляет значения параметра типа EMail.
     * @param id - код сущности в БД.
     * @param paramId - param ID.
     * @param position - позиция значения, начинается с 1, 0 - добавить новое значение с позицией MAX+1.
     * @param value - значение, null - удаление параметра на указанной позиции, если position>0; иначе - удаление всех значений.
     * @throws SQLException
     */
    public void updateParamEmail(int id, int paramId, int position, ParameterEmailValue value) throws SQLException {
        int index = 1;

        if (value == null) {
            PreparedQuery psDelay = new PreparedQuery(con);

            psDelay.addQuery(SQL_DELETE_FROM + Tables.TABLE_PARAM_EMAIL + SQL_WHERE + "id=? AND param_id=?");
            psDelay.addInt(id);
            psDelay.addInt(paramId);

            if (position > 0) {
                psDelay.addQuery(" AND n=?");
                psDelay.addInt(position);
            }

            psDelay.executeUpdate();

            psDelay.close();
        } else {
            if (position <= 0) {
                position = 1;

                String query = "SELECT MAX(n) + 1 FROM " + Tables.TABLE_PARAM_EMAIL + " WHERE id=? AND param_id=?";
                PreparedStatement ps = con.prepareStatement(query);
                ps.setInt(1, id);
                ps.setInt(2, paramId);

                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getObject(1) != null) {
                    position = rs.getInt(1);
                }
                ps.close();

                query = "INSERT INTO " + Tables.TABLE_PARAM_EMAIL + " SET id=?, param_id=?, n=?, value=?, comment=?";
                ps = con.prepareStatement(query);
                ps.setInt(index++, id);
                ps.setInt(index++, paramId);
                ps.setInt(index++, position);
                ps.setString(index++, value.getValue());
                ps.setString(index++, value.getComment());

                ps.executeUpdate();

                ps.close();
            } else {
                String query = "UPDATE " + Tables.TABLE_PARAM_EMAIL + " SET value=?, comment=?"
                        + " WHERE id=? AND param_id=? AND n=?";
                PreparedStatement ps = con.prepareStatement(query);

                ps.setString(index++, value.getValue());
                ps.setString(index++, value.getComment());
                ps.setInt(index++, id);
                ps.setInt(index++, paramId);
                ps.setInt(index++, position);

                ps.executeUpdate();

                ps.close();
            }
        }

        if (history) {
            logParam(id, paramId, userId, ParameterEmailValue.toString(getParamEmail(id, paramId).values()));
        }
    }

    /**
     * Updates parameter with type 'file'.
     * @param id object ID.
     * @param paramId param ID.
     * @param position position for multiple values, when is 0 - adding with new positions.
     * @param fileData value for the given position, if {@code null} - removes a value from the position or all values with {@code position} == -1.
     * @throws Exception
     */
    public void updateParamFile(int id, int paramId, int position, FileData fileData) throws Exception {
        if (fileData == null) {
            Map<Integer, FileData> currentValue = null;
            if (position == -1)
                currentValue = getParamFile(id, paramId);
            else {
                var value = getParamFile(id, paramId, position);
                currentValue = value != null ? Map.of(position, value) : Map.of();
            }

            if (!currentValue.isEmpty()) {
                for (var value : currentValue.values())
                    new FileDataDAO(con).delete(value);

                String query = SQL_DELETE_FROM + Tables.TABLE_PARAM_FILE + SQL_WHERE + "id=? AND param_id=?";
                try (var pq = new PreparedQuery(con, query)) {
                    pq.addInt(id);
                    pq.addInt(paramId);
                    if (position != -1) {
                        pq.addQuery(" AND n=?");
                        pq.addInt(position);
                    }
                    pq.executeUpdate();
                }
            }
        } else {
            if (fileData.getId() <= 0 && fileData.getData() != null) {
                try (var fos = new FileDataDAO(con).add(fileData)) {
                    fos.write(fileData.getData());
                }
            }

            if (position == 0) {
                var query = "SELECT MAX(n) + 1 FROM " + Tables.TABLE_PARAM_FILE + " WHERE id=? AND param_id=?";
                var ps = con.prepareStatement(query);
                ps.setInt(1, id);
                ps.setInt(2, paramId);

                var rs = ps.executeQuery();
                if (rs.next() && rs.getObject(1) != null) {
                    position = rs.getInt(1);
                } else {
                    position = 1;
                }
                ps.close();
            }

            var query = "INSERT INTO " + Tables.TABLE_PARAM_FILE + "(id, param_id, n, value) VALUES (?, ?, ?, ?)";

            var ps = con.prepareStatement(query);
            ps.setInt(1, id);
            ps.setInt(2, paramId);
            ps.setInt(3, position);
            ps.setInt(4, fileData.getId());
            ps.executeUpdate();
            ps.close();
        }

        if (history) {
            String values = "";

            String query = SQL_SELECT + "GROUP_CONCAT(fd.title SEPARATOR ', ')"
                + SQL_FROM + Tables.TABLE_PARAM_FILE + "AS pf "
                + SQL_INNER_JOIN + TABLE_FILE_DATA + "AS fd ON pf.value=fd.id"
                + SQL_WHERE + "pf.id=? AND pf.param_id=?";
            var pq = new PreparedQuery(con, query);
            pq.addInt(id).addInt(paramId);
            var rs = pq.executeQuery();

            if (rs.next())
                values = rs.getString(1);

            pq.close();

            logParam(id, paramId, userId, values);
        }
    }

    /**
     * @see #updateParamFile(int, int, int, FileData)
     */
    @Deprecated
    public void updateParamFile(int id, int paramId, int position, String comment, FileData fileData) throws Exception {
        updateParamFile(id, paramId, position, fileData);
    }

    /**
     * Устанавливает значения параметра типа 'list' с пустыми примечениями.
     * @param id object ID.
     * @param paramId param ID.
     * @param values набор с кодами значений.
     * @throws SQLException
     */
    public void updateParamList(int id, int paramId, Set<Integer> values) throws SQLException {
        if (values == null)
            values = Set.of();

        deleteFromParamTable(id, paramId, Tables.TABLE_PARAM_LIST);

        String query = "INSERT INTO " + Tables.TABLE_PARAM_LIST + "(id, param_id, value) VALUES (?,?,?)";

        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        for (int value : values) {
            ps.setInt(3, value);
            ps.executeUpdate();
        }
        ps.close();

        if (history) {
            logParam(id, paramId, userId, Utils.getObjectTitles(getParamListWithTitles(id, paramId)));
        }
    }

    /**
     * Устанавливает значения параметра типа 'list' с примечаниями.
     * @param id object ID.
     * @param paramId param ID.
     * @param values ключ - значение параметра, значение - текстовое примечание.
     * @throws SQLException
     */
    public void updateParamListWithComments(int id, int paramId, Map<Integer, String> values) throws SQLException {
        deleteFromParamTable(id, paramId, Tables.TABLE_PARAM_LIST);

        String query = "INSERT INTO " + Tables.TABLE_PARAM_LIST + "(id, param_id, value, comment) VALUES (?,?,?,?)";

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        for (Map.Entry<Integer, String> value : values.entrySet()) {
            ps.setInt(3, value.getKey());
            ps.setString(4, value.getValue());
            ps.executeUpdate();
        }
        ps.close();

        if (history) {
            logParam(id, paramId, userId, Utils.getObjectTitles(getParamListWithTitles(id, paramId)));
        }
    }

    @Deprecated
    public void updateParamList(int id, int paramId, Map<Integer, String> values) throws SQLException {
        log.warnd("Deprecated method 'updateParamList' was called. Use 'updateParamListWithComments' instead.");
        updateParamListWithComments(id, paramId, values);
    }

    /**
     * Sets values for parameter with type 'listcount'.
     * @param id entity ID.
     * @param paramId param ID.
     * @param values map with key = value ID, and values with possible types: {@link String}, {@link BigDecimal}, {@link ParameterListCountValue}.
     * @throws SQLException
     */
    public void updateParamListCount(int id, int paramId, Map<Integer, ?> values) throws SQLException {
        if (values == null)
            values = Map.of();

        deleteFromParamTable(id, paramId, Tables.TABLE_PARAM_LISTCOUNT);

        String query = "INSERT INTO " + Tables.TABLE_PARAM_LISTCOUNT + "(id, param_id, value, count, comment) VALUES (?,?,?,?,?)";

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        for (var me : values.entrySet()) {
            ps.setInt(3, me.getKey());
            Object value = me.getValue();

            BigDecimal count;
            String comment = "";

            if (value instanceof BigDecimal)
                count = (BigDecimal) value;
            else if (value instanceof ParameterListCountValue) {
                count = ((ParameterListCountValue) value).getCount();
                comment = ((ParameterListCountValue) value).getComment();
            } else if (value instanceof String)
                count = Utils.parseBigDecimal((String) value);
            else
                throw new IllegalArgumentException("Usupported value type: " + value);

            ps.setBigDecimal(4, count);
            ps.setString(5, comment);
            ps.executeUpdate();
        }
        ps.close();

        if (history) {
            logParam(id, paramId, userId, Utils.getObjectTitles(getParamListCountWithTitles(id, paramId)));
        }
    }

    /**
     * Использовать {@link #updateParamListCount(int, int, Map)}.
     */
    @Deprecated
    public void updateParamListCount(int id, int paramId, Map<Integer, Double> values,
            Map<Integer, String> valuesComments) throws SQLException {
        Map<Integer, BigDecimal> valuesFixed = new HashMap<>(values.size());
        for (Map.Entry<Integer, Double> me : values.entrySet())
            valuesFixed.put(me.getKey(), new BigDecimal(me.getValue()));
        updateParamListCount(id, paramId, valuesFixed);
    }

    /**
     * Updates parameter with type 'money'.
     * @param id object ID.
     * @param paramId param ID.
     * @param value the value, when {@code null} - delete.
     * @throws SQLException
     */
    public void updateParamMoney(int id, int paramId, BigDecimal value) throws SQLException {
        updateSimpleParam(id, paramId, value, Tables.TABLE_PARAM_MONEY);

        if (history) {
            logParam(id, paramId, userId, String.valueOf(value));
        }
    }

    /**
     * Updates parameter with type 'money'.
     * @param id object ID.
     * @param paramId param ID.
     * @param value the value, when {@code null} or a blank string - delete.
     * @throws SQLException
     */
    public void updateParamMoney(int id, int paramId, String value) throws SQLException {
        if (Utils.isBlankString(value))
            value = null;

        updateSimpleParam(id, paramId, Utils.parseBigDecimal(value), Tables.TABLE_PARAM_MONEY);

        if (history) {
            logParam(id, paramId, userId, value);
        }
    }

    /**
     * Устанавливает значения параметра типа 'phone'.
     * @param id object ID.
     * @param paramId param ID.
     * @param value значения, null либо пустой itemList - удаление значения.
     * @throws SQLException
     */
    public void updateParamPhone(int id, int paramId, ParameterPhoneValue value) throws SQLException {
        String newPhones = null;

        if (value == null || value.getItemList().size() == 0) {
            deleteFromParamTable(id, paramId, Tables.TABLE_PARAM_PHONE);
            deleteFromParamTable(id, paramId, Tables.TABLE_PARAM_PHONE_ITEM);
        } else {
            newPhones = value.toString();

            updateSimpleParam(id, paramId, newPhones, Tables.TABLE_PARAM_PHONE);

            deleteFromParamTable(id, paramId, Tables.TABLE_PARAM_PHONE_ITEM);

            int index = 1;

            String query = "INSERT INTO" + Tables.TABLE_PARAM_PHONE_ITEM
                    + "SET id=?, param_id=?, n=?, phone=?, comment=?";
            PreparedStatement ps = con.prepareStatement(query.toString());
            ps.setInt(index++, id);
            ps.setInt(index++, paramId);

            int n = 1;
            for (ParameterPhoneValueItem item : value.getItemList()) {
                index = 3;
                ps.setInt(index++, n++);
                ps.setString(index++, item.getPhone());
                ps.setString(index++, item.getComment());
                ps.executeUpdate();
            }
            ps.close();
        }

        if (history)
            logParam(id, paramId, userId, newPhones);
    }

    /**
     * Устанавливает значение параметра типа 'text'.
     * @param id object ID.
     * @param paramId param ID.
     * @param value значение, null или пустая строка - удалить значение.
     * @throws SQLException
     */
    public void updateParamText(int id, int paramId, String value) throws SQLException {
        if (Utils.isBlankString(value)) {
            value = null;
        }

        updateSimpleParam(id, paramId, value, Tables.TABLE_PARAM_TEXT);

        if (history) {
            logParam(id, paramId, userId, value);
        }
    }

    /**
     * Изменение значения параметра типа 'tree' объекта.
     * @param id object ID.
     * @param paramId param ID.
     * @param values значения.
     * @throws SQLException
     */
    public void updateParamTree(int id, int paramId, Set<String> values) throws SQLException {
        if (values == null)
            values = Set.of();

        deleteFromParamTable(id, paramId, Tables.TABLE_PARAM_TREE);

        String query = "INSERT INTO " + Tables.TABLE_PARAM_TREE + "(id, param_id, value) VALUES (?,?,?)";

        PreparedStatement ps = con.prepareStatement(query.toString());
        ps.setInt(1, id);
        ps.setInt(2, paramId);
        for (String value : values) {
            ps.setString(3, value);
            ps.executeUpdate();
        }
        ps.close();

        if (history) {
            logParam(id, paramId, userId, Utils.getObjectTitles(getParamTreeWithTitles(id, paramId)));
        }
    }

    /**
     * Loads parameter's values.
     * @param paramList parameters list.
     * @param id entity id.
     * @throws SQLException
    */
    public List<ParameterValuePair> loadParameters(List<Parameter> paramList, int id) throws SQLException {
        return loadParameters(paramList, id, false);
    }

    /**
     * Loads parameter's values.
     * @param paramList parameters list.
     * @param id entity id.
     * @param offEncryption decrypt pseudo encrypted values.
     * @throws SQLException
     */
    public List<ParameterValuePair> loadParameters(List<Parameter> paramList, int id, boolean offEncryption) throws SQLException {
        Map<String, List<Integer>> paramTypeMap = new HashMap<String, List<Integer>>();

        List<ParameterValuePair> result = new ArrayList<>(paramList.size());
        Map<Integer, ParameterValuePair> paramMap = new HashMap<>(paramList.size());

        for (Parameter parameter : paramList) {
            String type = parameter.getType();
            List<Integer> ids = paramTypeMap.get(type);
            if (ids == null) {
                paramTypeMap.put(type, ids = new ArrayList<>());
            }
            ids.add(parameter.getId());

            ParameterValuePair pvp = new ParameterValuePair(parameter);
            paramMap.put(parameter.getId(), pvp);

            result.add(pvp);
        }

        for (String type : paramTypeMap.keySet())
            updateParamValueMap(paramMap, type, paramTypeMap.get(type), id, offEncryption);

        return result;
    }

    @Deprecated
    public void loadParameterValue(ParameterValuePair param, int objectId, boolean offEncription) throws Exception {
        Parameter parameter = param.getParameter();
        updateParamValueMap(Collections.singletonMap(parameter.getId(), param), parameter.getType(),
                Collections.singletonList(parameter.getId()), objectId, offEncription);
    }

    /**
     * Loads parameters for a type.
     * @param paramMap target map.
     * @param type type.
     * @param ids IDs.
     * @param objectId object ID.
     * @throws SQLException
     */
    @SuppressWarnings("unchecked")
    private void updateParamValueMap(Map<Integer, ParameterValuePair> paramMap, String type, Collection<Integer> ids,
            int objectId, boolean offEncryption) throws SQLException {
        StringBuilder query = new StringBuilder();

        ResultSet rs = null;
        PreparedStatement ps = null;

        if (Parameter.TYPE_LIST.equals(type)) {
            // ключ - имя таблицы справочника, значение - перечень параметров
            Map<String, Set<Integer>> tableParamsMap = new HashMap<String, Set<Integer>>();
            for (Integer paramId : ids) {
                Parameter param = ParameterCache.getParameter(paramId);
                String tableName = param.getConfigMap().get(LIST_PARAM_USE_DIRECTORY_KEY);
                if (tableName == null) {
                    tableName = Tables.TABLE_PARAM_LIST_VALUE;
                }

                Set<Integer> pids = tableParamsMap.get(tableName);
                if (pids == null) {
                    tableParamsMap.put(tableName, pids = new HashSet<Integer>());
                }
                pids.add(paramId);
            }

            final String standartPrefix = "SELECT val.param_id, val.value, dir.title, val.comment FROM "
                    + Tables.TABLE_PARAM_LIST + " AS val ";
            for (Map.Entry<String, Set<Integer>> me : tableParamsMap.entrySet()) {
                String tableName = me.getKey();
                if (query.length() > 0) {
                    query.append("\nUNION ");
                }

                query.append(standartPrefix);
                addListTableJoin(query, tableName);
                query.append(SQL_WHERE);
                query.append("val.id=" + objectId + " AND val.param_id IN (");
                query.append(Utils.toString(me.getValue()));
                query.append(")");
            }

            ps = con.prepareStatement(query.toString());
            rs = ps.executeQuery();
        } else if (Parameter.TYPE_LISTCOUNT.equals(type)) {
            // ключ - имя таблицы справочника, значение - перечень параметров
            Map<String, Set<Integer>> tableParamsMap = new HashMap<String, Set<Integer>>();
            for (Integer paramId : ids) {
                Parameter param = ParameterCache.getParameter(paramId);
                String tableName = param.getConfigMap().get(LIST_PARAM_USE_DIRECTORY_KEY);
                if (tableName == null) {
                    tableName = Tables.TABLE_PARAM_LISTCOUNT_VALUE;
                }

                Set<Integer> pids = tableParamsMap.get(tableName);
                if (pids == null) {
                    tableParamsMap.put(tableName, pids = new HashSet<Integer>());
                }
                pids.add(paramId);
            }

            final String standartPrefix = "SELECT val.param_id, val.value, val.count, dir.title FROM "
                    + Tables.TABLE_PARAM_LISTCOUNT + " AS val ";
            for (Map.Entry<String, Set<Integer>> me : tableParamsMap.entrySet()) {
                String tableName = me.getKey();
                if (query.length() > 0) {
                    query.append("\nUNION ");
                }

                query.append(standartPrefix);
                addListCountTableJoin(query, tableName);
                query.append(SQL_WHERE);
                query.append("val.id=" + objectId + " AND val.param_id IN (");
                query.append(Utils.toString(me.getValue()));
                query.append(")");
            }

            ps = con.prepareStatement(query.toString());
            rs = ps.executeQuery();
        } else if (Parameter.TYPE_TREE.equals(type)) {
            // ключ - имя таблицы справочника, значение - перечень параметров
            Map<String, Set<Integer>> tableParamsMap = new HashMap<String, Set<Integer>>();
            for (Integer paramId : ids) {
                Parameter param = ParameterCache.getParameter(paramId);
                String tableName = param.getConfigMap().get(LIST_PARAM_USE_DIRECTORY_KEY);
                if (tableName == null) {
                    tableName = Tables.TABLE_PARAM_TREE_VALUE;
                }

                Set<Integer> pids = tableParamsMap.get(tableName);
                if (pids == null) {
                    tableParamsMap.put(tableName, pids = new HashSet<Integer>());
                }
                pids.add(paramId);
            }

            final String standartPrefix = "SELECT val.param_id, val.value, dir.title FROM " + Tables.TABLE_PARAM_TREE
                    + " AS val ";
            for (Map.Entry<String, Set<Integer>> me : tableParamsMap.entrySet()) {
                String tableName = me.getKey();
                if (query.length() > 0) {
                    query.append("\nUNION ");
                }

                query.append(standartPrefix);
                addTreeTableJoin(query, tableName);
                query.append(SQL_WHERE);
                query.append("val.id=" + objectId + " AND val.param_id IN (");
                query.append(Utils.toString(me.getValue()));
                query.append(")");
            }

            ps = con.prepareStatement(query.toString());
            rs = ps.executeQuery();
        } else if (Parameter.TYPE_ADDRESS.equals(type)) {
            query.append("SELECT param_id, n, value, house_id FROM param_");
            query.append(type);
            query.append(" WHERE id=? AND param_id IN ( ");
            query.append(Utils.toString(ids));
            query.append(" ) ORDER BY n ");
        } else if (Parameter.TYPE_EMAIL.equals(type)) {
            query.append("SELECT param_id, n, value, comment FROM param_");
            query.append(type);
            query.append(" WHERE id=? AND param_id IN ( ");
            query.append(Utils.toString(ids));
            query.append(" )");
        } else if (Parameter.TYPE_FILE.equals(type)) {
            query.append("SELECT pf.param_id, pf.n, fd.* FROM " + Tables.TABLE_PARAM_FILE
                    + " AS pf INNER JOIN " + TABLE_FILE_DATA + " AS fd ON pf.value=fd.id "
                    + " WHERE pf.id=? AND pf.param_id IN ( ");
            query.append(Utils.toString(ids));
            query.append(" ) ORDER BY n");
        } else if (Parameter.TYPE_PHONE.equals(type)) {
            query.append("SELECT pi.param_id, pi.n, pi.phone, pi.comment " + SQL_FROM + Tables.TABLE_PARAM_PHONE_ITEM
                    + "AS pi WHERE pi.id=? AND pi.param_id IN ( ");
            query.append(Utils.toString(ids));
            query.append(" ) ORDER BY pi.n");
        } else {
            query.append("SELECT param_id, value FROM param_");
            query.append(type);
            query.append(" WHERE id=? AND param_id IN ( ");
            query.append(Utils.toString(ids));
            query.append(" )");
        }

        if (ps == null) {
            ps = con.prepareStatement(query.toString());
            ps.setInt(1, objectId);
            rs = ps.executeQuery();
        }

        while (rs.next()) {
            final int paramId = rs.getInt(1);

            ParameterValuePair param = paramMap.get(paramId);

            if (Parameter.TYPE_DATE.equals(type)) {
                param.setValue(rs.getDate(2));
            } else if (Parameter.TYPE_DATETIME.equals(type)) {
                param.setValue(rs.getTimestamp(2));
            } else if (Parameter.TYPE_LIST.equals(type)) {
                List<IdTitle> values = (List<IdTitle>) param.getValue();
                if (values == null)
                    param.setValue(values = new ArrayList<IdTitle>());

                IdTitle value = new IdTitle(rs.getInt(2), rs.getString(3));

                String comment = rs.getString(4);
                // TODO: IdTitleComment?
                if (Utils.notBlankString(comment))
                    value.setTitle(value.getTitle() + " [" + comment + "]");

                values.add(value);
            } else if (Parameter.TYPE_LISTCOUNT.equals(type)) {
                List<IdTitle> values = (List<IdTitle>) param.getValue();
                if (values == null)
                    param.setValue(values = new ArrayList<IdTitle>());
                values.add(new IdTitle(
                    rs.getInt(2),
                    rs.getString(4) + ":" + Utils.format(rs.getBigDecimal(3))
                ));
            } else if (Parameter.TYPE_TREE.equals(type)) {
                List<IdStringTitle> values = (List<IdStringTitle>) param.getValue();
                if (values == null)
                    param.setValue(values = new ArrayList<>());
                values.add(new IdStringTitle(rs.getString(2), rs.getString(3)));
            } else if (Parameter.TYPE_ADDRESS.equals(type)) {
                Map<Integer, ParameterAddressValue> values = (Map<Integer, ParameterAddressValue>) param.getValue();
                if (values == null)
                    param.setValue(values = new TreeMap<Integer, ParameterAddressValue>());

                ParameterAddressValue val = new ParameterAddressValue();
                val.setValue(rs.getString(3));
                val.setHouseId(rs.getInt(4));

                values.put(rs.getInt(2), val);
            } else if (Parameter.TYPE_EMAIL.equals(type)) {
                Map<Integer, String> values = (Map<Integer, String>) param.getValue();
                if (values == null)
                    param.setValue(values = new TreeMap<Integer, String>());

                if (!"".equals(rs.getString(4))) {
                    values.put(rs.getInt(2), rs.getString(3) + " [" + rs.getString(4) + "]");
                } else {
                    values.put(rs.getInt(2), rs.getString(3));
                }
            } else if (Parameter.TYPE_FILE.equals(type)) {
                Map<String, FileData> values = (Map<String, FileData>) param.getValue();
                if (values == null)
                    param.setValue(values = new LinkedHashMap<String, FileData>());

                values.put(rs.getString(2), FileDataDAO.getFromRs(rs, "fd."));
            } else if (Parameter.TYPE_PHONE.equals(type)) {
                ParameterPhoneValue value = (ParameterPhoneValue)param.getValue();
                if (value == null)
                    param.setValue(value = new ParameterPhoneValue());
                value.addItem(getParamPhoneValueItemFromRs(rs));
            } else if (Parameter.TYPE_MONEY.equals(type)) {
                param.setValue(rs.getBigDecimal(2));
            } else {
                if ("encrypted".equals(param.getParameter().getConfigMap().get("encrypt")) && !offEncryption) {
                    param.setValue("<ЗНАЧЕНИЕ ЗАШИФРОВАНО>");
                } else {
                    param.setValue(rs.getString(2));
                }
            }
        }
        ps.close();
    }

    private void deleteFromParamTable(int id, int paramId, String tableName) throws SQLException {
        String query = SQL_DELETE_FROM + tableName + SQL_WHERE + "id=? AND param_id=?";
        try (var ps = con.prepareStatement(query)) {
            ps.setInt(1, id);
            ps.setInt(2, paramId);
            ps.executeUpdate();
        }
    }

    /**
     * @see OldParamSearchDAO#searchObjectByParameterPhone(int, ParameterPhoneValue)
     */
    @Deprecated
    public Set<Integer> searchObjectByParameterPhone(int parameterId, ParameterPhoneValue parameterPhoneValue) throws SQLException {
        return new org.bgerp.dao.param.OldParamSearchDAO(con).searchObjectByParameterPhone(parameterId, parameterPhoneValue);
    }

    /**
     * @see OldParamSearchDAO#searchObjectByParameterAddress(int, ParameterAddressValue)
     */
    @Deprecated
    public Set<Integer> searchObjectByParameterAddress(int parameterId, ParameterAddressValue parameterAddressValue) throws SQLException {
        return new org.bgerp.dao.param.OldParamSearchDAO(con).searchObjectByParameterAddress(parameterId, parameterAddressValue);
    }

    /**
     * @see OldParamSearchDAO#searchObjectByParameterText(int, String)
     */
    @Deprecated
    public Set<Integer> searchObjectByParameterText(int parameterId, String parameterTextValue) throws SQLException {
        return new org.bgerp.dao.param.OldParamSearchDAO(con).searchObjectByParameterText(parameterId, parameterTextValue);
    }

    /**
     * @see OldParamSearchDAO#searchObjectByParameterList(int, int)
     */
    @Deprecated
    public Set<Integer> searchObjectByParameterList(int parameterId, int value) throws Exception {
        return new org.bgerp.dao.param.OldParamSearchDAO(con).searchObjectByParameterList(parameterId, value);
    }
}
