package ru.bgcrm.cache;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections.CollectionUtils;
import org.bgerp.util.Dynamic;
import org.bgerp.util.Log;

import ru.bgcrm.dao.user.UserDAO;
import ru.bgcrm.dao.user.UserGroupDAO;
import ru.bgcrm.dao.user.UserPermsetDAO;
import ru.bgcrm.model.user.Group;
import ru.bgcrm.model.user.PermissionNode;
import ru.bgcrm.model.user.Permset;
import ru.bgcrm.model.user.User;
import ru.bgcrm.model.user.UserGroup;
import ru.bgcrm.util.ParameterMap;
import ru.bgcrm.util.Preferences;
import ru.bgcrm.util.Setup;
import ru.bgcrm.util.TimeUtils;

public class UserCache extends Cache<UserCache> {
    private static final Log log = Log.getLog();

    private static CacheHolder<UserCache> holder = new CacheHolder<>(new UserCache());

    private static final ParameterMap EMPTY_PERMISSION = new Preferences();

    public static User getUser(final int id) {
        return holder.getInstance().userMapById.get(id);
    }

    public static Map<Integer, User> getUserMap() {
        return holder.getInstance().userMapById;
    }

    /**
     * Finds user with status not {@link User#STATUS_DISABLED}.
     * @param login
     * @return
     */
    public static User getUser(final String login) {
        return holder.getInstance().activeUserMapByLogin.get(login);
    }

    public static List<User> getUserList() {
        return holder.getInstance().userList;
    }

    public static Collection<User> getActiveUsers() {
        return holder.getInstance().activeUserMapByLogin.values();
    }

    public static Group getUserGroup(final int groupId) {
        final List<Group> groups = holder.getInstance().userGroupList;
        for (final Group group : groups) {
            if (group.getId() == groupId) {
                return group;
            }
        }

        return null;
    }

    public static int getUserGroupChildCount(final int groupId) {
        int result = 0;

        for (final Group group : holder.getInstance().userGroupList) {
            if (group.getParentId() == groupId) {
                result++;
            }
        }

        return result;
    }

    /**
     * Gets user permission for action.
     * The method is used in {@code permission.tld}
     * @param userId user ID.
     * @param action semicolon separated action class name and method, e.g. {@code org.bgerp.plugin.bil.invoice.action.InvoiceAction:get}.
     * @return allowed permission with options or {@code null}.
     */
    @Dynamic
    public static ParameterMap getPerm(final int userId, String action) {
        final User user = getUser(userId);

        final String key = "user.permission.check";

        final boolean permCheck = Setup.getSetup().getBoolean(key, false);
        final boolean userPermCheck = user.getConfigMap().getBoolean(key, true);
        if (permCheck && userPermCheck && user.getId() != 1) {
            final Map<String, ParameterMap> userPerm = holder.getInstance().userPermMap.get(userId);
            if (userPerm != null) {
                final PermissionNode node = PermissionNode.getPermissionNode(action);

                if (node != null) {
                    if (node.isAllowAll()) {
                        return EMPTY_PERMISSION;
                    }

                    action = node.getAction();
                }

                final ParameterMap map = userPerm.get(action);
                if (map != null) {
                    return map;
                }
            }
            return null;
        } else {
            return EMPTY_PERMISSION;
        }
    }

    public static List<User> getUserList(final Set<Integer> groupIds) {
        final List<User> result = new ArrayList<User>();

        for (final User user : holder.getInstance().userList) {
            if (CollectionUtils.intersection(groupIds, user.getGroupIds()).size() > 0) {
                result.add(user);
            }
        }

        return result;
    }

    public static Set<Group> getUserGroupChildSet(final int groupId) {
        final Set<Group> resultSet = new HashSet<Group>();

        for (final Group group : holder.getInstance().userGroupList) {
            if (group.getParentId() == groupId) {
                resultSet.add(group);
            }
        }

        return resultSet;
    }

    public static Set<Group> getUserGroupChildFullSet(final int groupId) {
        final Set<Group> resultSet = new HashSet<Group>();
        resultSet.addAll(getUserGroupChildSet(groupId));

        if (resultSet.size() > 0) {
            final List<Group> groupList = new ArrayList<Group>(resultSet);

            for (int i = 0; i < groupList.size(); i++) {
                if (groupList.get(i).getChildCount() > 0) {
                    resultSet.addAll(getUserGroupChildFullSet(groupList.get(i).getId()));
                }
            }
        }

        return resultSet;
    }

