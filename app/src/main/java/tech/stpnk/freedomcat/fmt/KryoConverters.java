package tech.stpnk.freedomcat.fmt;

import androidx.room.TypeConverter;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import tech.stpnk.freedomcat.database.SubscriptionBean;
import tech.stpnk.freedomcat.fmt.http.HttpBean;
import tech.stpnk.freedomcat.fmt.hysteria.HysteriaBean;
import tech.stpnk.freedomcat.fmt.internal.ChainBean;
import tech.stpnk.freedomcat.fmt.mieru.MieruBean;
import tech.stpnk.freedomcat.fmt.naive.NaiveBean;
import tech.stpnk.freedomcat.fmt.shadowsocks.ShadowsocksBean;
import tech.stpnk.freedomcat.core.proxy.anytls.AnyTLSBean;
import tech.stpnk.freedomcat.core.proxy.shadowtls.ShadowTLSBean;
import tech.stpnk.freedomcat.fmt.socks.SOCKSBean;
import tech.stpnk.freedomcat.fmt.ssh.SSHBean;
import tech.stpnk.freedomcat.fmt.trojan.TrojanBean;
import tech.stpnk.freedomcat.fmt.trojan_go.TrojanGoBean;
import tech.stpnk.freedomcat.fmt.tuic.TuicBean;
import tech.stpnk.freedomcat.fmt.v2ray.VMessBean;
import tech.stpnk.freedomcat.fmt.wireguard.WireGuardBean;
import tech.stpnk.freedomcat.ktx.KryosKt;
import tech.stpnk.freedomcat.ktx.Logs;
import tech.stpnk.freedomcat.core.proxy.config.ConfigBean;
import tech.stpnk.freedomcat.core.proxy.neko.NekoBean;
import tech.stpnk.freedomcat.core.utils.JavaUtil;

public class KryoConverters {

    private static final byte[] NULL = new byte[0];

    @TypeConverter
    public static byte[] serialize(Serializable bean) {
        if (bean == null) return NULL;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBufferOutput buffer = KryosKt.byteBuffer(out);
        bean.serializeToBuffer(buffer);
        buffer.flush();
        buffer.close();
        return out.toByteArray();
    }

    public static <T extends Serializable> T deserialize(T bean, byte[] bytes) {
        if (bytes == null) return bean;
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        ByteBufferInput buffer = KryosKt.byteBuffer(input);
        try {
            bean.deserializeFromBuffer(buffer);
        } catch (KryoException e) {
            Logs.INSTANCE.w(e);
        }
        bean.initializeDefaultValues();
        return bean;
    }

    @TypeConverter
    public static SOCKSBean socksDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new SOCKSBean(), bytes);
    }

    @TypeConverter
    public static HttpBean httpDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new HttpBean(), bytes);
    }

    @TypeConverter
    public static ShadowsocksBean shadowsocksDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ShadowsocksBean(), bytes);
    }

    @TypeConverter
    public static ConfigBean configDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ConfigBean(), bytes);
    }

    @TypeConverter
    public static VMessBean vmessDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new VMessBean(), bytes);
    }

    @TypeConverter
    public static TrojanBean trojanDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new TrojanBean(), bytes);
    }

    @TypeConverter
    public static TrojanGoBean trojanGoDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new TrojanGoBean(), bytes);
    }

    @TypeConverter
    public static MieruBean mieruDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new MieruBean(), bytes);
    }

    @TypeConverter
    public static NaiveBean naiveDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new NaiveBean(), bytes);
    }

    @TypeConverter
    public static HysteriaBean hysteriaDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new HysteriaBean(), bytes);
    }

    @TypeConverter
    public static SSHBean sshDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new SSHBean(), bytes);
    }

    @TypeConverter
    public static WireGuardBean wireguardDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new WireGuardBean(), bytes);
    }

    @TypeConverter
    public static TuicBean tuicDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new TuicBean(), bytes);
    }

    @TypeConverter
    public static ShadowTLSBean shadowTLSDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ShadowTLSBean(), bytes);
    }

    @TypeConverter
    public static AnyTLSBean anyTLSDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new AnyTLSBean(), bytes);
    }


    @TypeConverter
    public static ChainBean chainDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new ChainBean(), bytes);
    }

    @TypeConverter
    public static NekoBean nekoDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new NekoBean(), bytes);
    }

    @TypeConverter
    public static SubscriptionBean subscriptionDeserialize(byte[] bytes) {
        if (JavaUtil.isEmpty(bytes)) return null;
        return deserialize(new SubscriptionBean(), bytes);
    }

}
