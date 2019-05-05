# Time-Sync-Across-Smartphones

## Implementation to synchronize Android Smartphones using Audio peripheral, Wifi peripheral and BLE peripheral.

This repo contains the reference implementations which can be used to synchronize time across Android smartphones. 

## 1. Audio peripheral
**Audio_Sync** folder contains the implementation which is using audio subsystem to timestamp the audio events using monotonic clock. The timestamp is provided by the android operating system. The implementation is tested for Pixel-3 and Nexus-5x phones. The highest audio sampling rate supported by Pixel-3 and Nexus-5x is used (192k).

### Usage
Same audio event can be timestamped by running the code on different devices. Any one of the device can be taken as reference. The timestamps of different devices can be captured and compared with the reference to calculate the offset.

### Result
In our testing, the audio peripheral acheives the best result with offset variation within [-200,200] microseconds for 86% of the time.  

### Things to consider
If audio experiments are conducted for a longer duration then the drift between the reference and other devices have to taken into account. Pixel-3 devices have relatively stable audio sampling rate. For Nexus-5x device audio sampling rate varies and code automatically takes cares of it by removing the buffers for which fluctuations are considerable high. These number are defined in the code.


