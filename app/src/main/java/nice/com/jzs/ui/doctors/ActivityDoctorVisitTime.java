package nice.com.jzs.ui.doctors;

import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

import java.util.HashMap;
import java.util.Map;

import nice.com.jzs.R;
import nice.com.jzs.background.RequestAPI;
import nice.com.jzs.core.AbstractActivity;
import nice.com.nice_library.bean.BaseBean;
import nice.com.nice_library.widget.container.ViewContainer;

/**
 * Created by admin on 2016/7/27.
 */
@EActivity(R.layout.activity_doctor_visit_time)
public class ActivityDoctorVisitTime extends AbstractActivity {


    @ViewById(R.id.container)
    ViewContainer viewContainer;
    @ViewById(R.id.during_time)
    TextView during_time;

    @ViewById(R.id.tips)
    TextView tips;

    @AfterViews
    void initView() {
        getData();
    }

    void getData() {
        Map<String, String> params = new HashMap<>();
        new NiceAsyncTask() {

            @Override
            public void loadSuccess(BaseBean bean) {
                DoctorVisitTimeBean visitTimeBean = (DoctorVisitTimeBean) bean;
                if (visitTimeBean != null) {
                    updateView(visitTimeBean);
                }
            }

            @Override
            public void exception() {

            }
        }.post(true, RequestAPI.API_JZB_DOCTORS_VISIT_TIME, params, DoctorVisitTimeBean.class);
    }

    private void updateView(DoctorVisitTimeBean visitTimeBean) {

        viewContainer.columns = 5;
        viewContainer.hasDivider = 1;
        viewContainer.dividerWid = 2;

        if (visitTimeBean.getData().getVisit_times() != null) {
            for (DoctorVisitTimeBean.DataBean.VisitTimesBean itemBean : visitTimeBean.getData().getVisit_times()) {
                ViewDoctorVisitTimeItem timeItem = new ViewDoctorVisitTimeItem(ActivityDoctorVisitTime.this);
                int index = visitTimeBean.getData().getVisit_times().indexOf(itemBean);
                int color = getResources().getColor(R.color.white);
                if (index % 2 == 0) {
                    color = getResources().getColor(R.color.white);
                } else {
                    color = getResources().getColor(R.color.white_two);
                }
                timeItem.setData(color, itemBean.getIs_visit());
                viewContainer.addView(timeItem);
            }
        }

        during_time.setText(visitTimeBean.getData().getDuring_time());
        tips.setText(visitTimeBean.getData().getTips());

    }


    @Override
    protected void onClickBack() {

    }
}
