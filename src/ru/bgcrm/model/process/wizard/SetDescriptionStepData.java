package ru.bgcrm.model.process.wizard;

import java.sql.Connection;

import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.Utils;

public class SetDescriptionStepData extends StepData<SetDescriptionStep> {
    public SetDescriptionStepData(SetDescriptionStep step, WizardData data) {
        super(step, data);
    }

    @Override
    public boolean isFilled(DynActionForm form, Connection con) {
        return Utils.notBlankString(data.getProcess().getDescription());
    }
}
