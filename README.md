# Time-Sync-Across-Smartphones

## Implementation to synchronize Android Smartphones using Audio peripheral, Wifi peripheral and BLE peripheral.

This repo contains the reference implementations which can be used to synchronize time across Android smartphones. 
We tested the implementation on 4 Android phoness (2 pixel-3 and 2 Nexus-5x). The paper is accepted. More figures and details will be added.

## Author
[Sandeep Singh Sandha](https://sites.google.com/view/sandeep-/home)

## Results
*Code can achieve synchronization within 200 microseconds using Audio and within few milliseconds using Wi-Fi and BLE.*

## General Instructions
1. The code consists of Android applications which can be complied and installed on Android devices.
2. Development was done using Android Studio in Java.
3. Each application has the code for sychronization along with local timestamp/offset calculation.
4. The goal is to provide the clean sychronization code with minimum UI which can integreated with any application as per need.
5. We present multiple synchronization techniques with different accuracies and tradeoffs. More figures and analysis will be made available later. The paper is under review.
5. For any questions/queries: Feel free to reach out to sandha.iitr (at) gmail.com


## 1. Audio peripheral
**Audio_Sync** folder contains the implementation which is using audio subsystem to timestamp the audio events using monotonic clock provided by Android operating system. The audio timestamp is directly provided by the android operating system and no application level timestamping is used. The implementation is tested for Pixel-3 and Nexus-5x phones. The highest audio sampling rate supported by Pixel-3 and Nexus-5x is used (192k) so as to achieve the best results. In general if the implementation is to used for older devices then the sampling rate can be changed to a lower number supported by the device.

### Usage
Same audio event can be timestamped by running the code on different devices. Any one of the device can be taken as reference. The timestamps of different devices can be captured and compared with the reference to calculate the offsets. These offsets can be used to provide a shared notion of time on all the devices now. 

### Result
In our testing, the audio peripheral achieves the best result with offset variation within [-200,200] microseconds for 86% of the experiments. We conducted ~160 audio events in an hour and calculated these variations by considering clock drifts into account. If audio offsets are calculated, these can used directly for next few minutes without considering the drift.  

### Things to consider
If audio experiments are conducted for a longer duration then the drift between the reference and other devices have to taken into account. Pixel-3 devices have relatively stable audio sampling rate. For Nexus-5x device audio sampling rate fluctuates, our implemtation automatically takes cares of it by removing the buffers at runtime for which fluctuations are considerably high.  

## 2. Wifi peripheral 
**wifi_Sync** folder contains the implementation which is using the NTP client to calculate the offset with respect to the Apple NTP server. Offsets are calculated with respect to the monotonic time of the device. The implementation is based on the sockets. The same implementation can be used for the LTE, but more variability is expected with the LTE. The implementation can be used across a wide set of Android devices. We extensively tested the implemented on Pixel-3 and Nexus-5x phones. In order to handle the NTP variability, for each offset calculation, client does 10 calcuations and then uses the median results as the offset.

### Usage
Each device can independtly calculate the offsets with respect to the same NTP server or a different NTP server. These offsets can be used to provide a shared notion of time on the devices.

### Result
In our testing, we used the same NTP server (Apple NTP server: 17.253.26.253) for all phones. We selected this server on the basis of the lowest roundtrip latency ~4ms using the Wi-Fi network at UCLA. The variability of NTP offsets using same UCLA campus Wi-Fi is within (-1,+1) milliseconds for 95% of the cases.  

### Things to consider
NTP offset calcuations are impacted by the network quality and the NTP server. It is generally preferred to use the same network and same NTP servers. In cases where devices are distributed and each device may use a different NTP server or may be connected to different networks, the offset variability will be high.  

## 3. BLE peripheral
**Ble_sync** folder contains the implementation which uses NTP client to calculate the offset with to NTP server. BLE protocol is used for communication.  Offsets are calculated with respect to the monotonic time of the device. Both client and server implementations are provided. he implementation can be used across a wide set of Android devices. We extensively tested the implemented on Pixel-3 and Nexus-5x phones. In order to handle the NTP variability, for each offset calculation, client does 10 calcuations and then uses the median results as the offset.

### Usage
Each device can independtly calculate the offsets with respect to the NTP server. These offsets can be used to provide a shared notion of time on the devices. The implementation assumes all the clients are only paired with the server Android phones. 
Any one of the device can be made the NTP server and all other devices can be used as clients.

### Result
In our testing, we used the same BLE NTP server for all other devices. The variability of NTP offsets calculation using BLE is within (-5,+5) milliseconds for 90% of the cases.  

### Things to consider
In BLE NTP offset calcuations have more variablity as compared to the Wi-Fi and Audio. This variability has impact on the accuracy. BLE can be used where it is exclusively available to the devices, or in cases where network connectivity is not available. 
