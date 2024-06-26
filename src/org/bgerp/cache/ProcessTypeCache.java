package org.bgerp.cache;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bgerp.app.cfg.ConfigMap;
import org.bgerp.app.cfg.Preferences;
import org.bgerp.app.cfg.Setup;
import org.bgerp.app.event.EventProcessor;
import org.bgerp.util.Log;

import javassist.NotFoundException;
import ru.bgcrm.dao.ConfigDAO;
import ru.bgcrm.dao.process.ProcessTypeDAO;
import ru.bgcrm.dao.process.StatusDAO;
import ru.bgcrm.event.SetupChangedEvent;
import ru.bgcrm.model.process.ProcessType;
import ru.bgcrm.model.process.Status;
import ru.bgcrm.util.Utils;

/**
 * In-memory process types cache.
 *
 * @author Shamil Vakhitov
 */
public class ProcessTypeCache extends Cache<ProcessTypeCache> {
    private static final Log log = Log.getLog();

    private static final CacheHolder<ProcessTypeCache> HOLDER = new CacheHolder<>(new ProcessTypeCache());

    public static Map<Integer, ProcessType> getProcessTypeMap() {
        return HOLDER.getInstance().typeMap;
    }

    /**
     * Retrieves process type object.
     * @param id ID.
     * @return type instance or {@code null}.
     */
    public static ProcessType getProcessType(int id) {
        return HOLDER.getInstance().typeMap.get(id);
    }

    /**
     * Gets process type by ID.
     * @param id ID.
     * @return
     * @throws NotFoundException
     */
    public static ProcessType getProcessTypeOrThrow(int id) throws NotFoundException {
        var result = getProcessType(id);
        if (result == null)
            throw new NotFoundException("Not found process type with ID: " + id);
        return result;
    }

    /**
     * Retrieves process type object null-safe.
     * @param id ID.
     * @return type instance or mock object with a title, generated out of {@code id}.
     */
    public static ProcessType getProcessTypeSafe(int id) {
        return Utils.maskNull(getProcessType(id), new ProcessType(id, "??? [" + id + "]"));
    }

    public static List<ProcessType> getTypeList(Set<Integer> ids) {
        List<ProcessType> result = new ArrayList<>();

        for (ProcessType type : HOLDER.getInstance().typeList) {
            if (ids.contains(type.getId())) {
                result.add(type);
            }
        }

        return result;
    }

    public static List<ProcessType> getTypeList(String objectType) throws Exception {
        List<ProcessType> result = new ArrayList<>();

        var filter = new TypeFilter(objectType);
        for (ProcessType type : HOLDER.getInstance().typeList) {
            if (filter.check(type))
                result.add(type);
        }

        return result;
    }

    //TODO: Возможно стоит создать Config класс, вынести в model.process.config.LinkedProcessCreateConfig, фильтр сделать на основе expression фильтра.
    private static class TypeFilter {
        private final String objectType;

        private TypeFilter(String objectType) {
            this.objectType = objectType;
        }

        private boolean check(ProcessType type) throws Exception {
            ConfigMap configMap = type.getProperties().getConfigMap();
            Set<String> createInObjectTypes = Utils.toSet(configMap.get("create.in.objectTypes", "*"));
            if (!createInObjectTypes.contains(objectType) && !createInObjectTypes.contains("*")) {
                return false;
            }

            /* Undocumented, remove later 31.01.2022.
            String paramFilter = configMap.get("create.in.filter");
            if (Utils.notBlankString(paramFilter)) {
                if (paramValueDao == null) {
                    paramValueDao = new ParamValueDAO(con);
                    paramValueCache = new HashMap<>();
                }

                if (!paramValueDao.paramValueFilter(paramFilter, objectId, paramValueCache)) {
                    return false;
                }
            }
            */

            return true;
        }
    }

    public static ProcessType getTypeTreeRoot() {
        return HOLDER.getInstance().tree;
    }

    public static List<Status> getTypeStatusList(ProcessType type, int currentStatusId) {
        List<Status> result = new ArrayList<>();

        Set<Integer> allowedStatusSet = type.getProperties().getAllowedStatusSet(currentStatusId);
        allowedStatusSet.add(currentStatusId);

        List<Status> statusList = Utils.getObjectList(ProcessTypeCache.getStatusMap(), type.getProperties().getStatusIds());
        for (Status status : statusList) {
            if (allowedStatusSet.contains(status.getId())) {
                result.add(status);
            }
        }

        return result;
    }

    public static List<Status> getStatusList() {
        return HOLDER.getInstance().statusList;
    }

    public static Map<Integer, Status> getStatusMap() {
        return HOLDER.getInstance().statusMap;
    }

    public static Status getStatusSafe(int statusId) {
        return Utils.maskNull(getStatusMap().get(statusId), new Status(statusId, "??? [" + statusId + "]"));
    }

    public static List<ProcessType> getTypePath(int id) {
        List<ProcessType> result = new ArrayList<>();

        ProcessType type = new ProcessType();
        type.setParentId(id);

        while (type.getParentId() != 0) {
            final int parentId = type.getParentId();

            type = HOLDER.getInstance().typeMap.get(parentId);
            if (type == null) {
                type = new ProcessType();
                type.setTitle("??? [" + parentId + "]");
            }
            result.add(0, type);
        }

        return result;
    }

    public static void flush(Connection con) {
        HOLDER.flush(con);
    }

    // end of static

    private List<ProcessType> typeList;
    private Map<Integer, ProcessType> typeMap;

    private ProcessType tree;
    private List<Status> statusList;
    private Map<Integer, Status> statusMap;

    @Override
    protected ProcessTypeCache newInstance() {
        ProcessTypeCache result = new ProcessTypeCache();

        try (var con = Setup.getSetup().getDBConnectionFromPool()) {
            ConfigDAO configDao = new ConfigDAO(con);
            ProcessTypeDAO typeDAO = new ProcessTypeDAO(con);

            result.typeList = typeDAO.getFullProcessTypeList();
            result.typeMap = new HashMap<>();

            for (ProcessType type : result.typeList) {
                if (!type.isUseParentProperties())
                    type.getProperties().setConfigMap(Preferences.processIncludes(configDao, type.getProperties().getConfig(), false));
                result.typeMap.put(type.getId(), type);
            }

            result.tree = typeDAO.getTypeTreeRoot();

            result.statusList = new StatusDAO(con).getStatusList();
            result.statusMap = new HashMap<>();
            for (Status status : result.statusList)
                result.statusMap.put(status.getId(), status);
        } catch (Exception e) {
            log.error(e);
        }

        // because process type configurations may include global configs
        EventProcessor.subscribe((conSet, e) -> {
            ProcessTypeCache.flush(null);
        }, SetupChangedEvent.class);

        return result;
    }
}
