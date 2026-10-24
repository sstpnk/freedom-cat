package tech.stpnk.freedomcat.fmt.v2ray;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import tech.stpnk.freedomcat.fmt.KryoConverters;
import tech.stpnk.freedomcat.core.utils.JavaUtil;

public class VMessBean extends StandardV2RayBean {

    public Integer alterId; // alterID == -1 --> VLESS

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        alterId = alterId != null ? alterId : 0;

        if (alterId == -1) {
            encryption = JavaUtil.isNotBlank(encryption) ? encryption : "";
        } else {
            encryption = JavaUtil.isNotBlank(encryption) ? encryption : "auto";
        }
    }

    @NotNull
    @Override
    public VMessBean clone() {
        return KryoConverters.deserialize(new VMessBean(), KryoConverters.serialize(this));
    }

    public static final Creator<VMessBean> CREATOR = new CREATOR<VMessBean>() {
        @NonNull
        @Override
        public VMessBean newInstance() {
            return new VMessBean();
        }

        @Override
        public VMessBean[] newArray(int size) {
            return new VMessBean[size];
        }
    };
}
