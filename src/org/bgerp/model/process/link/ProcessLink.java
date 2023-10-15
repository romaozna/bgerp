package org.bgerp.model.process.link;

import ru.bgcrm.model.CommonObjectLink;
import ru.bgcrm.model.process.Process;

/**
 * Link another object to a process.
 */
public class ProcessLink extends CommonObjectLink {
    public ProcessLink(int processId, String linkType, int linkedObjectId, String linkedObjectTitle) {
        super(Process.OBJECT_TYPE, processId, linkType, linkedObjectId, linkedObjectTitle);
    }
}
