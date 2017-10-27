package gr.ictpro.jsalatas.agendawidget.model.settings.types;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import gr.ictpro.jsalatas.agendawidget.R;
import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.ui.SearchPeriodDialog;
import gr.ictpro.jsalatas.agendawidget.ui.UpdateFrequencyDialog;
import gr.ictpro.jsalatas.agendawidget.ui.widgets.SettingDialog;
import gr.ictpro.jsalatas.agendawidget.utils.TimePeriod;
import gr.ictpro.jsalatas.agendawidget.utils.TimePeriodUtils;

public class SettingUpdateFrequency extends SettingLong {
    @Override
    protected SettingDialog<Long> getDialog(View view) {
        return new UpdateFrequencyDialog((Activity) view.getContext(), this);
    }

    @Override
    protected boolean shouldRefreshList() {
        return true;
    }

    @Override
    public View getView(Context context) {
        View v = super.getView(context);

        TextView tvDescription = (TextView) v.findViewById(R.id.tvDescription);

        String[] values = context.getResources().getStringArray(R.array.update_frequency);
        TimePeriodUtils tpu = new TimePeriodUtils(values, UpdateFrequencyDialog.UpdateFrequencyPeriod.class);

        TimePeriod tp = tpu.getTimePeriod(getValue());
        tvDescription.setText(AgendaWidgetApplication.getContext().getString(R.string.every) + " " + tp.getValue() + " " + tp.getTimeUnit());
        return v;
    }

}