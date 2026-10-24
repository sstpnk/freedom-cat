package tech.stpnk.freedomcat.aidl;

import tech.stpnk.freedomcat.aidl.IServiceBridgeCallback;

interface IServiceBridge {
  int getState();
  String getProfileName();

  void registerCallback(in IServiceBridgeCallback cb, int id);
  oneway void unregisterCallback(in IServiceBridgeCallback cb);

  int urlTest();
}