    public static List<Group> getUserGroupList() {
        return holder.getInstance().userGroupList;
    }

    public static List<Group> getUserGroupFullTitledList() {
        return holder.getInstance().userGroupFullTitledList;
    }

    public static Map<Integer, Group> getUserGroupMap() {
        return holder.getInstance().userGroupMap;
    }

    public static Map<Integer, Group> getUserGroupFullTitledMap() {
        return holder.getInstance().userGroupFullTitledMap;
    }

    /**
     * @return alphabetically sorted list with all permission sets.
     */
    public static List<Permset> getUserPermsetList() {
        return holder.getInstance().userPermsetList;
    }

    /**
     * @return map with all use permission sets, key - ID
     */
    public static Map<Integer, Permset> getUserPermsetMap() {
        return holder.getInstance().userPermsetMap;
    }

    public static void flush(final Connection con) {
        holder.flush(con);
    }

    public static List<Group> getGroupPath(final int id) {
        final List<Group> result = new ArrayList<Group>();

        Group script = new Group();
        script.setParentId(id);

        while (script.getParentId() != 0) {
            script = holder.getInstance().userGroupMap.get(script.getParentId());
            result.add(0, script);
        }
        return result;
    }

    /**
     * "Возвращает полный путь к корневой группе в виде строки (например: Администратор -> Помощник -> Помощник помощника)"
     * @param id группы
     * @return Строка с полным путем к корневой группе, либо title группы, если нет родительской группы
     */
    public static String getUserGroupWithPath(final Map<Integer, Group> groupMap, final int id, final boolean withId) {
        Group group = groupMap.get(id);
        String titleWithPath = group.getTitle();

        if (withId) {
            titleWithPath += " (" + group.getId() + ")";
        }

        while (group.getParentId() != 0) {
            final int parentId = group.getParentId();

            group = groupMap.get(parentId);
            if (group == null) {
                log.warn("Not found parent group with id: " + parentId);
                break;
            }

            if (withId) {
                titleWithPath = group.getTitle() + " (" + group.getId() + ") " + " / " + titleWithPath;
            } else {
                titleWithPath = group.getTitle() + " / " + titleWithPath;
            }
        }

        return titleWithPath;
    }

    public static List<UserGroup> getUserGroupList(final int id) {
        return holder.getInstance().userGroupListsMap.get(id) == null ? new ArrayList<UserGroup>()
                : holder.getInstance().userGroupListsMap.get(id);
    }

    public static List<UserGroup> getUserGroupList(final int id, final Date actualDate) {
        return getUserGroupList(id, -1, actualDate);
    }

    public static List<UserGroup> getUserGroupList(final int id, final int parentId, final Date actualDate) {
        final List<UserGroup> resultList = new ArrayList<UserGroup>();

        final List<UserGroup> groupList = holder.getInstance().userGroupListsMap.get(id);
        if (groupList != null) {
            for (final UserGroup item : groupList) {
                final Group group = holder.getInstance().userGroupMap.get(item.getGroupId());
                if (group != null) {
                    if ((parentId < 0 || group.getParentId() == parentId) && (actualDate == null
                            || ((item.getDateFrom() != null && actualDate.compareTo(item.getDateFrom()) >= 0)
                                    && (item.getDateTo() == null || item.getDateTo().compareTo(actualDate) >= 0)))) {
                        resultList.add(item);
                    }
                }
            }
        }

        return resultList;
    }

    // end of static part

    private List<User> userList;
    private Map<Integer, User> userMapById;
    private Map<String, User> activeUserMapByLogin;

    private List<Permset> userPermsetList;
    private Map<Integer, Permset> userPermsetMap;

    private List<Group> userGroupList;
    private Map<Integer, Group> userGroupMap;
    private List<Group> userGroupFullTitledList;
    private Map<Integer, Group> userGroupFullTitledMap;

    private Map<Integer, Map<String, ParameterMap>> userPermMap;

    private UserCache result;

