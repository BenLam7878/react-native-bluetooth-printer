'use strict';
var React = require('react-native');
var blueToothPrinterModule = React.NativeModules.BlueToothPrinterModule;

class BlueToothPrinterModule  {

  constructor() {
    this.isPeripheralConnected = this.isPeripheralConnected.bind(this);
  }

  read(peripheralId, serviceUUID, characteristicUUID) {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.read(peripheralId, serviceUUID, characteristicUUID, (error, data) => {
        if (error) {
          reject(error);
        } else {
          fulfill(data);
        }
      });
    });
  }

  readRSSI(peripheralId) {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.readRSSI(peripheralId, (error, rssi) => {
        if (error) {
          reject(error);
        } else {
          fulfill(rssi);
        }
      });
    });
  }

  write(options, peripheralId, serviceUUID, data, maxByteSize) {
    if (maxByteSize == null) {
      maxByteSize = 20;
    }
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.write(options, peripheralId, serviceUUID, data, maxByteSize, (error) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  writeWithoutResponse(peripheralId, serviceUUID, characteristicUUID, data, maxByteSize, queueSleepTime) {
    if (maxByteSize == null) {
      maxByteSize = 20;
    }
    if (queueSleepTime == null) {
      queueSleepTime = 10
    }
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.writeWithoutResponse(peripheralId, serviceUUID, characteristicUUID, data, maxByteSize, queueSleepTime, (error) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  connect(peripheralId) {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.connect(peripheralId, (error, peripheral) => {
        if (error) {
          reject(error);
        } else {
          fulfill(peripheral);
        }
      });
    });
  }

  disconnect(peripheralId) {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.disconnect(peripheralId, (error) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  startNotification(peripheralId, serviceUUID, characteristicUUID) {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.startNotification(peripheralId, serviceUUID, characteristicUUID, (error) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  stopNotification(peripheralId, serviceUUID, characteristicUUID) {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.stopNotification(peripheralId, serviceUUID, characteristicUUID, (error) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  checkState() {
    blueToothPrinterModule.checkState();
  }

  start(options) {
    return new Promise((fulfill, reject) => {
      if (options == null) {
        options = {};
      }
      blueToothPrinterModule.start(options, (error) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  scan(serviceUUIDs, seconds, allowDuplicates) {
    return new Promise((fulfill, reject) => {
      if (allowDuplicates == null) {
        allowDuplicates = false;
      }
      blueToothPrinterModule.scan(serviceUUIDs, seconds, allowDuplicates, (error) => {
        if (error) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  stopScan() {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.stopScan((error) => {
        if (error != null) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  enableBluetooth() {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.enableBluetooth((error) => {
        if (error != null) {
          reject(error);
        } else {
          fulfill();
        }
      });
    });
  }

  getConnectedPeripherals(serviceUUIDs) {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.getConnectedPeripherals(serviceUUIDs, (error, result) => {
        if (error) {
          reject(error);
        } else {
          if (result != null) {
            fulfill(result);
          } else {
            fulfill([]);
          }
        }
      });
    });
  }

  getDiscoveredPeripherals() {
    return new Promise((fulfill, reject) => {
      blueToothPrinterModule.getDiscoveredPeripherals((error, result) => {
        if (error) {
          reject(error);
        } else {
          if (result != null) {
            fulfill(result);
          } else {
            fulfill([]);
          }
        }
      });
    });
  }

  isPeripheralConnected(peripheralId, serviceUUIDs) {
    return this.getConnectedPeripherals(serviceUUIDs).then((result) => {
      if (result.find((p) => { return p.id === peripheralId; })) {
        return true;
      } else {
        return false;
      }
    });
  }
}

module.exports = new BlueToothPrinterModule();
