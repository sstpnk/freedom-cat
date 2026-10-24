package tech.stpnk.freedomcat.aidl;

import tech.stpnk.freedomcat.aidl.SpeedDisplayData;
import tech.stpnk.freedomcat.aidl.TrafficData;

oneway interface ISagerNetServiceCallback {
  void stateChanged(int state, String profileName, String msg);
  void missingPlugin(String profileName, String pluginName);
  void cbSpeedUpdate(in SpeedDisplayData stats);
  void cbTrafficUpdate(in TrafficData stats);
  void cbSelectorUpdate(long id);
}