    private Map<Integer, List<UserGroup>> userGroupListsMap;

    @SuppressWarnings("serial")
    @Override
    protected UserCache newInstance() {
        result = new UserCache();

        final Setup setup = Setup.getSetup();

        try (var con = setup.getDBConnectionFromPool()) {
            final UserDAO userDAO = new UserDAO(con);
            final UserPermsetDAO permsetDAO = new UserPermsetDAO(con);
            final UserGroupDAO groupDAO = new UserGroupDAO(con);

            result.userList = userDAO.getUserList();

            result.userMapById = new HashMap<Integer, User>() {
                @Override
                public User get(final Object key) {
                    final Integer id = (Integer) key;

                    User result = super.get(id);
                    if (result == null) {
                        result = new User();
                        result.setId(id);
                        result.setTitle("Не существует (" + id + ")");
                    }

                    return result;
                }
            };

            for (final User user : result.userList) {
                result.userMapById.put(user.getId(), user);
            }

            result.activeUserMapByLogin = new TreeMap<String, User>();
            for (final User user : result.userList) {
                if (user.getStatus() != User.STATUS_DISABLED) {
                    result.activeUserMapByLogin.put(user.getLogin(), user);
                }
            }

            // группы
            result.userGroupList = groupDAO.getGroupList();

            result.userGroupMap = new HashMap<Integer, Group>(result.userGroupList.size());
            for (final Group group : result.userGroupList) {
                result.userGroupMap.put(group.getId(), group);
            }

            result.userGroupFullTitledList = new ArrayList<Group>();
            for (final Group group : result.userGroupList) {
                final Group fullTitled = group.clone();
                fullTitled.setTitle(UserCache.getUserGroupWithPath(result.userGroupMap, group.getId(), false));
                result.userGroupFullTitledList.add(fullTitled);
            }

            result.userGroupFullTitledMap = new HashMap<Integer, Group>(result.userGroupFullTitledList.size());
            for (final Group group : result.userGroupFullTitledList) {
                result.userGroupFullTitledMap.put(group.getId(), group);
            }

            //привязки пользователей и групп
            result.userGroupListsMap = userDAO.getAllUserGroups();

            // наборы прав
            result.userPermsetList = permsetDAO.getPermsetList();

            result.userPermsetMap = new HashMap<Integer, Permset>(result.userPermsetList.size());
            for (final Permset permset : result.userPermsetList) {
                result.userPermsetMap.put(permset.getId(), permset);
            }

            // сбор свойств пользователей из групп и наборов прав
            final Map<Integer, List<Integer>> allUserPermsetIds = userDAO.getAllUserPermsetIds();
            final Map<Integer, Set<Integer>> allUserQueueIds = userDAO.getAllUserQueueIds();

            final Map<Integer, List<Integer>> allGroupPermsetIds = groupDAO.getAllGroupPermsetIds();
            final Map<Integer, Set<Integer>> allGroupQueueIds = groupDAO.getAllGroupQueueIds();

            final Map<Integer, Map<String, ParameterMap>> allUserPermById = primaryActions(userDAO.getAllUserPerm());
            final Map<Integer, Map<String, ParameterMap>> allPermsetPermById = primaryActions(permsetDAO.getAllPermsets());

            result.userPermMap = new HashMap<>();

            for (final User user : result.userList) {
                final Map<String, ParameterMap> perm = new HashMap<>();
                result.userPermMap.put(user.getId(), perm);

                user.setPermsetIds(allUserPermsetIds.get(user.getId()));
                user.setQueueIds(allUserQueueIds.get(user.getId()));

                final List<UserGroup> ugList = result.userGroupListsMap.get(user.getId());
                if (ugList != null) {
                    user.setGroupIds(getActualUserGroupIdSet(new Date(), ugList));
                }

                // действующие группы пользователя
                final List<Group> userGroupList = new ArrayList<Group>();
                for (final Group group : result.userGroupList) {
                    if (user.getGroupIds().contains(group.getId())) {
                        userGroupList.add(group);
                    }
                }

                // действующие наборы прав пользователя
                final List<Integer> userPermsetIds = new ArrayList<Integer>();
                // сначала собираются все наборы групп в порядке алфавита, установленных у пользователя
                // наборы в каждой группе установлены в определённом порядке
                for (final Group group : userGroupList) {
                    final List<Integer> groupPermsetIds = allGroupPermsetIds.get(group.getId());
                    if (groupPermsetIds != null) {
                        userPermsetIds.addAll(groupPermsetIds);
                    }
                }
                // далее - наборы из пользователя в порядке установки
                userPermsetIds.addAll(user.getPermsetIds());

                // склеенная конфигурация наборов прав
                final StringBuilder fullUserConfig = new StringBuilder(500);

                // сбор прав, ролей и конфигураций из действующих наборов пользователя
                for (final Integer permsetId : userPermsetIds) {
                    final Map<String, ParameterMap> permsetPermMap = allPermsetPermById.get(permsetId);
                    if (permsetPermMap != null) {
                        perm.putAll(permsetPermMap);
                    }

                    // склеивание ролей и конфигураций
                    final Permset permset = result.userPermsetMap.get(permsetId);
                    if (permset != null) {
                        user.setRoles(user.getRoles() + " " + permset.getRoles());

                        // пока просто склеивание конфигов полностью а нужно сделать склеивание на уровне параметров,
                        // чтобы города объединялись
                        fullUserConfig.append(permset.getConfig());
                        fullUserConfig.append("\n");
                    } else {
                        log.warn("Not existing permset '{}' is set for a user.", permsetId);
                    }
                }

                // склеенная конфигурация групп
                final StringBuilder groupConfig = new StringBuilder(500);

                for (final Group group : userGroupList) {
                    addGroupConfig(group.getId(), groupConfig);
                }

                user.setConfig(fullUserConfig.toString() + groupConfig.toString() + user.getConfig());

                // персональные права
                final Map<String, ParameterMap> personalPermMap = allUserPermById.get(user.getId());
                if (personalPermMap != null) {
                    perm.putAll(personalPermMap);
                }

                // очереди процессов из групп
                for (final Integer groupId : user.getGroupIds()) {
                    final Set<Integer> groupQueueIds = allGroupQueueIds.get(groupId);
                    if (groupQueueIds != null) {
                        user.getQueueIds().addAll(groupQueueIds);
                    }
                }

                log.debug("User id: {}; login: {}; roles: {}; queueIds: {}; config: \n{}; groups: {}; permsetIds: ", user.getId(), user.getLogin(),
                        user.getRoles(), user.getQueueIds(), user.getConfig(), userGroupList, userPermsetIds);
            }

            User user = new User();
            user.setId(User.USER_CUSTOMER_ID);
            user.setTitle("Customer");
            result.userMapById.put(user.getId(), user);

            user = new User();
            user.setId(User.USER_SYSTEM_ID);
            user.setTitle("System");
            user.setLogin(setup.get("user.system.login"));
            user.setPassword(setup.get("user.system.pswd"));
            result.userMapById.put(user.getId(), user);
        } catch (final Exception e) {
            log.error(e);
        }

        return result;
    }

    /**
     * Sets primary actions for all sub-maps, stored by IDs.
     * @param permMapById key - some ID, value - permission map with obitary keys.
     * @return modified {@code permMapById}.
     */
    private Map<Integer, Map<String, ParameterMap>> primaryActions(final Map<Integer, Map<String, ParameterMap>> permMapById) {
        for (final var me : permMapById.entrySet())
            me.setValue(PermissionNode.primaryActions(me.getValue()));
        return permMapById;
    }

    private Set<Integer> getActualUserGroupIdSet(final Date actualDate, final List<UserGroup> ugList) {
        final Set<Integer> activeGroupSet = new HashSet<Integer>();

        for (final UserGroup ug : ugList) {
            if (TimeUtils.dateInRange(actualDate, ug.getDateFrom(), ug.getDateTo())) {
                activeGroupSet.add(ug.getGroupId());
            }
        }

        return activeGroupSet;
    }

    private void addGroupConfig(final Integer groupId, final StringBuilder config) {
        final Group group = result.userGroupMap.get(groupId);

        if (group != null) {
            final Integer parentId = group.getParentId();
            if (parentId > 0) {
                addGroupConfig(parentId, config);
            }

            config.append(group.getConfig());
            config.append("\n");
        }
    }

}
