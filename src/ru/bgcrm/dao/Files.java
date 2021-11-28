package ru.bgcrm.dao;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bgerp.event.ProcessFilesEvent;

import ru.bgcrm.cache.ParameterCache;
import ru.bgcrm.cache.ProcessTypeCache;
import ru.bgcrm.dao.process.ProcessDAO;
import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.model.FileData;
import ru.bgcrm.model.param.Parameter;
import ru.bgcrm.model.param.ParameterValuePair;
import ru.bgcrm.util.Setup;
import ru.bgcrm.util.sql.ConnectionSet;

/**
 * Provides files from parameters for adding to messages.
 *
 * @author Shamil Vakhitov
 */
public class Files {
    public Files() {
        EventProcessor.subscribe((e, conSet) -> processFiles(e, conSet), ProcessFilesEvent.class);
    }

    private void processFiles(ProcessFilesEvent e, ConnectionSet conSet) throws Exception {
        try (var con = Setup.getSetup().getDBSlaveConnectionFromPool()) {
            var process = new ProcessDAO(con).getProcess(e.getProcessId());
            if (process == null)
                return;

            var paramIds = ProcessTypeCache.getProcessType(process.getTypeId()).getProperties().getParameterIds();
            List<Parameter> paramList = ParameterCache.getParameterList(paramIds).stream()
                .filter(param -> Parameter.TYPE_FILE.equals(param.getType()))
                .collect(Collectors.toList());

            if (paramList.isEmpty())
                return;

            List<ParameterValuePair> list = new ParamValueDAO(con).loadParameters(paramList, e.getProcessId(), false);
            for (var pair : list) {
                var value = (Map<Integer, FileData>) pair.getValue();
                if (value == null)
                    continue;

                for (var file : value.values())
                    e.addFile(file);
            }
        }
    }
}