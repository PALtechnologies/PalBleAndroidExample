# PalBleAndroidExample [ ![Download](https://api.bintray.com/packages/pal/maven/PalBleAndroid/images/download.svg?version=0.6.1) ](https://bintray.com/pal/maven/PalBleAndroid/0.6.1/link)
This is a basic example application that utilises the PalBleAndroid library to connect to Activator Micro devices and display the summary information.
## Connection Modes
The application demonstrates two modes of operation for the Activator Micro.
### Discreate Connection
This is the traditional form of device communication. It establishes a connection between mobile device and Activator Micro and downloads the latest summary data.
In addition to collecting the data a number of house-keep tasks are performed, these include waking sleeping Activators, setting the internal clock, and performing communication encryption.
Once the summaries have been collected the connection is closed. The app then demonstrates data download by establishing a new connection and downloading all data stored upon the Activator.
Both the summary and an example of the downloaded data are displayed by the application.
### Continuous Connection
The app also demonstrates the new continuous connection mode offered b the Activator Micro. This opens a connection between the Activator and mobile devices, and allows the Activator to send notifications to the mobile application. These notifications include the real-time detection of steps and posture change events.
The continuous connection is maintained through a foreground service. This displays a persistent notification that is used to display the current values of the connection device.
The top line gives an example of real-time updating summary figures, while the bottom line indicates the latest notification received.
## Data
There are four key data objects in the PalBleAndroid library.
### AdvertisingInfo
This contains information about the Micro from before a connection is established, such as serial number and firmware. It may be accessed from any device returned by a scan. It is typically used to decide which Activator you wish to connect to.
### ConnectionInfo
This contains additional information available once a connection has been established. For example, battery level and memory usage. The Activator extends upon this information with AcitvatorMicroConnectionInfo which includes additional information about the current posture of the participant.
### ActivatorMicroSummaries
This contains summary information for both the current day and current hour (last 60 minutes). This information includes step count in addition to time in seconds spent; lying, sitting, upright, and stepping. Step count and upright time are updated in real-time. During sedentary behaviour sitting time is incremented in real-time, however, once the sedentary bout has ended the Activator internally classifies the bout as either sitting or lying, and updates the two times respectively (i.e. sitting time may decrease). I simple sum of lying and sitting time will give a measure of sedentary time. Stepping time is updated every 15 seconds. It is estimated by summing each one second epoch in which one or more steps occurred over a 15 second epoch.
### DownloadInfo
This contains the downloaded data and associated metadata from the Activator Micro. The getDatx method returns data which may be saved, as demonstrated by the application, to produce a datx file that will be openable in a future release of PALanalysis. The downloaded data contains a detailed log of the participant’s activities. It is based upon 15 second summaries, with features such as steps and posture at second resolution. This more detailed data ma be post processed in PALanalysis to form a more detailed analysis of the participant’s behaviours.
